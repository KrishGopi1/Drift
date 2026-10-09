package app.drift.testing.fakes

import app.drift.model.SourceId
import app.drift.model.repository.ImportReport
import app.drift.model.repository.SourceInfo
import app.drift.model.repository.SourceRepository
import app.drift.model.repository.SyncReason
import app.drift.model.repository.SyncReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.InputStream
import java.time.Instant

/** In-memory [SourceRepository]. Script results via [syncResult] / [importResult]. */
class FakeSourceRepository(
    sources: List<SourceInfo> = emptyList(),
    var syncResult: (SyncReason) -> SyncReport = { emptySyncReport },
    var importResult: ImportReport = emptyImportReport,
) : Recording(), SourceRepository {
    private val sourcesFlow = MutableStateFlow(sources)
    private val progress = MutableStateFlow<Float?>(null)

    /** Bytes handed to [importSleepAsAndroidBackup], one entry per call. */
    val importedPayloads = mutableListOf<ByteArray>()
    val sources: List<SourceInfo> get() = sourcesFlow.value

    fun setSources(list: List<SourceInfo>) { sourcesFlow.value = list }
    fun setProgress(value: Float?) { progress.value = value }

    override fun observeSources(): Flow<List<SourceInfo>> = sourcesFlow
    override fun observeSyncProgress(): Flow<Float?> = progress

    override suspend fun sync(reason: SyncReason): SyncReport {
        record("sync", reason)
        return syncResult(reason)
    }

    override suspend fun setEnabled(id: SourceId, enabled: Boolean) {
        record("setEnabled", id, enabled)
        sourcesFlow.update { l -> l.map { if (it.id == id) it.copy(enabled = enabled) else it } }
    }

    override suspend fun importSleepAsAndroidBackup(input: InputStream): ImportReport {
        record("importSleepAsAndroidBackup")
        importedPayloads += input.readBytes()
        return importResult
    }

    companion object {
        val emptySyncReport = SyncReport(Instant.EPOCH, Instant.EPOCH, 0, 0, 0, emptyMap())
        val emptyImportReport = ImportReport(0, 0, 0, null, null, emptyList())
    }
}
