package app.drift.data.repo

import androidx.room.withTransaction
import app.drift.data.codec.AnnotationDto
import app.drift.data.codec.CheckInDto
import app.drift.data.codec.DriftJson
import app.drift.data.codec.EngineStateDto
import app.drift.data.codec.EpisodeDto
import app.drift.data.codec.ProfileDto
import app.drift.data.codec.SignalDto
import app.drift.data.codec.SnapshotCodec
import app.drift.data.codec.SnapshotDto
import app.drift.data.codec.toDto
import app.drift.data.crypto.KeyWrapper
import app.drift.data.db.AnnotationEntity
import app.drift.data.db.CheckInEntity
import app.drift.data.db.DriftDatabase
import app.drift.data.db.EngineStateEntity
import app.drift.data.db.ProfileEntity
import app.drift.data.db.SnapshotEntity
import app.drift.data.db.SourceEpisodeEntity
import app.drift.data.db.SourceStateEntity
import app.drift.data.db.UserEpisodeEntity
import app.drift.data.db.toDto as signalToDto
import app.drift.data.db.toEntity
import app.drift.model.repository.AppSettings
import app.drift.model.repository.BackupRepository
import app.drift.model.repository.ImportReport
import app.drift.model.repository.LocalBackup
import app.drift.model.repository.ReminderKind
import app.drift.model.repository.ThemeMode
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Serializable data class RawEpisodeBackup(val key: String, val ingestedAtMs: Long, val episode: EpisodeDto)
@Serializable data class UserEpisodeBackup(val episode: EpisodeDto, val deleted: Boolean, val suppressedKeys: List<String>)
@Serializable data class SourceStateBackup(val sourceId: String, val enabled: Boolean, val checkpoint: String?, val lastSyncMs: Long?, val lastError: String?)
@Serializable data class SettingsBackup(val theme: ThemeMode, val reminders: Map<ReminderKind, Boolean>, val phoneDetectionEnabled: Boolean, val use24HourClock: Boolean?)

