package app.drift.engine

import app.drift.engine.models.FreeNightDetector
import app.drift.engine.models.NightType
import app.drift.engine.models.Preprocessor
import app.drift.engine.models.SleepNeedModel
import app.drift.model.AlarmKind
import app.drift.model.DayAnnotation
import app.drift.model.Signal
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.QualityTier
import app.drift.model.engine.AgeBand
import app.drift.model.engine.Goals
import app.drift.model.engine.Schedule
import app.drift.model.engine.UserProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class SleepNeedTest {
    private val p = ParameterSet.DEFAULT
    private fun need(s: Scenario, anns: List<DayAnnotation> = s.annotations): Triple<app.drift.engine.models.NeedResult, Map<Long, NightType>, app.drift.engine.internal.Preprocessed> {
        val input = s.input().copy(annotations = anns)
        val pre = Preprocessor(p).run(input)
        val types = FreeNightDetector(p).classify(pre, input).types
        return Triple(SleepNeedModel(p).run(pre, types, input), types, pre)
    }

    @Test fun `zero episodes returns the age band prior with a wide band`() {
        val input = Kit.input(emptyList(), "2026-09-10T12:00", profile = Synth.profile(age = AgeBand.AGE_65_PLUS))
        val pre = Preprocessor(p).run(input)
        val r = SleepNeedModel(p).run(pre, emptyMap(), input)
        assertThat(r.displayed).isEqualTo(p.priorNeed65.v)
        assertThat(r.personal).isFalse()
        assertThat(r.upper - r.lower).isGreaterThan(90.0)
    }

    @Test fun `each age band has its own prior and older bands need less`() {
        val priors = AgeBand.values().map { p.needPrior(it) }
        assertThat(priors).isInOrder(Comparator.reverseOrder<Double>())
    }

    @Test fun `prior is shown until five informative nights exist`() {
        // Only weekends are free in the steady scenario: 1 week has 2 free nights.
        val s = Synth.steady(days = 14)
        val (r, _, _) = need(s)
        assertThat(r.informativeNights).isEqualTo(4)
        assertThat(r.personal).isFalse()
        assertThat(r.displayed).isEqualTo(p.priorNeed26.v)
        val (r2, _, _) = need(Synth.steady(days = 21))
        assertThat(r2.personal).isTrue()
    }

    @Test fun `annotations override the workday default`() {
        val s = Synth.steady(days = 14)
        val firstWorkday = LocalDate.of(2026, 8, 5) // Wednesday
        val (_, types, _) = need(s, listOf(DayAnnotation(firstWorkday, freeDay = true)))
        assertThat(types[firstWorkday.toEpochDay()]).isEqualTo(NightType.FREE)
        val saturday = LocalDate.of(2026, 8, 8)
        val (_, types2, _) = need(s, listOf(DayAnnotation(saturday, alarmSet = true)))
        assertThat(types2[saturday.toEpochDay()]).isEqualTo(NightType.ALARM)
    }

    @Test fun `a workday that wakes much later than the workday median counts as free`() {
        val base = Synth.steady(days = 28)
        val lateDate = LocalDate.of(2026, 8, 26) // a Wednesday
        val nights = base.nights.map { n ->
            if (n.id == "n-$lateDate") Scenario.LocalNight(n.start, lateDate.atTime(9, 0), n.zone, id = n.id) else n
        }
        val s = Scenario("late", base.zone, base.profile, base.nowLocal, nights)
        val (_, types, _) = need(s)
        assertThat(types[lateDate.toEpochDay()]).isEqualTo(NightType.FREE)
    }

    @Test fun `an alarm event near the wake marks an alarm night`() {
        val s = Synth.steady(days = 14)
        val saturday = LocalDate.of(2026, 8, 8)
        val wake = saturday.atTime(8, 0).atZone(s.zone).toInstant()
        val input = s.input().let {
            it.copy(signals = it.signals.copy(signals = listOf(Signal.AlarmEvent(SourceRef(SourceId.PHONE, "a", QualityTier.PHONE_APP), wake.minusSeconds(600), AlarmKind.DISMISSED))))
        }
        val pre = Preprocessor(p).run(input)
        val types = FreeNightDetector(p).classify(pre, input).types
        assertThat(types[saturday.toEpochDay()]).isEqualTo(NightType.ALARM)
    }

    @Test fun `free nights pull need toward observed sleep and narrow the band`() {
        val (r, _, _) = need(Synth.weekendCatchUp())
        assertThat(r.personal).isTrue()
        assertThat(r.displayed).isGreaterThan(p.priorNeed26.v) // free nights are ~9.6 h
        assertThat(r.upper - r.lower).isLessThan(2 * p.bandZ.v * p.priorSd.v)
    }

    @Test fun `censored alarm nights never lower need on their own`() {
        // Everyone sleeps 6 h on alarm days, and every night is an alarm night: only the censored update acts.
        val ns = Synth.nights(java.time.ZoneId.of("UTC"), Synth.UTC_START, 40) { _, _ -> LocalTime.of(0, 0) to LocalTime.of(6, 0) }
        val ann = (0 until 40).map { DayAnnotation(Synth.UTC_START.plusDays(it.toLong()), alarmSet = true) }
        val s = Scenario("alarms", java.time.ZoneId.of("UTC"), Synth.profile(), Synth.UTC_START.plusDays(40).atTime(12, 0), ns, ann)
        val (r, _, _) = need(s)
        assertThat(r.informativeNights).isEqualTo(0)
        // Sleeping 6 h under an alarm says need >= 6 h; the prior (8.0 h) must not be dragged below 6 h.
        assertThat(r.mean).isAtLeast(p.priorNeed26.v - 1.0)
    }

    @Test fun `censored update raises need when alarm sleep exceeds the prior`() {
        val ns = Synth.nights(java.time.ZoneId.of("UTC"), Synth.UTC_START, 40) { _, _ -> LocalTime.of(21, 30) to LocalTime.of(7, 0) }
        val ann = (0 until 40).map { DayAnnotation(Synth.UTC_START.plusDays(it.toLong()), alarmSet = true) }
        val s = Scenario("long-alarm", java.time.ZoneId.of("UTC"), Synth.profile(), Synth.UTC_START.plusDays(40).atTime(12, 0), ns, ann)
        val (r, _, _) = need(s)
        assertThat(r.mean).isGreaterThan(p.priorNeed26.v + 20)
    }

    @Test fun `need stays inside physiological bounds even for absurd data`() {
        val ns = Synth.nights(java.time.ZoneId.of("UTC"), Synth.UTC_START, 40) { _, _ -> LocalTime.of(14, 0) to LocalTime.of(5, 0) }
        val prof = UserProfile(AgeBand.AGE_18_25, null, Schedule(emptySet(), null, null, null), Goals())
        val s = Scenario("long", java.time.ZoneId.of("UTC"), prof, Synth.UTC_START.plusDays(40).atTime(12, 0), ns)
        val (r, _, _) = need(s)
        assertThat(r.displayed).isAtMost(p.needMax.v)
        assertThat(r.upper).isAtMost(p.needMax.v)
        assertThat(r.lower).isAtLeast(p.needMin.v)
    }

    @Test fun `rebound correction lowers the need estimate after a debt-heavy week`() {
        // Weekdays 5 h under alarm then a long free weekend night: part of the long night is rebound.
        fun scen(weekendHours: Int): Scenario {
            val ns = Synth.nights(java.time.ZoneId.of("UTC"), Synth.UTC_START, 28) { d, _ ->
                if (d.dayOfWeek in Synth.WORKDAYS) LocalTime.of(1, 0) to LocalTime.of(6, 0) else LocalTime.of(23, 0).minusHours(weekendHours - 8L) to LocalTime.of(7, 0)
            }
            return Scenario("rb", java.time.ZoneId.of("UTC"), Synth.profile(wake = LocalTime.of(6, 0)), Synth.UTC_START.plusDays(28).atTime(12, 0), ns)
        }
        val (r, _, pre) = need(scen(9))
        val freeAvg = pre.usable.filter { it.date.dayOfWeek == DayOfWeek.SATURDAY }.map { it.main!!.tst }.average()
        assertThat(r.mean).isLessThan(freeAvg)
    }

    @Test fun `a night broken by a long waking is disturbed, not free`() {
        val (r, types, _) = need(Synth.fragmentedParent())
        assertThat(types.values.count { it == NightType.DISTURBED }).isGreaterThan(8)
        // Interrupted nights are censored, so they do not count as informative.
        assertThat(r.informativeNights).isLessThan(42)
        // A user can still mark such a night as free.
        val day = LocalDate.of(2026, 8, 10)
        val (_, t2, _) = need(Synth.fragmentedParent(), listOf(DayAnnotation(day, freeDay = true)))
        assertThat(t2[day.toEpochDay()]).isEqualTo(NightType.FREE)
    }
}
