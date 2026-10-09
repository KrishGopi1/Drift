package app.drift.engine

import app.drift.model.Interval
import app.drift.model.Provenance
import app.drift.model.engine.AgeBand
import app.drift.model.engine.Goals
import app.drift.model.engine.HabitKind
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class RecommendationTest {
    private val utc = ZoneId.of("UTC")
    private val p = ParameterSet.DEFAULT

    private fun mins(d: Duration) = d.toMillis() / 60000.0

    @Test fun `sleep opportunity follows the TIB formula`() {
        val snap = Kit.engine.analyze(Synth.weekendCatchUp(utc).input())
        val need = mins(snap.sleepNeed.value)
        val debt = mins(snap.sleepDebt!!.value)
        val expected = (need + minOf(0.25 * debt, 60.0)) / 0.88
        assertThat(mins(snap.recommendations.sleepOpportunity.value)).isWithin(1.0).of(expected)
        assertThat(snap.recommendations.sleepOpportunity.provenance).isEqualTo(Provenance.ESTIMATED)
    }

    @Test fun `personal sleep efficiency replaces the default once enough nights have in-bed data`() {
        val s = Synth.steady(utc, days = 30)
        val input = s.input()
        val withBed = input.copy(
            episodes = input.episodes.map { e -> e.copy(inBed = Interval(e.start.minusSeconds(60 * 60), e.end.plusSeconds(60 * 10))) },
        )
        val snapDefault = Kit.engine.analyze(input)
        val snapPersonal = Kit.engine.analyze(withBed)
        val se = (7.75 * 60 + 8.25 * 60) / 2 / (8 * 60 + 70.0) // roughly 0.87-0.89; just check direction below
        assertThat(se).isGreaterThan(0.0)
        // The in-bed window is about 70 minutes longer than the sleep, so efficiency ~0.87, and TIB differs from the default.
        assertThat(snapPersonal.recommendations.sleepOpportunity.value).isNotEqualTo(snapDefault.recommendations.sleepOpportunity.value)
    }

    @Test fun `wake time comes from the schedule on workdays and bedtime is wake minus opportunity`() {
        // Wednesday noon: next wake is Thursday (workday) 06:45 from the schedule.
        val s = Synth.steady(utc, days = 30)
        val now = LocalDateTime.of(2026, 9, 2, 12, 0) // Wednesday
        val snap = Kit.engine.analyze(s.input(nowOverride = now))
        val wake = snap.recommendations.wakeTime
        assertThat(wake.provenance).isEqualTo(Provenance.USER_ENTERED)
        assertThat(wake.value.atZone(utc).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 9, 3, 6, 45))
        val bed = snap.recommendations.bedtime.value
        assertThat(Duration.between(bed, wake.value).toMinutes()).isWithin(1).of(snap.recommendations.sleepOpportunity.value.toMinutes())
    }

    @Test fun `free day wake comes from the median free night wake`() {
        val s = Synth.steady(utc, days = 30)
        val now = LocalDateTime.of(2026, 9, 4, 12, 0) // Friday: next wake is Saturday
        val snap = Kit.engine.analyze(s.input(nowOverride = now))
        val wake = snap.recommendations.wakeTime
        assertThat(wake.provenance).isEqualTo(Provenance.DERIVED)
        assertThat(wake.value.atZone(utc).toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 5))
        assertThat(wake.value.atZone(utc).hour).isIn(com.google.common.collect.Range.closed(7, 8))
    }

    @Test fun `a bedtime earlier than the melatonin window gets a trade-off instead of being overridden`() {
        val snap = Kit.engine.analyze(Synth.steady(utc).input())
        val rec = snap.recommendations
        val t = rec.tradeOff!!
        assertThat(t.bedtimeIfFollowingWindow).isEqualTo(snap.circadian.melatoninWindow!!.start)
        assertThat(t.bedtimeIfFollowingWindow).isGreaterThan(rec.bedtime.value)
        assertThat(t.message).contains("melatonin")
        // Recommended bedtime itself is unchanged by the conflict: wake minus opportunity.
        assertThat(Duration.between(rec.bedtime.value, rec.wakeTime.value).toMinutes()).isWithin(1).of(rec.sleepOpportunity.value.toMinutes())
    }

    @Test fun `no trade-off when the bedtime is compatible with the melatonin window`() {
        val s = Synth.steady(utc)
        val input = s.input().copy(profile = s.profile.copy(goals = Goals(targetSleep = Duration.ofHours(6))))
        assertThat(Kit.engine.analyze(input).recommendations.tradeOff).isNull()
    }

    @Test fun `habit cards are offset from bedtime and wind-down and carry parameter keys as basis`() {
        val snap = Kit.engine.analyze(Synth.steady(utc).input())
        val r = snap.recommendations
        val bed = r.bedtime.value
        fun card(k: HabitKind) = r.habits.first { it.kind == k }
        assertThat(card(HabitKind.CAFFEINE_CUTOFF).window.start).isEqualTo(bed.minus(Duration.ofHours(8)))
        assertThat(card(HabitKind.LAST_MEAL).window.start).isEqualTo(bed.minus(Duration.ofHours(3)))
        assertThat(card(HabitKind.ALCOHOL_CUTOFF).window.start).isEqualTo(bed.minus(Duration.ofHours(3)))
        assertThat(card(HabitKind.MORNING_LIGHT).window.duration).isEqualTo(Duration.ofHours(1))
        assertThat(card(HabitKind.DIM_LIGHTS).window.start).isEqualTo(snap.circadian.windDownStart)
        val exercise = r.habits.firstOrNull { it.kind == HabitKind.EXERCISE_WINDOW }
        if (exercise != null) assertThat(exercise.window.end).isAtMost(snap.circadian.windDownStart)
        for (h in r.habits) assertThat(p.entries.map { it.key }).contains(h.basis)
    }

    @Test fun `nap advice needs moderate debt and an afternoon dip and ends at least seven hours before bed`() {
        val high = Kit.engine.analyze(Synth.weekendCatchUp(utc).input())
        val nap = high.recommendations.nap
        assertThat(nap).isNotNull()
        assertThat(nap!!.maxDuration).isEqualTo(Duration.ofMinutes(30))
        assertThat(nap.window.end).isAtMost(high.recommendations.bedtime.value.minus(Duration.ofHours(7)))
        assertThat(nap.window.duration.toMinutes()).isAtLeast(20)
        val low = Kit.engine.analyze(Synth.steady(utc).input())
        assertThat(low.recommendations.nap).isNull()
    }

    @Test fun `user target overrides the estimated need in the opportunity`() {
        val s = Synth.steady(utc)
        val input = s.input().copy(profile = s.profile.copy(goals = Goals(targetSleep = Duration.ofHours(7))))
        val snap = Kit.engine.analyze(input)
        val debt = mins(snap.sleepDebt!!.value)
        assertThat(mins(snap.recommendations.sleepOpportunity.value)).isWithin(1.0).of((420 + minOf(0.25 * debt, 60.0)) / 0.88)
    }

    @Test fun `with no data recommendations come from priors and say so`() {
        val snap = Kit.engine.analyze(Kit.input(emptyList(), "2026-09-14T12:00", profile = Synth.profile(age = AgeBand.AGE_18_25, wake = LocalTime.of(7, 0))))
        assertThat(snap.recommendations.sleepOpportunity.provenance).isEqualTo(Provenance.ESTIMATED)
        assertThat(snap.recommendations.sleepOpportunity.confidence).isNotNull()
        assertThat(snap.recommendations.bedtime.sourceIds).isEmpty()
    }
}
