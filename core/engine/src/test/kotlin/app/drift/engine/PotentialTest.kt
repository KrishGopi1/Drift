package app.drift.engine

import app.drift.engine.models.DebtMath
import app.drift.engine.models.PotentialModel
import app.drift.model.Provenance
import app.drift.model.engine.EnergyLabel
import app.drift.model.engine.EnergyPhase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PotentialTest {
    private val p = ParameterSet.DEFAULT
    private val model = PotentialModel(p)

    @Test fun `potential is full with no debt and falls with debt`() {
        assertThat(model.score(0.0)).isEqualTo(100.0)
        assertThat(model.score(-30.0)).isEqualTo(100.0)
        // RISE's published example: 6.3 h of debt alongside 77% potential.
        assertThat(model.score(6.3 * 60)).isWithin(1.5).of(77.0)
        assertThat(model.score(600.0)).isLessThan(model.score(300.0))
    }

    @Test fun `labels follow the thresholds and the five hour target is great`() {
        assertThat(model.label(model.score(60.0))).isEqualTo(EnergyLabel.SUPER)
        assertThat(model.label(model.score(300.0))).isEqualTo(EnergyLabel.GREAT)
        assertThat(model.label(model.score(480.0))).isEqualTo(EnergyLabel.GOOD)
        assertThat(model.label(model.score(720.0))).isEqualTo(EnergyLabel.FAIR)
        assertThat(model.label(model.score(1200.0))).isEqualTo(EnergyLabel.LOW)
    }

    @Test fun `debt lowers dips more than peaks`() {
        val debt = 480.0
        assertThat(model.curvePenalty(30.0, debt)).isGreaterThan(model.curvePenalty(90.0, debt))
        assertThat(model.curvePenalty(50.0, 0.0)).isEqualTo(0.0)
    }

    @Test fun `catch up counts nights until great and the projection shrinks debt`() {
        val c = model.catchUp(600.0, 480.0, 0.6, DebtMath.lambda(p), LocalDate.of(2026, 10, 10))
        assertThat(c.nightsToGreat).isNotNull()
        assertThat(c.nightsToGreat!!).isAtLeast(1)
        assertThat(c.projection.first().first).isEqualTo(LocalDate.of(2026, 10, 10))
        val minutes = c.projection.map { it.second.toMinutes() }
        assertThat(minutes).isInOrder(Comparator.reverseOrder<Long>())
        assertThat(model.catchUp(60.0, 480.0, 0.6, DebtMath.lambda(p), LocalDate.of(2026, 10, 10)).nightsToGreat).isEqualTo(0)
    }

    @Test fun `snapshot carries an estimated potential with a band and a trend per debt night`() {
        val snap = Kit.engine.analyze(Synth.chronicShort().input())
        val pot = snap.energyPotential!!
        assertThat(pot.score.provenance).isEqualTo(Provenance.ESTIMATED)
        assertThat(pot.score.confidence!!.lower).isAtMost(pot.score.value)
        assertThat(pot.trend).hasSize(snap.debtTrend.size)
        assertThat(pot.score.value).isWithin(0.1).of(model.score(snap.sleepDebt!!.value.toMinutes().toDouble()))
        assertThat(snap.catchUp!!.projection).hasSize(p.catchUpHorizon.i)
    }

    @Test fun `chronic short sleep flattens the curve compared with steady sleep`() {
        fun peakAndDip(s: Scenario): Pair<Double, Double> {
            val today = Kit.engine.analyze(s.input()).energyCurve.days[1]
            val awake = today.segments.filter { it.phase != EnergyPhase.SLEEP }
            val pts = today.points.filter { pt -> awake.any { pt.at >= it.interval.start && pt.at < it.interval.end } }
            return pts.maxOf { it.level } to pts.minOf { it.level }
        }
        val (steadyPeak, _) = peakAndDip(Synth.steady())
        val (shortPeak, _) = peakAndDip(Synth.chronicShort())
        assertThat(shortPeak).isLessThan(steadyPeak)
    }

    @Test fun `someone still up before their usual wake time is shown awake, not asleep`() {
        val s = Synth.steady()
        // 04:30 the next morning, before the usual 06:45 wake, with no sleep recorded yet tonight.
        val now = s.nowLocal.toLocalDate().plusDays(1).atTime(4, 30)
        val nowI = now.atZone(s.zone).toInstant()
        val phone = app.drift.model.SourceRef(app.drift.model.SourceId.PHONE, "screen", app.drift.model.QualityTier.PHONE_INFERRED)
        val base = s.input(nowOverride = now)
        val input = base.copy(signals = app.drift.model.engine.SignalWindow(base.signals.window, listOf(app.drift.model.Signal.ScreenEvent(phone, nowI.minusSeconds(300), true))))
        val snap = Kit.engine.analyze(input)
        val at = now.minusHours(2).atZone(s.zone).toInstant()
        val day = snap.energyCurve.days.first { d -> d.points.isNotEmpty() && at >= d.points.first().at && at <= d.points.last().at }
        val seg = day.segments.first { at >= it.interval.start && at < it.interval.end }
        assertThat(seg.phase).isNotEqualTo(EnergyPhase.SLEEP)
    }

    @Test fun `without signs of being up, an unfinished night is still assumed slept`() {
        val s = Synth.steady()
        val now = s.nowLocal.toLocalDate().plusDays(1).atTime(4, 30)
        val snap = Kit.engine.analyze(s.input(nowOverride = now))
        val at = now.minusHours(2).atZone(s.zone).toInstant()
        val day = snap.energyCurve.days.first { d -> d.points.isNotEmpty() && at >= d.points.first().at && at <= d.points.last().at }
        assertThat(day.segments.first { at >= it.interval.start && at < it.interval.end }.phase).isEqualTo(EnergyPhase.SLEEP)
    }
}
