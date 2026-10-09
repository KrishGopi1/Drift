package app.drift.background.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import app.drift.background.R
import app.drift.background.notify.NotificationChannels

/** The notice WorkManager shows for expedited jobs on Android 11 and older (newer versions never ask for it). */
internal fun syncForegroundInfo(context: Context, id: Int): ForegroundInfo {
    NotificationChannels.ensure(context)
    val notification = NotificationCompat.Builder(context, NotificationChannels.SYNC)
        .setSmallIcon(R.drawable.ic_stat_drift)
        .setContentTitle(context.getString(R.string.sync_running))
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setOngoing(true)
        .build()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(id, notification)
    }
}