/** Versioned export document. Bump [FORMAT_VERSION] and keep reading older versions when the shape changes. */
@Serializable
data class BackupDocument(
    val formatVersion: Int,
    val exportedAt: String,
    val profile: ProfileDto?,
    val onboardingComplete: Boolean,
    val settings: SettingsBackup,
    val sourceEpisodes: List<RawEpisodeBackup>,
    val signals: List<SignalDto>,
    val userEpisodes: List<UserEpisodeBackup>,
    val annotations: List<AnnotationDto>,
    val checkIns: List<CheckInDto>,
    val sourceState: List<SourceStateBackup>,
    /** Oldest first. */
    val snapshots: List<SnapshotDto>,
    val engineState: EngineStateDto?,
) {
    companion object { const val FORMAT_VERSION = 1 }
}

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val db: DriftDatabase,
    private val prefs: DriftPreferences,
    private val clock: Clock,
    @Named("backupDir") private val backupDir: File,
    @Named("backupCipher") private val cipher: KeyWrapper,
) : BackupRepository {

    override suspend fun exportAll(out: OutputStream) {
        val doc = db.withTransaction { buildDocument() }
        out.write(DriftJson.encodeToString(BackupDocument.serializer(), doc).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    override suspend fun importAll(input: InputStream): ImportReport {
        val text = input.readBytes().toString(Charsets.UTF_8)
        val doc = try {
            DriftJson.decodeFromString(BackupDocument.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a Drift backup: ${e.message}", e)
        }
        require(doc.formatVersion in 1..BackupDocument.FORMAT_VERSION) {
            "Backup format ${doc.formatVersion} is newer than this app supports (${BackupDocument.FORMAT_VERSION})"
        }
        db.withTransaction {
            clearTables()
            restore(doc)
        }
        prefs.clear()
        prefs.update { _ ->
            AppSettings(doc.settings.theme, ReminderKind.entries.associateWith { doc.settings.reminders[it] ?: AppSettings().reminders.getValue(it) }, doc.settings.phoneDetectionEnabled, doc.settings.use24HourClock)
        }
        prefs.setOnboardingComplete(doc.onboardingComplete)
        val days = doc.sourceEpisodes.map { LocalDate.parse(it.episode.end.substring(0, 10)) }
        return ImportReport(doc.sourceEpisodes.size + doc.userEpisodes.count { !it.deleted }, 0, 0, days.minOrNull(), days.maxOrNull(), emptyList())
    }

    override suspend fun wipeAll() {
        db.withTransaction { clearTables() }
        prefs.clear()
        backupDir.listFiles()?.forEach { it.delete() }
    }

    override suspend fun createLocalBackup(): LocalBackup {
        val json = ByteArrayOutputStream().also { exportAll(it) }.toByteArray()
        val zipped = ByteArrayOutputStream().also { b -> GZIPOutputStream(b).use { it.write(json) } }.toByteArray()
        val blob = MAGIC + cipher.encrypt(zipped)
        backupDir.mkdirs()
        var stamp = clock.millis()
        while (File(backupDir, "$PREFIX$stamp$SUFFIX").exists()) stamp++
        val file = File(backupDir, "$PREFIX$stamp$SUFFIX")
        val tmp = File(backupDir, "${file.name}.tmp")
        tmp.writeBytes(blob)
        check(tmp.renameTo(file)) { "Could not finalise backup file" }
        listFiles().drop(MAX_BACKUPS).forEach { it.delete() }
        return file.toBackup()
    }

    override suspend fun listLocalBackups(): List<LocalBackup> = listFiles().map { it.toBackup() }

    override suspend fun restoreLocalBackup(id: String) {
        val file = File(backupDir, "$id$SUFFIX")
        require(file.parentFile == backupDir && file.exists()) { "Unknown backup $id" }
        val bytes = file.readBytes()
        require(bytes.size > MAGIC.size && bytes.copyOf(MAGIC.size).contentEquals(MAGIC)) { "Backup file is damaged" }
        val zipped = cipher.decrypt(bytes.copyOfRange(MAGIC.size, bytes.size))
        val json = GZIPInputStream(ByteArrayInputStream(zipped)).use { it.readBytes() }
        importAll(ByteArrayInputStream(json))
    }

    // ---- internals -------------------------------------------------------------------------------

    private fun listFiles(): List<File> =
        (backupDir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) } ?: emptyArray())
            .sortedByDescending { it.name.removePrefix(PREFIX).removeSuffix(SUFFIX).toLongOrNull() ?: 0L }

    private fun File.toBackup(): LocalBackup {
        val stamp = name.removePrefix(PREFIX).removeSuffix(SUFFIX).toLong()
        return LocalBackup(name.removeSuffix(SUFFIX), Instant.ofEpochMilli(stamp), length())
    }

    private suspend fun buildDocument(): BackupDocument {
        val s = prefs.currentSettings()
        return BackupDocument(
            formatVersion = BackupDocument.FORMAT_VERSION,
            exportedAt = clock.instant().toString(),
            profile = db.profile().get()?.let { DriftJson.decodeFromString(ProfileDto.serializer(), it.payload) },
            onboardingComplete = prefs.isOnboardingComplete(),
            settings = SettingsBackup(s.theme, s.reminders, s.phoneDetectionEnabled, s.use24HourClock),
            sourceEpisodes = db.sourceEpisodes().all().map {
                RawEpisodeBackup(it.key, it.ingestedAtMs, DriftJson.decodeFromString(EpisodeDto.serializer(), it.payload))
            },
            signals = db.signals().all().map { it.signalToDto() },
            userEpisodes = db.userEpisodes().all().map {
                UserEpisodeBackup(
                    DriftJson.decodeFromString(EpisodeDto.serializer(), it.payload), it.deleted,
                    if (it.suppressedKeys.isEmpty()) emptyList() else it.suppressedKeys.split('\n'),
                )
            },
            annotations = db.annotations().all().map { AnnotationDto(LocalDate.ofEpochDay(it.epochDay).toString(), it.freeDay, it.alarmSet, it.alarmDismissedAt) },
            checkIns = db.checkIns().all().map { DriftJson.decodeFromString(CheckInDto.serializer(), it.payload) },
            sourceState = db.sourceState().all().map { SourceStateBackup(it.sourceId, it.enabled, it.checkpoint, it.lastSyncMs, it.lastError) },
            snapshots = db.snapshots().all().map { SnapshotCodec.decode(it.payload).toDto() },
            engineState = db.engineState().get()?.let { DriftJson.decodeFromString(EngineStateDto.serializer(), it.payload) },
        )
    }

    private suspend fun restore(doc: BackupDocument) {
        doc.profile?.let { db.profile().upsert(ProfileEntity(1, DriftJson.encodeToString(ProfileDto.serializer(), it))) }
        db.sourceEpisodes().upsert(
            doc.sourceEpisodes.map {
                SourceEpisodeEntity(
                    key = it.key, sourceId = it.episode.origin.sourceId, recordId = it.episode.origin.recordId,
                    startMs = Instant.parse(it.episode.start).toEpochMilli(), endMs = Instant.parse(it.episode.end).toEpochMilli(),
                    ingestedAtMs = it.ingestedAtMs, payload = DriftJson.encodeToString(EpisodeDto.serializer(), it.episode),
                )
            },
        )
        doc.signals.chunked(1000).forEach { chunk -> db.signals().upsert(chunk.map { it.toEntity() }) }
        db.userEpisodes().upsertAll(
            doc.userEpisodes.map {
                UserEpisodeEntity(
                    id = it.episode.id, startMs = Instant.parse(it.episode.start).toEpochMilli(),
                    endMs = Instant.parse(it.episode.end).toEpochMilli(), deleted = it.deleted,
                    suppressedKeys = it.suppressedKeys.joinToString("\n"),
                    payload = DriftJson.encodeToString(EpisodeDto.serializer(), it.episode),
                )
            },
        )
        db.annotations().upsertAll(
            doc.annotations.map { AnnotationEntity(LocalDate.parse(it.date).toEpochDay(), it.freeDay, it.alarmSet, it.alarmDismissedAt) },
        )
        db.checkIns().upsertAll(
            doc.checkIns.map { CheckInEntity(type = it.type, atMs = Instant.parse(it.at).toEpochMilli(), payload = DriftJson.encodeToString(CheckInDto.serializer(), it)) },
        )
        db.sourceState().upsertAll(doc.sourceState.map { SourceStateEntity(it.sourceId, it.enabled, it.checkpoint, it.lastSyncMs, it.lastError) })
        doc.snapshots.forEach { dto ->
            val snap = dto
            db.snapshots().upsert(
                SnapshotEntity(
                    computedAtMs = Instant.parse(snap.computedAt).toEpochMilli(),
                    engineVersion = with(snap.engineVersion) { "$major.$minor.$patch" },
                    inputHash = snap.inputHash, computedAt = snap.computedAt,
                    payload = DriftJson.encodeToString(SnapshotDto.serializer(), snap),
                ),
            )
        }
        doc.engineState?.let { db.engineState().upsert(EngineStateEntity(1, DriftJson.encodeToString(EngineStateDto.serializer(), it))) }
    }

    private suspend fun clearTables() {
        db.sourceEpisodes().clear(); db.signals().clear(); db.userEpisodes().clear(); db.annotations().clear()
        db.checkIns().clear(); db.sourceState().clear(); db.snapshots().clear(); db.engineState().clear(); db.profile().clear()
    }

    private companion object {
        val MAGIC = "DRB1".toByteArray(Charsets.US_ASCII)
        const val PREFIX = "backup-"
        const val SUFFIX = ".drb"
        const val MAX_BACKUPS = 7
    }
}
