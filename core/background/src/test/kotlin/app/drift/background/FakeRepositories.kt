package app.drift.background

import app.drift.model.DayAnnotation
import app.drift.model.EpisodeId
import app.drift.model.Interval
import app.drift.model.SleepEpisode
import app.drift.model.SourceId
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.UserProfile
import app.drift.model.repository.AnalysisRepository
import app.drift.model.repository.AppSettings
import app.drift.model.repository.BackupRepository
import app.drift.model.repository.ImportReport
import app.drift.model.repository.LocalBackup
import app.drift.model.repository.ProfileRepository
import app.drift.model.repository.SettingsRepository
import app.drift.model.repository.SleepRepository
import app.drift.model.repository.SourceInfo
import app.drift.model.repository.SourceRepository
import app.drift.model.repository.SyncReason
import app.drift.model.repository.SyncReport
import app.drift.background.widget.WidgetRefresher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate

class FakeSleepRepository : SleepRepository {
    val pending = MutableStateFlow<SleepEpisode?>(null)
    val confirmed = mutableListOf<EpisodeId>()
    var confirmFails = false
    override fun observeEpisodes(range: Interval): Flow<List<SleepEpisode>> = flowOf(emptyList())
    override fun observeEpisode(id: EpisodeId): Flow<SleepEpisode?> = flowOf(null)
    override fun observeSourceEpisodes(range: Interval): Flow<List<SleepEpisode>> = flowOf(emptyList())
    override fun observePendingConfirmation(): Flow<SleepEpisode?> = pending
    override suspend fun saveUserEpisode(episode: SleepEpisode) = Unit
    override suspend fun confirmEpisode(id: EpisodeId) {
        if (confirmFails) error("db locked")
        confirmed += id
        pending.value = null
    }
    override suspend fun deleteEpisode(id: EpisodeId) = Unit
    override fun observeAnnotations(from: LocalDate, to: LocalDate): Flow<List<DayAnnotation>> = flowOf(emptyList())
    override suspend fun setAnnotation(annotation: DayAnnotation) = Unit
}

class FakeAnalysisRepository(var snapshot: AnalysisSnapshot? = Fixtures.snapshot()) : AnalysisRepository {
    var refreshCount = 0
    var refreshFails = false
    override fun observeLatest(): Flow<AnalysisSnapshot?> = flowOf(snapshot)
    override fun observePrevious(): Flow<AnalysisSnapshot?> = flowOf(null)
    override suspend fun refresh(now: Instant): AnalysisSnapshot {
        if (refreshFails) error("engine failed")
        refreshCount++
        return snapshot ?: Fixtures.snapshot(computedAt = now).also { snapshot = it }
    }
}

class FakeSourceRepository : SourceRepository {
    val syncs = mutableListOf<SyncReason>()
    var syncFails = false
    override fun observeSources(): Flow<List<SourceInfo>> = flowOf(emptyList())
    override fun observeSyncProgress(): Flow<Float?> = flowOf(null)
    override suspend fun sync(reason: SyncReason): SyncReport {
        if (syncFails) error("source unavailable")
        syncs += reason
        return SyncReport(Instant.EPOCH, Instant.EPOCH, 0, 0, 0, emptyMap())
    }
    override suspend fun setEnabled(id: SourceId, enabled: Boolean) = Unit
    override suspend fun importSleepAsAndroidBackup(input: InputStream): ImportReport = error("unused")
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val settings = MutableStateFlow(initial)
    override fun observeSettings(): Flow<AppSettings> = settings
    override suspend fun update(transform: (AppSettings) -> AppSettings) { settings.value = transform(settings.value) }
}

class FakeProfileRepository(var profile: UserProfile? = null) : ProfileRepository {
    override fun observeProfile(): Flow<UserProfile?> = flowOf(profile)
    override suspend fun saveProfile(profile: UserProfile) { this.profile = profile }
    override fun observeOnboardingComplete(): Flow<Boolean> = flowOf(true)
    override suspend fun setOnboardingComplete(complete: Boolean) = Unit
}

class FakeBackupRepository : BackupRepository {
    var created = 0
    var fails = false
    override suspend fun exportAll(out: OutputStream) = Unit
    override suspend fun importAll(input: InputStream): ImportReport = error("unused")
    override suspend fun wipeAll() = Unit
    override suspend fun createLocalBackup(): LocalBackup {
        if (fails) error("disk full")
        created++
        return LocalBackup("b$created", Instant.EPOCH, 1)
    }
    override suspend fun listLocalBackups(): List<LocalBackup> = emptyList()
    override suspend fun restoreLocalBackup(id: String) = Unit
}

class FakeWidgetRefresher : WidgetRefresher {
    var updates = 0
    override suspend fun update() { updates++ }
}

