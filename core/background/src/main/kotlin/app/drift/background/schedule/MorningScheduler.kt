package app.drift.background.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.drift.background.BackgroundState
import app.drift.background.di.BackgroundClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms the morning sync at expected wake + 20 min, plus a late fallback three hours after wake that only
 * runs if the first one did not complete. Inexact 10-minute windows; no exact-alarm permission.
 */
@Singleton
class MorningScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val state: BackgroundState,
    @BackgroundClock private val clock: Clock,
) {
    /** Arms both alarms for the next morning and remembers the wake time used. Returns the primary trigger. */
    fun schedule(wake: LocalTime): Instant {
        val now = clock.instant()
        val primary = WakeSchedule.nextTrigger(wake, WakeSchedule.SYNC_DELAY, now, clock.zone)
        val fallback = WakeSchedule.nextTrigger(wake, WakeSchedule.FALLBACK_DELAY, now, clock.zone)
        state.expectedWakeMinutes = wake.hour * 60 + wake.minute
        arm(primary, fallbackAlarm = false)
        arm(fallback, fallbackAlarm = true)
        return primary
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent(false))
        alarmManager.cancel(pendingIntent(true))
    }

    private fun arm(at: Instant, fallbackAlarm: Boolean) {
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), WINDOW_MILLIS, pendingIntent(fallbackAlarm))
    }

    private fun pendingIntent(fallbackAlarm: Boolean): PendingIntent {
        val intent = Intent(context, MorningAlarmReceiver::class.java)
            .setAction(MorningAlarmReceiver.ACTION)
            .putExtra(MorningAlarmReceiver.EXTRA_FALLBACK, fallbackAlarm)
        return PendingIntent.getBroadcast(
            context, if (fallbackAlarm) REQUEST_FALLBACK else REQUEST_PRIMARY, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val REQUEST_PRIMARY = 4001
        const val REQUEST_FALLBACK = 4002
        private const val WINDOW_MILLIS = 10 * 60 * 1000L
    }
}
