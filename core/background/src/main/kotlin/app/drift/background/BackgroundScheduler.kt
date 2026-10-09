package app.drift.background

import android.app.AlarmManager
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.drift.background.notify.NotificationChannels
import app.drift.background.reminders.ReminderScheduler
import app.drift.background.schedule.MorningScheduler
import app.drift.background.work.DayRefreshWorker
import app.drift.background.work.LocalBackupWorker
import app.drift.background.work.Work
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Public facade for :app. Call [scheduleAll] at startup and after onboarding; [cancelAll] on reset.
 * It only enqueues work and arms alarms, so it is safe to call from the main thread.
 */
@Singleton
class BackgroundScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val reminders: ReminderScheduler,
    private val morning: MorningScheduler,
) {
    /** Idempotent: periodic jobs keep their phase, and a restore job re-arms alarms from the stored snapshot. */
    fun scheduleAll() {
        NotificationChannels.ensure(context)
        workManager.enqueueUniquePeriodicWork(
            Work.DAY_REFRESH,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DayRefreshWorker>(DAY_REFRESH_INTERVAL)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            Work.LOCAL_BACKUP,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<LocalBackupWorker>(BACKUP_INTERVAL)
                .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresDeviceIdle(true).build())
                .build(),
        )
        Work.enqueueRestore(context)
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(Work.DAY_REFRESH)
        workManager.cancelUniqueWork(Work.LOCAL_BACKUP)
        workManager.cancelUniqueWork(Work.RESTORE)
        workManager.cancelUniqueWork(Work.MORNING_SYNC)
        workManager.cancelUniqueWork(Work.CONFIRM)
        reminders.cancelAll()
        morning.cancel()
    }

    companion object {
        val DAY_REFRESH_INTERVAL: Duration = Duration.ofHours(3)
        val BACKUP_INTERVAL: Duration = Duration.ofDays(1)

        /** Builds the facade without Hilt; used by receivers that run before the graph is needed. */
        fun from(context: Context, clock: Clock = Clock.systemDefaultZone()): BackgroundScheduler {
            val app = context.applicationContext
            val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return BackgroundScheduler(app, WorkManager.getInstance(app), ReminderScheduler(app, alarms, clock), MorningScheduler(app, alarms, BackgroundState(app), clock))
        }
    }
}
