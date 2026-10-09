package app.drift.background

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Tiny persisted bookkeeping for the background jobs (what has already been synced or notified). */
@Singleton
class BackgroundState @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("drift_background", Context.MODE_PRIVATE)

    var lastMorningSync: Instant?
        get() = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        set(value) { prefs.edit().putLong(KEY_LAST_SYNC, value?.toEpochMilli() ?: 0L).apply() }

    /** Wake time of day (minutes after midnight) used for the last morning schedule. */
    var expectedWakeMinutes: Int?
        get() = prefs.getInt(KEY_WAKE, -1).takeIf { it >= 0 }
        set(value) { prefs.edit().putInt(KEY_WAKE, value ?: -1).apply() }

    /** Key of the last "confirm your sleep" notification, so the same night does not alert twice. */
    var lastConfirmNotified: String?
        get() = prefs.getString(KEY_CONFIRM, null)
        set(value) { prefs.edit().putString(KEY_CONFIRM, value).apply() }

    companion object {
        private const val KEY_LAST_SYNC = "last_morning_sync"
        private const val KEY_WAKE = "expected_wake_minutes"
        private const val KEY_CONFIRM = "last_confirm_notified"
    }
}
