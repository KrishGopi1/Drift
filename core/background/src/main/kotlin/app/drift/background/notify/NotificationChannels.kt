package app.drift.background.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import app.drift.background.R

object NotificationChannels {
    const val REMINDERS = "drift_reminders"
    const val SLEEP_CONFIRMATION = "drift_sleep_confirmation"
    /** Quiet channel for WorkManager's expedited-job notice on Android 11 and older. */
    const val SYNC = "drift_sync"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_reminders_description) },
        )
        nm.createNotificationChannel(
            NotificationChannel(SLEEP_CONFIRMATION, context.getString(R.string.channel_confirmation), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_confirmation_description) },
        )
        nm.createNotificationChannel(
            NotificationChannel(SYNC, context.getString(R.string.channel_sync), NotificationManager.IMPORTANCE_MIN),
        )
    }
}
