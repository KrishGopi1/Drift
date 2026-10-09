package app.drift.background.reminders

import app.drift.background.schedule.WakeSchedule
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.HabitKind
import app.drift.model.repository.AppSettings
import app.drift.model.repository.ReminderKind
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** One alarm to arm: fire anywhere in [at], [at] + [ReminderPlanner.WINDOW]. */
data class PlannedReminder(val kind: ReminderKind, val at: Instant)

/** Pure: which reminders get which windows for a snapshot and the user's settings. */
object ReminderPlanner {
    val WINDOW: Duration = Duration.ofMinutes(10)
    val HORIZON: Duration = Duration.ofHours(36)

    fun plan(
        snapshot: AnalysisSnapshot,
        settings: AppSettings,
        now: Instant,
        zone: ZoneId,
        profileWake: LocalTime? = null,
    ): List<PlannedReminder> {
        fun enabled(kind: ReminderKind) = settings.reminders[kind] == true
        val candidates = buildList {
            if (enabled(ReminderKind.WIND_DOWN)) snapshot.circadian.windDownStart?.let { add(PlannedReminder(ReminderKind.WIND_DOWN, it)) }
            if (enabled(ReminderKind.MELATONIN_WINDOW)) snapshot.circadian.melatoninWindow?.let { add(PlannedReminder(ReminderKind.MELATONIN_WINDOW, it.start)) }
            habit(snapshot, HabitKind.CAFFEINE_CUTOFF, ReminderKind.CAFFEINE_CUTOFF, now, ::enabled)?.let(::add)
            habit(snapshot, HabitKind.MORNING_LIGHT, ReminderKind.MORNING_LIGHT, now, ::enabled)?.let(::add)
            if (enabled(ReminderKind.CONFIRM_SLEEP)) {
                val wake = WakeSchedule.expectedWake(snapshot, profileWake, zone)
                add(PlannedReminder(ReminderKind.CONFIRM_SLEEP, WakeSchedule.nextTrigger(wake, WakeSchedule.CONFIRM_DELAY, now, zone)))
            }
            if (enabled(ReminderKind.ENERGY_CHECK_IN)) {
                snapshot.energyCurve.days.flatMap { it.segments }
                    .filter { it.phase == EnergyPhase.AFTERNOON_DIP }
                    .mapNotNull { it.extremumAt }
                    .filter { it.isAfter(now) }
                    .minOrNull()
                    ?.let { add(PlannedReminder(ReminderKind.ENERGY_CHECK_IN, it)) }
            }
        }
        val horizon = now.plus(HORIZON)
        return candidates
            .filter { it.at.isAfter(now) && !it.at.isAfter(horizon) }
            .groupBy { it.kind }.map { (_, list) -> list.minBy { it.at } }
            .sortedBy { it.at }
    }

    private fun habit(
        snapshot: AnalysisSnapshot,
        habit: HabitKind,
        kind: ReminderKind,
        now: Instant,
        enabled: (ReminderKind) -> Boolean,
    ): PlannedReminder? {
        if (!enabled(kind)) return null
        return snapshot.recommendations.habits.filter { it.kind == habit && it.window.start.isAfter(now) }
            .minByOrNull { it.window.start }?.let { PlannedReminder(kind, it.window.start) }
    }
}
