package app.drift.feature.energy

import app.drift.model.CheckIn
import app.drift.model.Interval
import app.drift.model.SleepEpisode
import app.drift.model.engine.DayCurve
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.EnergySegment
import app.drift.model.engine.HabitCard
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * Pure functions behind the Energy screen: which phase is it, what comes next, how habits are ordered, when the
 * check-in card is due and how the sleep range is adjusted. No Android types, so they are unit-tested directly.
 */

internal val PEAK_PHASES = setOf(EnergyPhase.MORNING_PEAK, EnergyPhase.EVENING_PEAK)
internal val DIP_PHASES = setOf(EnergyPhase.AFTERNOON_DIP)

/** An upcoming event. [laterDay] is true when it falls on a different local date than "now". */
internal data class Upcoming(
    val at: Instant,
    val phase: EnergyPhase,
    val laterDay: Boolean,
    /** For peak and dip phases, the moment of the extremum itself; [at] is where the phase begins. */
    val extremumAt: Instant? = null,
) {
    /** The time to show: the extremum for peaks and dips, else the phase start. */
    val displayAt: Instant get() = extremumAt ?: at
}

/** The segment of [day] that contains [now], or null when [now] is outside every segment. */
internal fun currentSegment(day: DayCurve?, now: Instant): EnergySegment? =
    day?.segments?.firstOrNull { it.interval.contains(now) }

/** The first segment, across all [days], that starts after [now]. */
internal fun nextSegment(days: List<DayCurve>, now: Instant, zone: ZoneId): Upcoming? =
    days.sortedBy { it.date }
        .flatMap { it.segments }
        .filter { it.interval.start.isAfter(now) }
        .minByOrNull { it.interval.start }
        ?.let {
            val shown = it.extremumAt?.takeIf { _ -> it.phase in PEAK_PHASES || it.phase in DIP_PHASES } ?: it.interval.start
            Upcoming(
                it.interval.start, it.phase,
                laterDay = shown.localDate(zone) != now.localDate(zone),
                extremumAt = shown.takeIf { _ -> it.extremumAt != null && (it.phase in PEAK_PHASES || it.phase in DIP_PHASES) },
            )
        }

/** The next peak or dip (by its extremum time) after [now]. Days flagged too flat never offer a dip. */
internal fun upcomingExtremum(days: List<DayCurve>, now: Instant, zone: ZoneId, phases: Set<EnergyPhase>): Upcoming? =
    days.sortedBy { it.date }
        .filter { !(phases == DIP_PHASES && it.tooFlatForDip) }
        .flatMap { day -> day.segments.filter { it.phase in phases && it.extremumAt != null } }
        .filter { it.extremumAt!!.isAfter(now) }
        .minByOrNull { it.extremumAt!! }
        ?.let { Upcoming(it.extremumAt!!, it.phase, laterDay = it.extremumAt!!.localDate(zone) != now.localDate(zone)) }

/** Highest peak (by level) and the dip of one day, for the Yesterday/Tomorrow summary. */
internal data class DayHighlights(val peak: Instant?, val dip: Instant?)

internal fun dayHighlights(day: DayCurve): DayHighlights {
    val peak = day.segments.filter { it.phase in PEAK_PHASES && it.extremumAt != null }
        .maxByOrNull { levelAt(day, it.extremumAt!!) ?: 0.0 }?.extremumAt
    val dip = if (day.tooFlatForDip) null else day.segments.firstOrNull { it.phase in DIP_PHASES }?.extremumAt
    return DayHighlights(peak, dip)
}

/** Level of the sample closest to [at], or null for an empty curve. */
internal fun levelAt(day: DayCurve, at: Instant): Double? =
    day.points.minByOrNull { Math.abs(Duration.between(it.at, at).seconds) }?.level

/** Upcoming habits first (soonest window first), then the ones whose window is over. */
internal fun sortHabits(habits: List<HabitCard>, now: Instant): List<HabitCard> {
    val (past, upcoming) = habits.partition { it.window.end.isBefore(now) }
    return upcoming.sortedBy { it.window.start } + past.sortedBy { it.window.start }
}

internal fun isHabitPast(habit: HabitCard, now: Instant): Boolean = habit.window.end.isBefore(now)

internal val CHECK_IN_INTERVAL: Duration = Duration.ofHours(4)

/** The check-in prompt shows when nothing was logged in the last few hours and the user is not asleep. */
internal fun isCheckInDue(checkIns: List<CheckIn>, now: Instant, phase: EnergyPhase?): Boolean {
    if (phase == EnergyPhase.SLEEP) return false
    val cutoff = now.minus(CHECK_IN_INTERVAL)
    return checkIns.none { it.at.isAfter(cutoff) && !it.at.isAfter(now) }
}

/** Seconds to wait so the next tick lands on a minute boundary (never 0). */
internal fun millisToNextMinute(now: Instant): Long = 60_000L - (now.toEpochMilli() % 60_000L)

// --- Sleep range adjustment (drag handles, 5-minute steps) --------------------------------------------------------

internal const val ADJUST_STEP_MINUTES = 5L
internal const val MIN_ADJUST_MINUTES = 15L
internal val ADJUST_MARGIN: Duration = Duration.ofHours(3)

/** How far the handles may travel: three hours either side of the inferred night. */
internal fun adjustWindow(episode: SleepEpisode): Interval =
    Interval(episode.start.minus(ADJUST_MARGIN), episode.end.plus(ADJUST_MARGIN))

/** Rounds to the nearest 5 minutes. */
internal fun snapToStep(t: Instant): Instant {
    val step = ADJUST_STEP_MINUTES * 60
    return Instant.ofEpochSecond(Math.round(t.epochSecond / step.toDouble()) * step)
}

/** Snaps [candidate] and keeps at least [MIN_ADJUST_MINUTES] before [end] and inside [window]. */
internal fun clampStart(candidate: Instant, end: Instant, window: Interval): Instant {
    val latest = end.minus(Duration.ofMinutes(MIN_ADJUST_MINUTES))
    return minOf(maxOf(snapToStep(candidate), window.start), latest)
}

/** Snaps [candidate] and keeps at least [MIN_ADJUST_MINUTES] after [start] and inside [window]. */
internal fun clampEnd(candidate: Instant, start: Instant, window: Interval): Instant {
    val earliest = start.plus(Duration.ofMinutes(MIN_ADJUST_MINUTES))
    return maxOf(minOf(snapToStep(candidate), window.end), earliest)
}

private fun Instant.localDate(zone: ZoneId): LocalDate = atZone(zone).toLocalDate()
