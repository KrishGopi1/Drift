package app.drift.engine

import app.drift.engine.internal.Circ
import app.drift.engine.models.FreeNightDetector
import app.drift.engine.models.NightType
import app.drift.engine.models.Preprocessor
import app.drift.engine.models.RegularityModel
import app.drift.model.Provenance
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.engine.ChronotypeLabel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RegularityTest {
    private val p = ParameterSet.DEFAULT
    private val utc = ZoneId.of("UTC")

    private fun report(s: Scenario) = Kit.engine.analyze(s.input()).regularity

    /** Straightforward minute-by-minute Phillips et al. (2017) reference over days [first, last]. */
    private fun referenceSri(s: Scenario, firstDay: LocalDate, lastDay: LocalDate): Double {
        // The last day is only known up to the final wake-up (the evening has not been recorded yet).
        val lastWake = s.nights.filter { it.kind == app.drift.model.EpisodeKind.MAIN && it.end.toLocalDate() == lastDay }.maxOf { it.end }
        val base = firstDay.toEpochDay() * 1440
        val minutes = ((lastDay.toEpochDay() - firstDay.toEpochDay() + 1) * 1440).toInt()
        val asleep = BooleanArray(minutes)
        for (n in s.nights) {
            val a = n.start.toEpochSecond(java.time.ZoneOffset.UTC) / 60 - base
            val b = n.end.toEpochSecond(java.time.ZoneOffset.UTC) / 60 - base
            for (m in a.coerceAtLeast(0) until b.coerceAtMost(minutes.toLong())) asleep[m.toInt()] = true
        }
        var same = 0
        var total = 0
        val knownEnd = (lastWake.toEpochSecond(java.time.ZoneOffset.UTC) / 60 - base).toInt()
        for (i in 0 until minutes - 1440) {
            if (i + 1440 >= knownEnd) break
            total++
            if (asleep[i] == asleep[i + 1440]) same++
        }
        return -100.0 + 200.0 * same / total
    }

    @Test fun `SRI matches the reference implementation within one point on several histories`() {
        val last = LocalDate.of(2026, 9, 13)
        for (s in listOf(Synth.steady(utc), Synth.weekendCatchUp(utc), Synth.fragmentedParent(utc), Synth.chronicShort(utc))) {
            val r = report(s)
            val ref7 = referenceSri(s, last.minusDays(6), last)
            val ref14 = referenceSri(s, last.minusDays(13), last)
            assertThat(r.sri7!!.value).isWithin(1.0).of(ref7)
            assertThat(r.sri14!!.value).isWithin(1.0).of(ref14)
        }
    }

    @Test fun `a perfectly regular sleeper scores 100`() {
        val ns = (0 until 20).map { i ->
            val d = LocalDate.of(2026, 8, 20).plusDays(i.toLong())
            Scenario.LocalNight(d.minusDays(1).atTime(23, 0), d.atTime(7, 0), utc, id = "r$i")
        }
        val s = Scenario("perfect", utc, Synth.profile(), LocalDate.of(2026, 9, 9).atTime(12, 0), ns)
        assertThat(report(s).sri7!!.value).isWithin(1e-6).of(100.0)
        assertThat(report(s).sri7!!.provenance).isEqualTo(Provenance.DERIVED)
    }

    @Test fun `irregular sleep scores lower than regular sleep`() {
        val regular = report(Synth.steady(utc)).sri14!!.value
        val irregular = report(Synth.rotatingNightShift(utc)).sri14!!.value
        assertThat(irregular).isLessThan(regular - 15)
    }

    @Test fun `SRI needs at least five paired days`() {
        assertThat(report(Synth.steady(utc, days = 5)).sri7).isNull()
        assertThat(report(Synth.steady(utc, days = 7)).sri7).isNotNull()
    }

    @Test fun `trend lists daily SRI oldest first`() {
        val t = report(Synth.steady(utc)).trend
        assertThat(t).isNotEmpty()
        assertThat(t.map { it.first }).isInOrder()
    }

    @Test fun `circular standard deviation handles wrap around midnight`() {
        // Onsets 23:50 and 00:10 are 20 minutes apart, not 23 hours.
        val sd = Circ.sd(listOf(23 * 60 + 50.0, 10.0, 23 * 60 + 50.0, 10.0))!!
        assertThat(sd).isLessThan(15.0)
        assertThat(Circ.mean(listOf(23 * 60 + 50.0, 10.0))!!).isAnyOf(0.0, 1439.9999999999995, 0.0000000001)
    }

    @Test fun `timing spread is small for a steady sleeper and large for rotating shifts`() {
        val steady = report(Synth.steady(utc))
        assertThat(steady.onsetSpread!!.value.toMinutes()).isLessThan(30)
        assertThat(steady.onsetSpread!!.provenance).isEqualTo(Provenance.DERIVED)
        val shift = report(Synth.rotatingNightShift(utc))
        assertThat(shift.onsetSpread!!.value.toMinutes()).isGreaterThan(120)
    }

    @Test fun `social jetlag is the free day minus workday midpoint`() {
        // Workday 23:00-06:45 (mid 02:52) vs weekend 23:30-08:00 (mid 03:45) -> about 53 minutes.
        val sjl = report(Synth.steady(utc)).socialJetlag!!.value.toMinutes()
        assertThat(sjl).isIn(com.google.common.collect.Range.closed(40L, 65L))
        assertThat(report(Synth.steady(utc, days = 3)).socialJetlag).isNull()
    }

    @Test fun `chronotype leans on the questionnaire until four free nights then ignores it`() {
        fun chrono(days: Int, answer: ChronotypeAnswer) =
            Kit.engine.analyze(Synth.steady(utc, days).input().copy(profile = Synth.profile(chrono = answer))).regularity.chronotype!!
        val none = chrono(5, ChronotypeAnswer.DEFINITELY_EVENING) // 5 days from a Monday: no weekend yet
        assertThat(none.freeNightsUsed).isEqualTo(0)
        assertThat(none.questionnaireWeight).isEqualTo(1.0)
        assertThat(none.label).isEqualTo(ChronotypeLabel.LATE)
        assertThat(none.msfscMinutes.provenance).isEqualTo(Provenance.ESTIMATED)
        val late = chrono(42, ChronotypeAnswer.DEFINITELY_EVENING)
        val early = chrono(42, ChronotypeAnswer.DEFINITELY_MORNING)
        assertThat(late.questionnaireWeight).isEqualTo(0.0)
        assertThat(late.msfscMinutes.value).isWithin(1e-6).of(early.msfscMinutes.value)
        val partial = chrono(15, ChronotypeAnswer.DEFINITELY_EVENING) // 4 free nights -> weight 0
        assertThat(partial.freeNightsUsed).isAtLeast(4)
        val one = chrono(9, ChronotypeAnswer.DEFINITELY_EVENING) // 2 free nights -> weight 0.5
        assertThat(one.questionnaireWeight).isWithin(1e-9).of(0.5)
    }

    @Test fun `classification of free and work nights is shared with the need model`() {
        val s = Synth.steady(utc)
        val input = s.input()
        val pre = Preprocessor(p).run(input)
        val types = FreeNightDetector(p).classify(pre, input).types
        val r = RegularityModel(p).run(pre, types, input)
        assertThat(types.values.count { it == NightType.FREE }).isEqualTo(r.chronotype!!.freeNightsUsed)
    }
}
