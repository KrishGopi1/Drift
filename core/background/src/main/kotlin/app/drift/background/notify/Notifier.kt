package app.drift.background.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.drift.background.R
import app.drift.model.SleepEpisode
import app.drift.model.repository.ReminderKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Posts and cancels Drift's notifications. Everything is skipped quietly when notifications are not allowed. */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: NotificationManagerCompat,
) {
    /** False when POST_NOTIFICATIONS is missing (Android 13+) or the user turned notifications off. */
    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return manager.areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // guarded by canPost()
    fun postReminder(kind: ReminderKind): Boolean {
        if (!canPost()) return false
        NotificationChannels.ensure(context)
        val text = NotificationContent.reminder(context, kind)
        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_drift)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply { openApp(DESTINATION_ENERGY)?.let(::setContentIntent) }
            .build()
        manager.notify(reminderId(kind), notification)
        return true
    }

    @SuppressLint("MissingPermission") // guarded by canPost()
    fun postSleepConfirmation(episode: SleepEpisode, zone: ZoneId, use24Hour: Boolean?): Boolean {
        if (!canPost()) return false
        NotificationChannels.ensure(context)
        val text = NotificationContent.confirmation(context, episode, zone, use24Hour)
        val confirmIntent = Intent(context, ConfirmActionReceiver::class.java)
            .setAction(ConfirmActionReceiver.ACTION_CONFIRM)
            .putExtra(ConfirmActionReceiver.EXTRA_EPISODE, episode.id.value)
        val confirm = PendingIntent.getBroadcast(context, CONFIRM_REQUEST, confirmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, NotificationChannels.SLEEP_CONFIRMATION)
            .setSmallIcon(R.drawable.ic_stat_drift)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.action_confirm), confirm)
        openApp(DESTINATION_EDIT_SLEEP, episode.id.value)?.let {
            builder.setContentIntent(it)
            builder.addAction(0, context.getString(R.string.action_adjust), it)
        }
        manager.notify(ID_CONFIRMATION, builder.build())
        return true
    }

    fun cancelConfirmation() = manager.cancel(ID_CONFIRMATION)

    /** Launch intent for the app's own package, since this module cannot name :app's activity. */
    private fun openApp(destination: String, episodeId: String? = null): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.putExtra(EXTRA_DESTINATION, destination)
        if (episodeId != null) launch.putExtra(EXTRA_EPISODE_ID, episodeId)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, destination.hashCode() xor (episodeId?.hashCode() ?: 0), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ID_CONFIRMATION = 2001
        private const val CONFIRM_REQUEST = 2002
        fun reminderId(kind: ReminderKind) = 3000 + kind.ordinal

        /** Extras on the launch intent; :app reads them to deep-link. */
        const val EXTRA_DESTINATION = "app.drift.extra.DESTINATION"
        const val EXTRA_EPISODE_ID = "app.drift.extra.EPISODE_ID"
        const val DESTINATION_ENERGY = "energy"
        const val DESTINATION_EDIT_SLEEP = "edit_sleep"
    }
}
