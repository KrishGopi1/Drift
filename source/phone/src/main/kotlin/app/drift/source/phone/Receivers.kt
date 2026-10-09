package app.drift.source.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.google.android.gms.location.SleepClassifyEvent
import com.google.android.gms.location.SleepSegmentEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Receives Sleep API segments and classification samples and stores them for the nightly reconstruction. */
class SleepApiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val store = PhoneStore(context)
        val now = Instant.now()
        if (SleepSegmentEvent.hasEvents(intent)) {
            store.addSegments(
                SleepSegmentEvent.extractEvents(intent).map {
                    SleepSegment(Instant.ofEpochMilli(it.startTimeMillis), Instant.ofEpochMilli(it.endTimeMillis), it.status)
                },
                now,
            )
        } else if (SleepClassifyEvent.hasEvents(intent)) {
            store.addClassify(
                SleepClassifyEvent.extractEvents(intent).map {
                    ClassifySample(Instant.ofEpochMilli(it.timestampMillis), it.confidence, it.motion, it.light)
                },
                now,
            )
        }
    }

    companion object {
        internal const val ACTION = "app.drift.source.phone.SLEEP_API_UPDATE"
    }
}

/** Records plug/unplug while the app process is alive (the OS does not deliver these to the manifest). */
class ChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val plugged = when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> true
            Intent.ACTION_POWER_DISCONNECTED -> false
            else -> intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1).takeIf { it >= 0 }?.let { it != 0 } ?: return
        }
        val now = Instant.now()
        PhoneStore(context).addCharging(now, plugged, now)
    }
}

/** Re-arms the sampler and the Sleep API registration after boot/update, and runs the scheduled samples. */
class PhoneMaintenanceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                val app = context.applicationContext
                val system = AndroidPhoneSystem(app)
                val now = Instant.now()
                if (intent.action == ACTION_SAMPLE) PhoneSampler(system, PhoneStore(app)).sample(now)
                system.registerSleepApi()
                PhoneSampler.arm(app, now, ZoneId.systemDefault())
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        internal const val ACTION_SAMPLE = "app.drift.source.phone.SAMPLE"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
