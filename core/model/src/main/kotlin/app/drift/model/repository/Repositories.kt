package app.drift.model.repository

import app.drift.model.CheckIn
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeId
import app.drift.model.Interval
import app.drift.model.SleepEpisode
import app.drift.model.SourceId
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.UserProfile
import app.drift.model.source.SourceStatus
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate

/*
 * Repository interfaces. Implemented by :core:data (A2), consumed by :feature:* (A5) and
 * :core:background (A3). Fakes live in :testing (A6). Features never see source modules.
 */

/** Merged canonical timeline (plan 3, C3 merge rules). */
interface SleepRepository {
    fun observeEpisodes(range: Interval): Flow<List<SleepEpisode>>
    fun observeEpisode(id: EpisodeId): Flow<SleepEpisode?>
    /** Raw per-source episodes overlapping [range], before merge (for the night-detail conflict view). */
    fun observeSourceEpisodes(range: Interval): Flow<List<SleepEpisode>>
    /** Most recent phone-inferred/unconfirmed main episode awaiting the morning "confirm your sleep" card. */
    fun observePendingConfirmation(): Flow<SleepEpisode?>
    /** Saves a user edit (or a brand-new manual episode/nap). Result is USER_ENTERED and wins merges. */
    suspend fun saveUserEpisode(episode: SleepEpisode)
    suspend fun confirmEpisode(id: EpisodeId)
    suspend fun deleteEpisode(id: EpisodeId)
    fun observeAnnotations(from: LocalDate, to: LocalDate): Flow<List<DayAnnotation>>
    suspend fun setAnnotation(annotation: DayAnnotation)
}

/** Runs the engine over the cached timeline and stores snapshots (with engine version + input hash). */
interface AnalysisRepository {
    fun observeLatest(): Flow<AnalysisSnapshot?>
    /** Snapshot before the latest one, for "why this changed". */
    fun observePrevious(): Flow<AnalysisSnapshot?>
    /** Recompute now (under 50 ms engine time) and persist. */
    suspend fun refresh(now: Instant = Instant.now()): AnalysisSnapshot
}

data class SourceInfo(
    val id: SourceId,
    val displayName: String,
    val status: SourceStatus,
    val enabled: Boolean,
    val lastSync: Instant?,
    val lastError: String?,
    /** What the user can do to fix a non-available status. */
    val fix: SourceFix?,
)

/** User-actionable fixes. :app maps each to an Android intent / permission request. */
sealed interface SourceFix {
    data object InstallHealthConnect : SourceFix
    data object UpdateHealthConnect : SourceFix
    data class GrantHealthConnectPermissions(val permissions: Set<String>) : SourceFix
    data object GrantUsageAccess : SourceFix
    data class GrantRuntimePermissions(val permissions: Set<String>) : SourceFix
    data object ImportSleepAsAndroidBackup : SourceFix
    data object OpenAppSettings : SourceFix
}

enum class SyncReason { FIRST_RUN, APP_OPEN, MORNING, PERIODIC, USER_PULL, SOURCE_BROADCAST }

data class SyncReport(
    val startedAt: Instant,
    val finishedAt: Instant,
    val newEpisodes: Int,
    val updatedEpisodes: Int,
    val newSignals: Int,
    val errors: Map<SourceId, String>,
)

data class ImportReport(
    val nightsImported: Int,
    val duplicatesSkipped: Int,
    val rowsRejected: Int,
    val earliest: LocalDate?,
    val latest: LocalDate?,
    val warnings: List<String>,
)

interface SourceRepository {
    fun observeSources(): Flow<List<SourceInfo>>
    /** Progress of a running sync (0..1) or null when idle; drives onboarding's import progress. */
    fun observeSyncProgress(): Flow<Float?>
    suspend fun sync(reason: SyncReason): SyncReport
    suspend fun setEnabled(id: SourceId, enabled: Boolean)
    suspend fun importSleepAsAndroidBackup(input: InputStream): ImportReport
}

interface ProfileRepository {
    fun observeProfile(): Flow<UserProfile?>
    suspend fun saveProfile(profile: UserProfile)
    fun observeOnboardingComplete(): Flow<Boolean>
    suspend fun setOnboardingComplete(complete: Boolean)
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ReminderKind { WIND_DOWN, MELATONIN_WINDOW, CAFFEINE_CUTOFF, MORNING_LIGHT, CONFIRM_SLEEP, ENERGY_CHECK_IN }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val reminders: Map<ReminderKind, Boolean> = ReminderKind.entries.associateWith { it != ReminderKind.ENERGY_CHECK_IN },
    val phoneDetectionEnabled: Boolean = true,
    val use24HourClock: Boolean? = null,
)

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface CheckInRepository {
    fun observeCheckIns(since: Instant): Flow<List<CheckIn>>
    suspend fun add(checkIn: CheckIn)
}

data class LocalBackup(val id: String, val createdAt: Instant, val sizeBytes: Long)

/** Export/import (plan 9.3: round-trips all data exactly), wipe, and the rolling encrypted local backups. */
interface BackupRepository {
    suspend fun exportAll(out: OutputStream)
    suspend fun importAll(input: InputStream): ImportReport
    suspend fun wipeAll()
    suspend fun createLocalBackup(): LocalBackup
    suspend fun listLocalBackups(): List<LocalBackup>
    suspend fun restoreLocalBackup(id: String)
}
