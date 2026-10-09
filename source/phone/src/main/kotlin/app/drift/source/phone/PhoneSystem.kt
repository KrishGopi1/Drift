package app.drift.source.phone

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.SleepSegmentRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import kotlin.coroutines.resume

/** Everything the adapter reads from the OS, behind one seam so it can be faked. */
internal interface PhoneSystem {
    fun hasUsageAccess(): Boolean
    fun hasActivityRecognition(): Boolean
    fun screenChanges(from: Instant, to: Instant): List<ScreenChange>
    fun nextAlarmAt(): Instant?
    fun isPlugged(): Boolean?
    /** Cumulative step counter plus a key that changes on reboot, or null when unavailable. */
    suspend fun stepCounter(): Pair<Long, Long>?
    fun registerSleepApi()
}

internal class AndroidPhoneSystem(context: Context) : PhoneSystem {
    private val app = context.applicationContext

    override fun hasUsageAccess(): Boolean {
        val ops = app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun hasActivityRecognition(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    override fun screenChanges(from: Instant, to: Instant): List<ScreenChange> {
        if (!hasUsageAccess()) return emptyList()
        val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = try {
            usm.queryEvents(from.toEpochMilli(), to.toEpochMilli())
        } catch (_: SecurityException) {
            return emptyList()
        }
        val screen = mutableListOf<ScreenChange>()
        val keyguard = mutableListOf<ScreenChange>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val at = Instant.ofEpochMilli(e.timeStamp)
            when (e.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> screen += ScreenChange(at, true)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> screen += ScreenChange(at, false)
                UsageEvents.Event.KEYGUARD_HIDDEN -> keyguard += ScreenChange(at, true)
                UsageEvents.Event.KEYGUARD_SHOWN -> keyguard += ScreenChange(at, false)
            }
        }
        return screen.ifEmpty { keyguard }
    }

    override fun nextAlarmAt(): Instant? {
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.nextAlarmClock?.triggerTime?.let { Instant.ofEpochMilli(it) }
    }

    override fun isPlugged(): Boolean? {
        val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        return battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    override suspend fun stepCounter(): Pair<Long, Long>? {
        if (!hasActivityRecognition()) return null
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        // One-shot read: registering delivers the latest value immediately, then we unregister.
        val total = withTimeoutOrNull(3_000) {
            suspendCancellableCoroutine<Long> { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values[0].toLong())
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } ?: return null
        val bootKey = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000L
        return total to bootKey
    }

    override fun registerSleepApi() {
        if (!hasActivityRecognition()) return
        val intent = Intent(app, SleepApiReceiver::class.java).setAction(SleepApiReceiver.ACTION)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val pending = PendingIntent.getBroadcast(app, 0, intent, flags)
        try {
            ActivityRecognition.getClient(app)
                .requestSleepSegmentUpdates(pending, SleepSegmentRequest.getDefaultSleepSegmentRequest())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; status() reports it.
        }
    }
}
