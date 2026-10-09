package app.drift.background.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.drift.background.notify.Notifier
import app.drift.background.widget.WidgetRefresher
import app.drift.model.EpisodeId
import app.drift.model.repository.AnalysisRepository
import app.drift.model.repository.SleepRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/** The notification's Confirm button: mark the episode confirmed, dismiss the card, recompute. */
@HiltWorker
class ConfirmSleepWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sleep: SleepRepository,
    private val analysis: AnalysisRepository,
    private val notifier: Notifier,
    private val widget: WidgetRefresher,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(Work.KEY_EPISODE) ?: return Result.failure()
        return try {
            sleep.confirmEpisode(EpisodeId(id))
            notifier.cancelConfirmation()
            analysis.refresh()
            widget.update()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = syncForegroundInfo(applicationContext, 2102)
}
