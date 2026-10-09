package app.drift.background.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.drift.background.di.BackgroundClock
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.repository.AppSettings
import app.drift.model.repository.ReminderKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms inexact alarms (10-minute windows, no exact-alarm permission) for the enabled reminders.
 * Call [reschedule] after each engine run; kinds that are disabled or no longer planned are cancelled.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    @BackgroundClock private val clock: Clock,
) {
    fun reschedule(snapshot: AnalysisSnapshot, settings: AppSettings, profileWake: LocalTime? = null): List<PlannedReminder> {
        val plan = ReminderPlanner.plan(snapshot, settings, clock.instant(), clock.zone, profileWake)
        val planned = plan.associateBy { it.kind }
        for (kind in ReminderKind.entries) {
            val reminder = planned[kind]
            if (reminder == null) {
                alarmManager.cancel(pendingIntent(kind, 0L))
            } else {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    reminder.at.toEpochMilli(),
                    ReminderPlanner.WINDOW.toMillis(),
                    pendingIntent(kind, reminder.at.toEpochMilli()),
                )
            }
        }
        return plan
    }

    fun cancelAll() {
        ReminderKind.entries.forEach { alarmManager.cancel(pendingIntent(it, 0L)) }
    }

    private fun pendingIntent(kind: ReminderKind, at: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ReminderAlarmReceiver.ACTION)
            .putExtra(ReminderAlarmReceiver.EXTRA_KIND, kind.name)
            .putExtra(ReminderAlarmReceiver.EXTRA_AT, at)
        return PendingIntent.getBroadcast(context, REQUEST_BASE + kind.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val REQUEST_BASE = 4100
    }
}
