package app.drift.background.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import app.drift.model.EpisodeId
import app.drift.model.repository.ReminderKind
import java.time.Duration

/** Unique work names and the enqueue helpers shared by receivers and the scheduler facade. */
object Work {
    const val MORNING_SYNC = "drift.morning-sync"
    const val DAY_REFRESH = "drift.day-refresh"
    const val LOCAL_BACKUP = "drift.local-backup"
    const val RESTORE = "drift.restore"
    const val CONFIRM = "drift.confirm-sleep"
    const val REMINDER_PREFIX = "drift.reminder."

    const val KEY_FALLBACK = "fallback"
    const val KEY_KIND = "kind"
    const val KEY_EPISODE = "episode"

    private fun expedited(builder: OneTimeWorkRequest.Builder) =
        builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(1))

    fun enqueueMorningSync(context: Context, fallback: Boolean = false) {
        val request = expedited(OneTimeWorkRequestBuilder<MorningSyncWorker>())
            .setInputData(Data.Builder().putBoolean(KEY_FALLBACK, fallback).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MORNING_SYNC, ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueReminder(context: Context, kind: ReminderKind) {
        val request = expedited(OneTimeWorkRequestBuilder<ReminderNotifyWorker>())
            .setInputData(Data.Builder().putString(KEY_KIND, kind.name).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(REMINDER_PREFIX + kind.name, ExistingWorkPolicy.REPLACE, request)
    }

    fun enqueueConfirm(context: Context, episode: EpisodeId) {
        val request = expedited(OneTimeWorkRequestBuilder<ConfirmSleepWorker>())
            .setInputData(Data.Builder().putString(KEY_EPISODE, episode.value).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(CONFIRM, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun enqueueRestore(context: Context) {
        val request = OneTimeWorkRequestBuilder<RestoreWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(RESTORE, ExistingWorkPolicy.REPLACE, request)
    }
}
