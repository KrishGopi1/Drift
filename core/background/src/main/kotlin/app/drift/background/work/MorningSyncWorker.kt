package app.drift.background.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.drift.background.BackgroundState
import app.drift.background.di.BackgroundClock
import app.drift.background.notify.Notifier
import app.drift.background.reminders.ReminderScheduler
import app.drift.background.schedule.MorningScheduler
import app.drift.background.schedule.WakeSchedule
import app.drift.background.widget.WidgetRefresher
import app.drift.model.repository.AnalysisRepository
import app.drift.model.repository.ProfileRepository
import app.drift.model.repository.ReminderKind
import app.drift.model.repository.SettingsRepository
import app.drift.model.repository.SleepRepository
import app.drift.model.repository.SourceRepository
import app.drift.model.repository.SyncReason
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Clock

/**
 * Expedited morning job: sync sources, recompute, offer the "confirm your sleep" card, refresh the widget,
 * re-arm reminders and tomorrow's alarm.
 */
@HiltWorker
class MorningSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sources: SourceRepository,
    private val analysis: AnalysisRepository,
    private val sleep: SleepRepository,
    private val settings: SettingsRepository,
    private val profile: ProfileRepository,
    private val notifier: Notifier,
    private val widget: WidgetRefresher,
    private val reminders: ReminderScheduler,
    private val morning: MorningScheduler,
    private val state: BackgroundState,
    @BackgroundClock private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = clock.instant()
        val fallback = inputData.getBoolean(Work.KEY_FALLBACK, false)
        val profileWake = profile.observeProfile().first()?.schedule?.wakeTime
        if (fallback) {
            // The late fallback only matters if the primary sync never completed.
            val wake = state.expectedWakeMinutes?.let { java.time.LocalTime.of(it / 60, it % 60) } ?: profileWake ?: WakeSchedule.DEFAULT_WAKE
            val primary = WakeSchedule.lastTrigger(wake, WakeSchedule.SYNC_DELAY, now, clock.zone)
            val last = state.lastMorningSync
            if (last != null && !last.isBefore(primary)) return Result.success()
        }

        var syncFailed = false
        try {
            sources.sync(SyncReason.MORNING)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            syncFailed = true
        }

        val snapshot = try {
            analysis.refresh(now)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
        val current = settings.observeSettings().first()

        val pending = sleep.observePendingConfirmation().first()
        if (pending != null && current.reminders[ReminderKind.CONFIRM_SLEEP] == true) {
            val key = "${pending.id.value}:${pending.start.toEpochMilli()}:${pending.end.toEpochMilli()}"
            if (state.lastConfirmNotified != key && notifier.postSleepConfirmation(pending, clock.zone, current.use24HourClock)) {
                state.lastConfirmNotified = key
            }
        }

        reminders.reschedule(snapshot, current, profileWake)
        morning.schedule(WakeSchedule.expectedWake(snapshot, profileWake, clock.zone))
        widget.update()

        if (syncFailed && runAttemptCount < MAX_ATTEMPTS) return Result.retry()
        if (!syncFailed) state.lastMorningSync = now
        return Result.success()
    }

    /** Only used by WorkManager for expedited work on Android 11 and older. */
    override suspend fun getForegroundInfo(): ForegroundInfo = syncForegroundInfo(applicationContext, FOREGROUND_ID)

    companion object {
        const val MAX_ATTEMPTS = 3
        private const val FOREGROUND_ID = 2100
    }
}
