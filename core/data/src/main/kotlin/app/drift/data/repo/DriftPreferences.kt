package app.drift.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.drift.model.repository.AppSettings
import app.drift.model.repository.ReminderKind
import app.drift.model.repository.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** DataStore-backed settings and the onboarding flag. */
@Singleton
class DriftPreferences @Inject constructor(private val store: DataStore<Preferences>) {
    private val theme = stringPreferencesKey("theme")
    private val phoneDetection = booleanPreferencesKey("phone_detection")
    private val clock24 = intPreferencesKey("clock_24h")
    private val onboarding = booleanPreferencesKey("onboarding_complete")
    private fun reminder(kind: ReminderKind) = booleanPreferencesKey("reminder_${kind.name}")

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }.distinctUntilChanged()
    val onboardingComplete: Flow<Boolean> = store.data.map { it[onboarding] ?: false }.distinctUntilChanged()

    suspend fun currentSettings(): AppSettings = settings.first()
    suspend fun isOnboardingComplete(): Boolean = onboardingComplete.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[theme] = next.theme.name
            prefs[phoneDetection] = next.phoneDetectionEnabled
            ReminderKind.entries.forEach { k -> prefs[reminder(k)] = next.reminders[k] ?: defaultReminder(k) }
            val c = next.use24HourClock
            if (c == null) prefs.remove(clock24) else prefs[clock24] = if (c) 1 else 0
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        store.edit { it[onboarding] = complete }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun defaultReminder(k: ReminderKind) = AppSettings().reminders.getValue(k)

    private fun Preferences.toSettings() = AppSettings(
        theme = this[theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        reminders = ReminderKind.entries.associateWith { this[reminder(it)] ?: defaultReminder(it) },
        phoneDetectionEnabled = this[phoneDetection] ?: true,
        use24HourClock = this[clock24]?.let { it == 1 },
    )
}
