package app.drift.source.phone

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.time.Instant
import java.time.ZoneId

/**
 * Entry point for :app. Call [start] from Application.onCreate: it registers the Sleep API (when permitted),
 * listens for charging changes while the process lives, and arms the next sample alarm.
 */
object PhoneTracking {
    @Volatile private var chargingRegistered = false

    fun start(context: Context) {
        val app = context.applicationContext
        val system = AndroidPhoneSystem(app)
        system.registerSleepApi()
        system.isPlugged()?.let { Instant.now().let { now -> PhoneStore(app).addCharging(now, it, now) } }
        PhoneSampler.arm(app, Instant.now(), ZoneId.systemDefault())
        if (!chargingRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            androidx.core.content.ContextCompat.registerReceiver(app, ChargingReceiver(), filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
            chargingRegistered = true
        }
    }
}
