package app.drift.testing.fakes

import app.drift.model.repository.BackupRepository
import app.drift.model.repository.ImportReport
import app.drift.model.repository.LocalBackup
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

/**
 * In-memory [BackupRepository]. [exportAll] writes [exportPayload]; [importAll] captures what it is given in
 * [importedPayloads] and returns [importResult]. [wipeAll] runs [onWipe] so tests can clear sibling fakes.
 */
class FakeBackupRepository(
    var exportPayload: ByteArray = "drift-fake-export-v1".toByteArray(),
    var importResult: ImportReport = FakeSourceRepository.emptyImportReport,
    var onWipe: () -> Unit = {},
    var clock: () -> Instant = { Instant.parse("2026-10-14T07:30:00Z") },
) : Recording(), BackupRepository {
    private val backups = mutableListOf<LocalBackup>()
    private var nextId = 1
    val importedPayloads = mutableListOf<ByteArray>()
    var restoredId: String? = null
        private set

    override suspend fun exportAll(out: OutputStream) {
        record("exportAll")
        out.write(exportPayload)
        out.flush()
    }

    override suspend fun importAll(input: InputStream): ImportReport {
        record("importAll")
        importedPayloads += input.readBytes()
        return importResult
    }

    override suspend fun wipeAll() {
        record("wipeAll")
        backups.clear()
        onWipe()
    }

    override suspend fun createLocalBackup(): LocalBackup {
        record("createLocalBackup")
        return LocalBackup("backup-${nextId++}", clock(), exportPayload.size.toLong()).also { backups += it }
    }

    override suspend fun listLocalBackups(): List<LocalBackup> {
        record("listLocalBackups")
        return backups.sortedByDescending { it.createdAt }
    }

    override suspend fun restoreLocalBackup(id: String) {
        record("restoreLocalBackup", id)
        require(backups.any { it.id == id }) { "No such backup: $id" }
        restoredId = id
    }
}
