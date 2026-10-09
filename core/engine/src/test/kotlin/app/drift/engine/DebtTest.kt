package app.drift.engine

import app.drift.engine.models.DebtMath
import app.drift.engine.models.DebtModel
import app.drift.engine.models.FreeNightDetector
import app.drift.engine.models.Preprocessor
import app.drift.engine.models.SleepNeedModel
import app.drift.model.Provenance
import app.drift.model.engine.DebtBand
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.math.abs

class DebtTest {
    private val p = ParameterSet.DEFAULT
    private val lambda = DebtMath.lambda(p)

    private fun debt(s: Scenario) = Kit.engine.analyze(s.input())

    @Test fun `lambda gives a seven day half life`() {
        assertThat(Math.pow(lambda, 7.0)).isWithin(1e-9).of(0.5)
    }

    @Test fun `leaky accumulator follows the plan equations`() {
        // Short night adds the shortfall after leaking yesterday's debt.
        assertThat(DebtMath.step(100.0, 480.0, 420.0, lambda, 0.6)).isWithin(1e-9).of(lambda * 100 + 60)
        // A long night repays eta times the surplus.
        assertThat(DebtMath.step(100.0, 480.0, 540.0, lambda, 0.6)).isWithin(1e-9).of(lambda * 100 - 36)
        // Debt never goes below zero.
        assertThat(DebtMath.step(10.0, 480.0, 700.0, lambda, 0.6)).isEqualTo(0.0)
    }

    @Test fun `daily contributions rebuild the displayed debt`() {
        val snap = debt(Synth.weekendCatchUp())
        var d = 0.0
        for (row in snap.debtTrend) {
            d = lambda * d + row.contribution.toMillis() / 60000.0
            assertThat(row.debtAfter.toMillis() / 60000.0).isWithin(0.1).of(d)
        }
        assertThat(snap.sleepDebt!!.value.toMillis() / 60000.0).isWithin(0.1).of(d)
    }

    @Test fun `window covers 14 sleep days ending on the last sleep day`() {
        val snap = debt(Synth.steady())
        assertThat(snap.debtTrend).hasSize(14)
        assertThat(snap.debtTrend.last().date).isEqualTo(LocalDate.of(2026, 9, 13))
        assertThat(snap.debtTrend.first().date).isEqualTo(LocalDate.of(2026, 8, 31))
    }

    @Test fun `naps are credited in full up to 90 minutes then at half rate`() {
        val n = Preprocessor(p).run(Kit.input(listOf(Kit.ep("n1", "2026-09-02T14:00", "2026-09-02T16:30"), Kit.ep("m", "2026-09-01T23:00", "2026-09-02T07:00")), "2026-09-03T12:00"))
        val night = n.byDay.values.single()
        assertThat(night.naps.single().tst).isEqualTo(150.0)
        assertThat(night.napCredit(p.napFullCredit.v, p.napOverRate.v)).isEqualTo(90.0 + 30.0)
    }

    @Test fun `weekend catch up leaves moderate debt and the contribution list shows who built it`() {
        val snap = debt(Synth.weekendCatchUp())
        assertThat(snap.debtBand).isEqualTo(DebtBand.MODERATE)
        val weekdays = snap.debtTrend.filter { it.date.dayOfWeek.value in 1..5 }
        assertThat(weekdays.any { it.contribution.toMinutes() > 0 }).isTrue()
        val weekend = snap.debtTrend.filter { it.date.dayOfWeek.value in 6..7 }
        assertThat(weekend.all { it.contribution.toMinutes() < 0 }).isTrue()
    }

    @Test fun `debt bands use the plan thresholds`() {
        val low = debt(Synth.steady())
        assertThat(low.debtBand).isEqualTo(DebtBand.LOW)
        // A person who only sleeps four hours against the prior need: far above 10 h of debt after two weeks.
        val ns = Synth.nights(java.time.ZoneId.of("UTC"), Synth.UTC_START, 6) { _, _ -> java.time.LocalTime.of(2, 0) to java.time.LocalTime.of(6, 0) }
        val s = Scenario("brutal", java.time.ZoneId.of("UTC"), Synth.profile(), Synth.UTC_START.plusDays(6).atTime(12, 0), ns)
        val snap = debt(s)
        assertThat(snap.debtBand).isEqualTo(DebtBand.HIGH)
    }

    @Test fun `one or two missing nights are imputed and widen the band`() {
        val full = debt(Synth.steady())
        val gap = debt(Synth.missingWeek(gapFrom = 39, gapLen = 2)) // two nights before the last day
        assertThat(gap.debtTrend.count { it.imputed }).isEqualTo(2)
        assertThat(gap.debtTrend.filter { it.imputed }.all { it.sleep == null }).isTrue()
        val w1 = full.sleepDebt!!.confidence!!.let { it.upper - it.lower }
        val w2 = gap.sleepDebt!!.confidence!!.let { it.upper - it.lower }
        assertThat(w2).isGreaterThan(w1)
    }

    @Test fun `three consecutive missing recent nights make debt unavailable`() {
        val snap = debt(Synth.missingWeek(gapFrom = 38, gapLen = 4))
        assertThat(snap.sleepDebt).isNull()
        assertThat(snap.debtBand).isNull()
        assertThat(snap.dataSufficiency.debtAvailable).isFalse()
        val snap2 = debt(Synth.missingWeek(gapFrom = 39, gapLen = 3))
        assertThat(snap2.sleepDebt).isNull()
    }

    @Test fun `an older long gap does not cancel debt once data resumes but is not guessed`() {
        val snap = debt(Synth.missingWeek(gapFrom = 33, gapLen = 5))
        assertThat(snap.sleepDebt).isNotNull()
        val unknown = snap.debtTrend.filter { it.sleep == null && !it.imputed }
        assertThat(unknown).hasSize(5)
        assertThat(unknown.all { it.contribution.isZero }).isTrue()
    }

    @Test fun `measured sleep keeps its provenance and the debt is estimated`() {
        val snap = debt(Synth.steady())
        assertThat(snap.sleepDebt!!.provenance).isEqualTo(Provenance.ESTIMATED)
        assertThat(snap.sleepDebt!!.confidence).isNotNull()
        assertThat(snap.debtTrend.last().sleep!!.provenance).isEqualTo(Provenance.MEASURED)
    }

    @Test fun `user edited nights are user entered`() {
        val eps = (0 until 20).map { i ->
            val d = LocalDate.of(2026, 8, 20).plusDays(i.toLong())
            Kit.ep("e$i", "${d.minusDays(1)}T23:00", "${d}T07:00", edited = i == 19)
        }
        val snap = Kit.engine.analyze(Kit.input(eps, "2026-09-09T12:00"))
        assertThat(snap.debtTrend.last().sleep!!.provenance).isEqualTo(Provenance.USER_ENTERED)
    }

    @Test fun `fitted eta stays within bounds and defaults without enough free night pairs`() {
        val s = Synth.steady()
        val input = s.input()
        val pre = Preprocessor(p).run(input)
        val types = FreeNightDetector(p).classify(pre, input).types
        val need = SleepNeedModel(p).run(pre, types, input)
        assertThat(need.fittedEta).isAtLeast(p.debtEtaMin.v)
        assertThat(need.fittedEta).isAtMost(p.debtEtaMax.v)
        val short = Preprocessor(p).run(Synth.steady(days = 9).input())
        val n2 = SleepNeedModel(p).run(short, emptyMap(), Synth.steady(days = 9).input())
        assertThat(abs(n2.fittedEta - p.debtEta.v)).isLessThan(1e-9)
    }
}
