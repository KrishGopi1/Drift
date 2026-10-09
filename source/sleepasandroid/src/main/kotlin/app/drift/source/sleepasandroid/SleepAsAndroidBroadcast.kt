package app.drift.source.sleepasandroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Optional

enum class SleepAsAndroidEvent { TRACKING_STOPPED, ALARM_DISMISSED }

/** Bound by `:app`: typically enqueues a sync (`SyncReason.SOURCE_BROADCAST`) on WorkManager. Must return quickly. */
fun interface SleepAsAndroidSyncCallback {
    fun onSleepAsAndroidEvent(event: SleepAsAndroidEvent)
}

object SleepAsAndroidActions {
    /**
     * Sleep as Android's documented "sleep tracking stopped" event. NOTE: the action strings come from the
     * public Tasker/automation event list as reproduced by third parties; the vendor's developer pages
     * could not be fetched when this was written, so treat the exact spelling as unverified (see docs/issues).
     */
    const val TRACKING_STOPPED = "com.urbandroid.sleep.alarmclock.SLEEP_TRACKING_STOPPED"
    const val ALARM_DISMISSED = "com.urbandroid.sleep.alarmclock.ALARM_ALERT_DISMISS"

    val all: List<String> = listOf(TRACKING_STOPPED, ALARM_DISMISSED)

    fun eventFor(action: String?): SleepAsAndroidEvent? = when (action) {
        TRACKING_STOPPED -> SleepAsAndroidEvent.TRACKING_STOPPED
        ALARM_DISMISSED -> SleepAsAndroidEvent.ALARM_DISMISSED
        else -> null
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SleepAsAndroidEntryPoint {
    fun callback(): Optional<SleepAsAndroidSyncCallback>
}

/** Makes the callback optional so the graph builds even before `:app` binds one. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SleepAsAndroidModule {
    @BindsOptionalOf abstract fun callback(): SleepAsAndroidSyncCallback
}

/**
 * Receives Sleep as Android's tracking-finished broadcast and asks the app to sync.
 *
 * Registered in the manifest, and also registrable at runtime via [register]: Android 8+ does not deliver
 * most implicit broadcasts to manifest receivers, so `:app` should call [register] while its process is alive.
 */
class SleepAsAndroidBroadcastReceiver : BroadcastReceiver() {

    /** Seam for tests; production resolves the callback through Hilt. */
    internal var callbackResolver: (Context) -> SleepAsAndroidSyncCallback? = { context ->
        runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, SleepAsAndroidEntryPoint::class.java)
                .callback().orElse(null)
        }.getOrNull()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val event = SleepAsAndroidActions.eventFor(intent.action) ?: return
        val callback = callbackResolver(context) ?: return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - lastDelivered[event.ordinal] < DEBOUNCE_MS) return
            lastDelivered[event.ordinal] = now
        }
        callback.onSleepAsAndroidEvent(event)
    }

    companion object {
        private const val DEBOUNCE_MS = 15_000L
        private val lock = Any()
        private val lastDelivered = LongArray(SleepAsAndroidEvent.entries.size)

        internal fun resetDebounceForTests() = synchronized(lock) { lastDelivered.fill(0L) }

        /** Runtime registration; returns the receiver so the caller can unregister it. */
        fun register(context: Context): SleepAsAndroidBroadcastReceiver {
            val receiver = SleepAsAndroidBroadcastReceiver()
            val filter = IntentFilter().apply { SleepAsAndroidActions.all.forEach(::addAction) }
            if (Build.VERSION.SDK_INT >= 33) {
                context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(receiver, filter)
            }
            return receiver
        }
    }
}
