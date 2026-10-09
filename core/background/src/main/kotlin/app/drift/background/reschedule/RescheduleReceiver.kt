package app.drift.background.reschedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import app.drift.background.BackgroundScheduler
import app.drift.background.BackgroundState
import app.drift.background.schedule.WakeSchedule
import app.drift.background.work.Work
import java.time.Clock
import java.time.LocalTime

/**
 * Restores all work and alarms after boot, a clock or zone change, or an app update, and catches up a
 * missed morning sync when the phone is unlocked.
 */
class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> BackgroundScheduler.from(context).scheduleAll()
            Intent.ACTION_USER_PRESENT -> catchUpMorningSync(context, Clock.systemDefaultZone())
        }
    }

    companion object {
        /** Enqueues the morning sync if the latest scheduled one has passed without completing. Returns whether it did. */
        fun catchUpMorningSync(context: Context, clock: Clock): Boolean {
            val state = BackgroundState(context)
            val wake = state.expectedWakeMinutes?.let { LocalTime.of(it / 60, it % 60) } ?: return false
            if (!WakeSchedule.isMissed(wake, state.lastMorningSync, clock.instant(), clock.zone)) return false
            Work.enqueueMorningSync(context.applicationContext)
            return true
        }

        /**
         * The OS does not deliver USER_PRESENT to manifest receivers on recent Android versions, so :app also
         * registers it for the life of the process. Costs nothing while the process is not running.
         */
        fun registerUnlockListener(context: Context) {
            ContextCompat.registerReceiver(
                context.applicationContext, RescheduleReceiver(), IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }
}
