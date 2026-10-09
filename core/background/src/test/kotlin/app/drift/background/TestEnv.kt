package app.drift.background

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import app.drift.background.notify.Notifier
import app.drift.background.reminders.ReminderScheduler
import app.drift.background.schedule.MorningScheduler
import app.drift.background.work.ConfirmSleepWorker
import app.drift.background.work.DayRefreshWorker
import app.drift.background.work.LocalBackupWorker
import app.drift.background.work.MorningSyncWorker
import app.drift.background.work.ReminderNotifyWorker
import app.drift.background.work.RestoreWorker
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant

/** Real notifier, schedulers and Robolectric alarm/notification shadows around in-memory repositories. */
class TestEnv(now: Instant = Fixtures.noon, grantNotifications: Boolean = true) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val clock: Clock = Clock.fixed(now, Fixtures.zone)
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val notificationManager = NotificationManagerCompat.from(context)
    val state = BackgroundState(context).also { it.lastMorningSync = null; it.expectedWakeMinutes = null; it.lastConfirmNotified = null }

    val sleep = FakeSleepRepository()
    val analysis = FakeAnalysisRepository()
    val sources = FakeSourceRepository()
    val settings = FakeSettingsRepository()
    val profile = FakeProfileRepository()
    val backup = FakeBackupRepository()
    val widget = FakeWidgetRefresher()

    val notifier = Notifier(context, notificationManager)
    val reminders = ReminderScheduler(context, alarmManager, clock)
    val morning = MorningScheduler(context, alarmManager, state, clock)

    init {
        if (grantNotifications) shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? = when (workerClassName) {
            MorningSyncWorker::class.java.name -> MorningSyncWorker(appContext, workerParameters, sources, analysis, sleep, settings, profile, notifier, widget, reminders, morning, state, clock)
            DayRefreshWorker::class.java.name -> DayRefreshWorker(appContext, workerParameters, analysis, settings, profile, reminders, widget, clock)
            LocalBackupWorker::class.java.name -> LocalBackupWorker(appContext, workerParameters, backup)
            ReminderNotifyWorker::class.java.name -> ReminderNotifyWorker(appContext, workerParameters, settings, sleep, notifier, clock)
            ConfirmSleepWorker::class.java.name -> ConfirmSleepWorker(appContext, workerParameters, sleep, analysis, notifier, widget)
            RestoreWorker::class.java.name -> RestoreWorker(appContext, workerParameters, analysis, settings, profile, reminders, morning, widget, clock)
            else -> null
        }
    }

    fun posted() = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).allNotifications
    fun scheduled() = shadowOf(alarmManager).scheduledAlarms
}
