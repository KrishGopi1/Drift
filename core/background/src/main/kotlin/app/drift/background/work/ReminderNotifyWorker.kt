package app.drift.background.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.drift.background.di.BackgroundClock
import app.drift.background.notify.Notifier
import app.drift.model.repository.ReminderKind
import app.drift.model.repository.SettingsRepository
import app.drift.model.repository.SleepRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock

/** Posts one reminder when its alarm window fires, after re-checking that it is still wanted. */
@HiltWorker
class ReminderNotifyWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val sleep: SleepRepository,
    private val notifier: Notifier,
    @BackgroundClock private val clock: Clock,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString(Work.KEY_KIND)?.let { name -> ReminderKind.entries.firstOrNull { it.name == name } }
            ?: return Result.failure()
        val current = settings.observeSettings().first()
        if (current.reminders[kind] != true) return Result.success()
        if (kind == ReminderKind.CONFIRM_SLEEP) {
            // Only nag if the morning card is still waiting for an answer.
            val pending = sleep.observePendingConfirmation().first() ?: return Result.success()
            notifier.postSleepConfirmation(pending, clock.zone, current.use24HourClock)
        } else {
            notifier.postReminder(kind)
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = syncForegroundInfo(applicationContext, 2101)
}
