package app.drift.background.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.drift.background.di.BackgroundClock
import app.drift.background.notify.NotificationChannels
import app.drift.background.reminders.ReminderScheduler
import app.drift.background.schedule.MorningScheduler
import app.drift.background.schedule.WakeSchedule
import app.drift.background.widget.WidgetRefresher
import app.drift.model.repository.AnalysisRepository
import app.drift.model.repository.ProfileRepository
import app.drift.model.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Clock

/** Re-arms the morning alarms and reminders from the last stored snapshot (after boot, time change, update). */
@HiltWorker
class RestoreWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val analysis: AnalysisRepository,
    private val settings: SettingsRepository,
    private val profile: ProfileRepository,
    private val reminders: ReminderScheduler,
    private val morning: MorningScheduler,
    private val widget: WidgetRefresher,
    @BackgroundClock private val clock: Clock,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        NotificationChannels.ensure(applicationContext)
        val snapshot = analysis.observeLatest().first()
        val profileWake = profile.observeProfile().first()?.schedule?.wakeTime
        morning.schedule(WakeSchedule.expectedWake(snapshot, profileWake, clock.zone))
        if (snapshot != null) reminders.reschedule(snapshot, settings.observeSettings().first(), profileWake)
        widget.update()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
