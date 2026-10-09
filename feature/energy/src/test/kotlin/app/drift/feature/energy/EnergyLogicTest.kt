package app.drift.feature.energy

import app.drift.model.CheckIn
import app.drift.model.Interval
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.HabitCard
import app.drift.model.engine.HabitKind
import app.drift.testing.fixtures.FixtureContext
import app.drift.testing.fixtures.SnapshotFixtures
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class EnergyLogicTest {
    private val ctx = FixtureContext.IST
    private val snapshot = SnapshotFixtures.full(ctx)
    private val days = snapshot.energyCurve.days
    private val today = snapshot.energyCurve.forDate(ctx.today)!!

    @Test fun currentSegmentFindsThePhaseContainingNow() {
        val seg = today.segments.first { it.phase == EnergyPhase.MORNING_PEAK }
        val inside = seg.interval.start.plusSeconds(60)
        assertThat(currentSegment(today, inside)).isEqualTo(seg)
        assertThat(currentSegment(today, seg.interval.end)?.phase).isNotEqualTo(EnergyPhase.MORNING_PEAK)
        assertThat(currentSegment(null, inside)).isNull()
    }

    @Test fun nextSegmentIsTheEarliestOneStartingAfterNow() {
        val firstStart = today.segments.minOf { it.interval.start }
        val next = nextSegment(days, firstStart.minusSeconds(1), ctx.zone)!!
        assertThat(next.at).isEqualTo(firstStart)
        val afterEverything = days.maxOf { d -> d.segments.maxOf { it.interval.start } }
        assertThat(nextSegment(days, afterEverything, ctx.zone)).isNull()
    }

    @Test fun nextPeakAndDipAreStrictlyAfterNow() {
        val dip = today.segments.first { it.phase == EnergyPhase.AFTERNOON_DIP }.extremumAt!!
        val beforeDip = upcomingExtremum(days, dip.minusSeconds(1), ctx.zone, DIP_PHASES)!!
        assertThat(beforeDip.at).isEqualTo(dip)
        assertThat(beforeDip.laterDay).isFalse()
        val afterDip = upcomingExtremum(days, dip, ctx.zone, DIP_PHASES)
        assertThat(afterDip?.at).isNotEqualTo(dip)
        if (afterDip != null) assertThat(afterDip.laterDay).isTrue()
    }

    @Test fun peakAfterLastPeakOfTodayRollsToTomorrow() {
        val lastPeakToday = today.segments.filter { it.phase in PEAK_PHASES }.maxOf { it.extremumAt!! }
        val next = upcomingExtremum(days, lastPeakToday, ctx.zone, PEAK_PHASES)
        assertThat(next).isNotNull()
        assertThat(next!!.laterDay).isTrue()
    }

    @Test fun flatDaysNeverOfferADip() {
        val flat = SnapshotFixtures.flatCurve(ctx)
        val flatDays = flat.energyCurve.days
        assertThat(flatDays.all { it.tooFlatForDip }).isTrue()
        assertThat(upcomingExtremum(flatDays, flatDays.first().points.first().at, ctx.zone, DIP_PHASES)).isNull()
        assertThat(dayHighlights(flatDays.first()).dip).isNull()
    }

    @Test fun highlightsPickTheHighestPeak() {
        val h = dayHighlights(today)
        val peaks = today.segments.filter { it.phase in PEAK_PHASES }.mapNotNull { it.extremumAt }
        assertThat(peaks).contains(h.peak)
        val best = peaks.maxBy { levelAt(today, it)!! }
        assertThat(h.peak).isEqualTo(best)
    }

    @Test fun habitsSortUpcomingFirstThenPast() {
        val t0 = Instant.parse("2026-10-14T10:00:00Z")
        fun habit(kind: HabitKind, fromH: Long, toH: Long) =
            HabitCard(kind, Interval(t0.plus(Duration.ofHours(fromH)), t0.plus(Duration.ofHours(toH))), "b")
        val past = habit(HabitKind.MORNING_LIGHT, -5, -4)
        val soon = habit(HabitKind.CAFFEINE_CUTOFF, 1, 2)
        val later = habit(HabitKind.DIM_LIGHTS, 6, 7)
        val sorted = sortHabits(listOf(later, past, soon), t0)
        assertThat(sorted).containsExactly(soon, later, past).inOrder()
        assertThat(isHabitPast(past, t0)).isTrue()
        assertThat(isHabitPast(soon, t0)).isFalse()
    }

    @Test fun checkInDueOnlyAfterTheIntervalAndNotWhileAsleep() {
        val now = ctx.now
        val recent = CheckIn.EnergyRating(now.minus(Duration.ofHours(1)), 3)
        val old = CheckIn.EnergyRating(now.minus(Duration.ofHours(5)), 3)
        assertThat(isCheckInDue(emptyList(), now, EnergyPhase.MORNING_PEAK)).isTrue()
        assertThat(isCheckInDue(listOf(old), now, EnergyPhase.MORNING_PEAK)).isTrue()
        assertThat(isCheckInDue(listOf(recent), now, EnergyPhase.MORNING_PEAK)).isFalse()
        assertThat(isCheckInDue(emptyList(), now, EnergyPhase.SLEEP)).isFalse()
        assertThat(isCheckInDue(emptyList(), now, null)).isTrue()
    }

    @Test fun millisToNextMinuteIsNeverZero() {
        assertThat(millisToNextMinute(Instant.parse("2026-10-14T07:30:00Z"))).isEqualTo(60_000)
        assertThat(millisToNextMinute(Instant.parse("2026-10-14T07:30:45.500Z"))).isEqualTo(14_500)
    }

    @Test fun snapRoundsToFiveMinutes() {
        assertThat(snapToStep(Instant.parse("2026-10-14T07:32:00Z"))).isEqualTo(Instant.parse("2026-10-14T07:30:00Z"))
        assertThat(snapToStep(Instant.parse("2026-10-14T07:33:00Z"))).isEqualTo(Instant.parse("2026-10-14T07:35:00Z"))
    }

    @Test fun adjustClampsToWindowAndMinimumLength() {
        val ep = pendingEpisode(ctx)
        val window = adjustWindow(ep)
        assertThat(window.start).isEqualTo(ep.start.minus(Duration.ofHours(3)))
        assertThat(clampStart(ep.start.minus(Duration.ofHours(9)), ep.end, window)).isEqualTo(window.start)
        assertThat(clampStart(ep.end.plus(Duration.ofHours(1)), ep.end, window)).isEqualTo(ep.end.minus(Duration.ofMinutes(15)))
        assertThat(clampEnd(ep.start.minus(Duration.ofHours(1)), ep.start, window)).isEqualTo(ep.start.plus(Duration.ofMinutes(15)))
        assertThat(clampEnd(ep.end.plus(Duration.ofHours(9)), ep.start, window)).isEqualTo(window.end)
    }
}
