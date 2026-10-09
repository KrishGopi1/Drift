package app.drift.engine

import app.drift.model.CheckIn
import app.drift.model.ConfidenceLevel
import app.drift.model.Provenance
import app.drift.model.engine.EnergyPhase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class EnergyTest {
    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 9, 14)

    private fun curve(s: Scenario = Synth.steady(utc)) = Kit.engine.analyze(s.input())

    @Test fun `curve covers yesterday today and tomorrow at five minute resolution`() {
        val c = curve().energyCurve
        assertThat(c.days.map { it.date }).containsExactly(today.minusDays(1), today, today.plusDays(1)).inOrder()
        for (d in c.days) {
            assertThat(d.points).hasSize(288)
            assertThat(Duration.between(d.points[0].at, d.points[1].at)).isEqualTo(Duration.ofMinutes(5))
            assertThat(d.points.all { it.level in 0.0..100.0 }).isTrue()
            assertThat(d.provenance).isEqualTo(Provenance.ESTIMATED)
        }
        assertThat(c.forDate(today)).isNotNull()
    }

    @Test fun `typical day has grogginess a morning peak an afternoon dip and an evening peak in order`() {
        val day = curve().energyCurve.forDate(today)!!
        val order = day.segments.filter { it.phase != EnergyPhase.SLEEP }.map { it.phase }
        val expected = listOf(EnergyPhase.GROGGINESS, EnergyPhase.MORNING_PEAK, EnergyPhase.AFTERNOON_DIP, EnergyPhase.EVENING_PEAK)
        assertThat(order.take(4)).isEqualTo(expected)
        val dip = day.segments.first { it.phase == EnergyPhase.AFTERNOON_DIP }
        val hour = dip.extremumAt!!.atZone(utc).hour
        assertThat(hour).isIn(com.google.common.collect.Range.closed(11, 17))
        assertThat(day.tooFlatForDip).isFalse()
        // Segments never overlap and are ordered.
        val segs = day.segments
        for (i in 1 until segs.size) assertThat(segs[i].interval.start).isAtLeast(segs[i - 1].interval.start)
    }

    @Test fun `sleep inertia depresses the first hour after waking`() {
        val day = curve().energyCurve.forDate(today)!!
        val wake = day.segments.first { it.phase == EnergyPhase.GROGGINESS }.interval.start
        val at = { m: Long -> day.points.minByOrNull { Math.abs(Duration.between(it.at, wake.plus(Duration.ofMinutes(m))).toMinutes()) }!!.level }
        assertThat(at(0)).isLessThan(at(120))
    }

    @Test fun `short sleep lowers the next day`() {
        val base = Synth.steady(utc)
        val lastDate = LocalDate.of(2026, 9, 13)
        val short = Scenario(
            "short", utc, base.profile, base.nowLocal,
            base.nights.map { n -> if (n.id == "n-$lastDate") Scenario.LocalNight(lastDate.atTime(2, 30), n.end, utc, id = n.id) else n },
        )
        val a = curve(base).energyCurve.forDate(today)!!.points.map { it.level }.average()
        val b = curve(short).energyCurve.forDate(today)!!.points.map { it.level }.average()
        assertThat(b).isLessThan(a)
    }

    @Test fun `no prominent dip is reported as too flat instead of invented`() {
        val flat = DefaultSleepEngine(ParameterSet.DEFAULT.withOverrides("energy.dipProminence" to 60.0), failSafe = false)
        val d = flat.analyze(Synth.steady(utc).input()).energyCurve.forDate(today)!!
        assertThat(d.tooFlatForDip).isTrue()
        assertThat(d.segments.none { it.phase == EnergyPhase.AFTERNOON_DIP }).isTrue()
    }

    @Test fun `tomorrow assumes the recommended sleep`() {
        val snap = curve()
        val tomorrow = snap.energyCurve.forDate(today.plusDays(1))!!
        val sleepEnd = tomorrow.segments.first { it.phase == EnergyPhase.SLEEP }.interval.end
        val wake = snap.recommendations.wakeTime.value
        assertThat(Math.abs(Duration.between(sleepEnd, wake).toMinutes())).isAtMost(5)
        assertThat(tomorrow.confidence).isNotEqualTo(ConfidenceLevel.HIGH)
    }

    @Test fun `check-ins calibrate the curve only from ten on and the count is reported`() {
        val s = Synth.steady(utc)
        fun with(n: Int): Scenario = Scenario(
            s.name, s.zone, s.profile, s.nowLocal, s.nights,
            checkIns = (0 until n).map { s.nowLocal.minusDays(1L + it).withHour(16).withMinute(0) to 1 },
        )
        val none = curve(s)
        val nine = curve(with(9))
        val twelve = curve(with(12))
        assertThat(none.energyCurve.calibrationCheckIns).isEqualTo(0)
        assertThat(nine.energyCurve.calibrationCheckIns).isEqualTo(0)
        assertThat(twelve.energyCurve.calibrationCheckIns).isEqualTo(12)
        val a = none.energyCurve.forDate(today)!!.points.map { it.level }
        val b = twelve.energyCurve.forDate(today)!!.points.map { it.level }
        assertThat(a.zip(b).maxOf { (x, y) -> Math.abs(x - y) }).isGreaterThan(1.0)
        // Calibration is bounded: the afternoon cannot be pushed to arbitrary values.
        assertThat(b.all { it in 0.0..100.0 }).isTrue()
    }

    @Test fun `reaction tests count as check-ins`() {
        val s = Synth.steady(utc)
        val input = s.input().copy(
            checkIns = (0 until 10).map { CheckIn.ReactionTest(s.nowLocal.minusDays(1L + it).withHour(10).atZone(utc).toInstant(), 320.0, 0, 20) },
        )
        assertThat(Kit.engine.analyze(input).energyCurve.calibrationCheckIns).isEqualTo(10)
    }

    @Test fun `priors only still draws a labelled low confidence curve`() {
        val snap = Kit.engine.analyze(Kit.input(emptyList(), "2026-09-14T12:00"))
        val d = snap.energyCurve.forDate(today)!!
        assertThat(d.confidence).isEqualTo(ConfidenceLevel.LOW)
        assertThat(d.provenance).isEqualTo(Provenance.ESTIMATED)
        assertThat(d.points).isNotEmpty()
    }
}
