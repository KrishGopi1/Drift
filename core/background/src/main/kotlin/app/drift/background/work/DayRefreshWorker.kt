package app.drift.background.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.drift.background.di.BackgroundClock
import app.drift.background.reminders.ReminderScheduler
import app.drift.background.widget.WidgetRefresher
import app.drift.model.repository.AnalysisRepository
import app.drift.model.repository.ProfileRepository
import app.drift.model.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Clock

/** Every 3 hours while the battery is not low: recompute the curve, re-arm reminders, refresh the widget. */
@HiltWorker
class DayRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val analysis: AnalysisRepository,
    private val settings: SettingsRepository,
    private val profile: ProfileRepository,
    private val reminders: ReminderScheduler,
    private val widget: WidgetRefresher,
    @BackgroundClock private val clock: Clock,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val snapshot = analysis.refresh(clock.instant())
        reminders.reschedule(snapshot, settings.observeSettings().first(), profile.observeProfile().first()?.schedule?.wakeTime)
        widget.update()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // The next 3-hour run tries again, so give up quietly after a few attempts.
        if (runAttemptCount < 3) Result.retry() else Result.success()
    }
}
