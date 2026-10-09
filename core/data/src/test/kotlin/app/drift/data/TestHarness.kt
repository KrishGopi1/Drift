package app.drift.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.drift.data.crypto.AesGcmWrapper
import app.drift.data.crypto.KeyWrapper
import app.drift.data.db.DriftDatabase
import app.drift.data.repo.AnalysisRepositoryImpl
import app.drift.data.repo.BackupImporter
import app.drift.data.repo.BackupRepositoryImpl
import app.drift.data.repo.CheckInRepositoryImpl
import app.drift.data.repo.DriftPreferences
import app.drift.data.repo.ProfileRepositoryImpl
import app.drift.data.repo.SettingsRepositoryImpl
import app.drift.data.repo.SleepRepositoryImpl
import app.drift.data.repo.SourceRepositoryImpl
import app.drift.data.repo.TimelineStore
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.EngineInput
import app.drift.model.engine.EngineVersion
import app.drift.model.engine.SleepEngine
import app.drift.model.source.SourceAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.crypto.KeyGenerator

class MutableClock(var now: Instant) : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
    override fun instant(): Instant = now
}

/** Records every [EngineInput] it receives and returns a canned snapshot. */
class RecordingEngine(var hash: String = "hash-1") : SleepEngine {
    override val version = EngineVersion(1, 2, 3)
    val inputs = ArrayList<EngineInput>()
    override fun analyze(input: EngineInput): AnalysisSnapshot {
        inputs += input
        return Fixtures.snapshot(hash, input.now)
    }
}

class Harness(
    context: Context,
    val clock: MutableClock = MutableClock(Instant.parse("2026-10-09T08:00:00Z")),
    adapters: Set<SourceAdapter> = emptySet(),
    importers: Set<BackupImporter> = emptySet(),
    val engine: RecordingEngine = RecordingEngine(),
) {
    val dir: File = Files.createTempDirectory("drift-test").toFile()
    val db: DriftDatabase = DriftDatabase.inMemory(context)
    val prefs = DriftPreferences(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { File(dir, "prefs.preferences_pb") })
    val timeline = TimelineStore(db)
    val sleep = SleepRepositoryImpl(db, timeline, clock)
    val analysis = AnalysisRepositoryImpl(db, timeline, engine, clock)
    val sources = SourceRepositoryImpl(db, adapters, importers, clock)
    val profile = ProfileRepositoryImpl(db, prefs)
    val settings = SettingsRepositoryImpl(prefs)
    val checkIns = CheckInRepositoryImpl(db)
    val cipher: KeyWrapper = AesGcmWrapper { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().let { fixedKey } }
    val backups = BackupRepositoryImpl(db, prefs, clock, File(dir, "backups"), cipher)

    fun close() { db.close(); dir.deleteRecursively() }

    private companion object {
        val fixedKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
}
