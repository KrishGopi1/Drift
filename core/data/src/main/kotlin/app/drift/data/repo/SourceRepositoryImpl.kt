package app.drift.data.repo

import androidx.room.withTransaction
import app.drift.data.db.DriftDatabase
import app.drift.data.db.SourceStateEntity
import app.drift.data.db.toEntity
import app.drift.data.db.toEpisode
import app.drift.data.db.toRawEntity
import app.drift.data.merge.episodeKey
import app.drift.model.Checkpoint
import app.drift.model.SourceId
import app.drift.model.source.PullResult
import app.drift.model.source.SourceAdapter
import app.drift.model.source.SourceStatus
import app.drift.model.repository.ImportReport
import app.drift.model.repository.SourceFix
import app.drift.model.repository.SourceInfo
import app.drift.model.repository.SourceRepository
import app.drift.model.repository.SyncReason
import app.drift.model.repository.SyncReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceRepositoryImpl @Inject constructor(
    private val db: DriftDatabase,
    private val adapters: Set<@JvmSuppressWildcards SourceAdapter>,
    private val importers: Set<@JvmSuppressWildcards BackupImporter>,
    private val clock: Clock,
) : SourceRepository {
    private val progress = MutableStateFlow<Float?>(null)
    private val syncLock = Mutex()

    override fun observeSources(): Flow<List<SourceInfo>> = db.sourceState().observeAll().map { states ->
        val byId = states.associateBy { it.sourceId }
        adapters.sortedBy { it.id }.map { adapter ->
            val state = byId[adapter.id.value]
            val status = safeStatus(adapter)
            SourceInfo(
                id = adapter.id,
                displayName = displayName(adapter.id),
                status = status,
                enabled = state?.enabled ?: true,
                lastSync = state?.lastSyncMs?.let(Instant::ofEpochMilli),
                lastError = state?.lastError,
                fix = fixFor(adapter.id, status),
            )
        }
    }

    override fun observeSyncProgress(): Flow<Float?> = progress

    override suspend fun sync(reason: SyncReason): SyncReport = syncLock.withLock {
        val startedAt = clock.instant()
        var newEpisodes = 0
        var updatedEpisodes = 0
        var newSignals = 0
        val errors = LinkedHashMap<SourceId, String>()
        val states = db.sourceState().all().associateBy { it.sourceId }
        val active = adapters.sortedBy { it.id }.filter { states[it.id.value]?.enabled ?: true }
        progress.value = 0f
        try {
            active.forEachIndexed { index, adapter ->
                val prior = states[adapter.id.value]
                try {
                    val status = adapter.status()
                    if (status != SourceStatus.Available) {
                        saveState(adapter.id, prior, lastError = (status as? SourceStatus.Error)?.reason, synced = false)
                        (status as? SourceStatus.Error)?.let { errors[adapter.id] = it.reason }
                    } else {
                        val result = adapter.pull(prior?.checkpoint?.let(::Checkpoint))
                        val applied = apply(adapter.id, result, startedAt)
                        newEpisodes += applied.newEpisodes
                        updatedEpisodes += applied.updatedEpisodes
                        newSignals += applied.newSignals
                        saveState(adapter.id, prior, checkpoint = result.next?.token, lastError = null, synced = true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val message = e.message ?: e.javaClass.simpleName
                    errors[adapter.id] = message
                    saveState(adapter.id, prior, lastError = message, synced = false)
                }
                progress.value = (index + 1f) / active.size
            }
            db.signals().deleteBefore(startedAt.minus(AnalysisRepositoryImpl.HISTORY).toEpochMilli())
        } finally {
            progress.value = null
        }
        SyncReport(startedAt, clock.instant(), newEpisodes, updatedEpisodes, newSignals, errors)
    }

    override suspend fun setEnabled(id: SourceId, enabled: Boolean) {
        val prior = db.sourceState().get(id.value)
        db.sourceState().upsert(
            prior?.copy(enabled = enabled) ?: SourceStateEntity(id.value, enabled, null, null, null),
        )
    }

    override suspend fun importSleepAsAndroidBackup(input: InputStream): ImportReport {
        val importer = importers.firstOrNull { it.sourceId == SourceId.SLEEP_AS_ANDROID }
            ?: return ImportReport(0, 0, 0, null, null, listOf("Sleep as Android import is not available in this build."))
        val batch = try {
            importer.parse(input)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ImportReport(0, 0, 0, null, null, listOf("The file could not be read: ${e.message ?: "unrecognised format"}"))
        }
        val now = clock.instant()
        var imported = 0
        var duplicates = 0
        val storedDays = ArrayList<LocalDate>()
        db.withTransaction {
            for (ep in batch.episodes) {
                val key = episodeKey(ep.origin)
                if (db.sourceEpisodes().get(key) != null) { duplicates++; continue }
                val overlapping = db.sourceEpisodes()
                    .overlapping(ep.start.toEpochMilli(), ep.end.toEpochMilli())
                    .map { it.toEpisode() }
                    .filter { it.origin.sourceId != ep.origin.sourceId && overlapFraction(it, ep) >= DUPLICATE_OVERLAP }
                val bringsStages = ep.stages != null && overlapping.all { it.stages == null }
                if (overlapping.isNotEmpty() && !bringsStages) { duplicates++; continue }
                db.sourceEpisodes().upsert(listOf(ep.toRawEntity(now)))
                imported++
                storedDays += ep.end.atZone(ep.zone).toLocalDate()
            }
            if (batch.signals.isNotEmpty()) db.signals().upsert(batch.signals.map { it.toEntity() })
        }
        return ImportReport(imported, duplicates, batch.rowsRejected, storedDays.minOrNull(), storedDays.maxOrNull(), batch.warnings)
    }

    // ---- internals -------------------------------------------------------------------------------

    private data class Applied(val newEpisodes: Int, val updatedEpisodes: Int, val newSignals: Int)

    private suspend fun apply(id: SourceId, result: PullResult, now: Instant): Applied = db.withTransaction {
        if (result.fullResync) {
            val earliest = (result.episodes.map { it.start.toEpochMilli() } + result.signals.map { it.toEntity().startMs }).minOrNull()
            val from = minOf(earliest ?: Long.MAX_VALUE, now.minus(RESYNC_WINDOW).toEpochMilli())
            db.sourceEpisodes().deleteFrom(id.value, from)
            db.signals().deleteFrom(id.value, from)
        }
        if (result.deletedRecordIds.isNotEmpty()) {
            result.deletedRecordIds.chunked(500).forEach {
                db.sourceEpisodes().deleteRecords(id.value, it)
                db.signals().deleteRecords(id.value, it)
            }
        }
        var created = 0
        var updated = 0
        val rows = result.episodes.map { ep ->
            // Adapters stamp their own id on origin; a mismatched record is stored under the adapter's id.
            val fixed = if (ep.origin.sourceId == id) ep else ep.copy(origin = ep.origin.copy(sourceId = id))
            if (db.sourceEpisodes().get(episodeKey(fixed.origin)) == null) created++ else updated++
            fixed.toRawEntity(now)
        }
        if (rows.isNotEmpty()) db.sourceEpisodes().upsert(rows)
        val before = db.signals().count()
        result.signals.chunked(1000).forEach { chunk -> db.signals().upsert(chunk.map { it.toEntity() }) }
        Applied(created, updated, (db.signals().count() - before).coerceAtLeast(0))
    }

    private suspend fun saveState(id: SourceId, prior: SourceStateEntity?, checkpoint: String? = prior?.checkpoint, lastError: String?, synced: Boolean) {
        db.sourceState().upsert(
            SourceStateEntity(
                sourceId = id.value,
                enabled = prior?.enabled ?: true,
                checkpoint = checkpoint,
                lastSyncMs = if (synced) clock.millis() else prior?.lastSyncMs,
                lastError = lastError,
            ),
        )
    }

    private suspend fun safeStatus(adapter: SourceAdapter): SourceStatus = try {
        adapter.status()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SourceStatus.Error(e.message ?: e.javaClass.simpleName)
    }

    private fun overlapFraction(a: app.drift.model.SleepEpisode, b: app.drift.model.SleepEpisode): Double {
        val common = a.interval.intersect(b.interval)?.duration?.toMillis() ?: return 0.0
        val shorter = minOf(a.interval.duration.toMillis(), b.interval.duration.toMillis()).coerceAtLeast(1)
        return common.toDouble() / shorter
    }

    private companion object {
        val RESYNC_WINDOW: Duration = Duration.ofDays(30)
        const val DUPLICATE_OVERLAP = 0.5

        fun displayName(id: SourceId): String = when (id) {
            SourceId.HEALTH_CONNECT -> "Health Connect"
            SourceId.SLEEP_AS_ANDROID -> "Sleep as Android"
            SourceId.PHONE -> "This phone"
            else -> id.value.replaceFirstChar { it.uppercase() }
        }

        fun fixFor(id: SourceId, status: SourceStatus): SourceFix? = when (status) {
            SourceStatus.Available -> null
            is SourceStatus.NeedsPermission -> when {
                id == SourceId.HEALTH_CONNECT -> SourceFix.GrantHealthConnectPermissions(status.missing)
                status.missing.any { it.endsWith("PACKAGE_USAGE_STATS") } -> SourceFix.GrantUsageAccess
                else -> SourceFix.GrantRuntimePermissions(status.missing)
            }
            SourceStatus.NotInstalled -> when (id) {
                SourceId.HEALTH_CONNECT -> SourceFix.InstallHealthConnect
                SourceId.SLEEP_AS_ANDROID -> SourceFix.ImportSleepAsAndroidBackup
                else -> SourceFix.OpenAppSettings
            }
            SourceStatus.NotSupported -> if (id == SourceId.HEALTH_CONNECT) SourceFix.UpdateHealthConnect else SourceFix.OpenAppSettings
            is SourceStatus.Error -> SourceFix.OpenAppSettings
        }
    }
}

