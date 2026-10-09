package app.drift.background.schedule

import app.drift.model.engine.AnalysisSnapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Pure timing rules for the morning sync (plan 5.5: expected wake + 20 min, then on unlock if missed). */
object WakeSchedule {
    val SYNC_DELAY: Duration = Duration.ofMinutes(20)
    val CONFIRM_DELAY: Duration = Duration.ofMinutes(45)
    val FALLBACK_DELAY: Duration = Duration.ofHours(3)
    /** Past this age a missed morning sync is no longer worth catching up on at unlock. */
    val CATCH_UP_LIMIT: Duration = Duration.ofHours(14)
    val DEFAULT_WAKE: LocalTime = LocalTime.of(8, 0)

    /** Expected wake time of day: the engine's recommendation, else the profile's workday wake, else 08:00. */
    fun expectedWake(snapshot: AnalysisSnapshot?, profileWake: LocalTime?, zone: ZoneId): LocalTime =
        snapshot?.recommendations?.wakeTime?.value?.atZone(zone)?.toLocalTime()?.withSecond(0)?.withNano(0)
            ?: profileWake
            ?: DEFAULT_WAKE

    /** The first occurrence of wake + [delay] strictly after [now]. */
    fun nextTrigger(wake: LocalTime, delay: Duration, now: Instant, zone: ZoneId): Instant {
        val today = now.atZone(zone).toLocalDate()
        val candidates = listOf(today.minusDays(1), today, today.plusDays(1)).map { it.atTime(wake).atZone(zone).toInstant().plus(delay) }
        return candidates.first { it.isAfter(now) }
    }

    /** The most recent occurrence of wake + [delay] at or before [now]. */
    fun lastTrigger(wake: LocalTime, delay: Duration, now: Instant, zone: ZoneId): Instant {
        val today = now.atZone(zone).toLocalDate()
        val candidates = listOf(today.plusDays(1), today, today.minusDays(1)).map { it.atTime(wake).atZone(zone).toInstant().plus(delay) }
        return candidates.first { !it.isAfter(now) }
    }

    /** True if the most recent morning trigger has passed without a sync after it, and it is still recent. */
    fun isMissed(wake: LocalTime, lastSync: Instant?, now: Instant, zone: ZoneId): Boolean {
        val trigger = lastTrigger(wake, SYNC_DELAY, now, zone)
        if (Duration.between(trigger, now) > CATCH_UP_LIMIT) return false
        return lastSync == null || lastSync.isBefore(trigger)
    }
}
