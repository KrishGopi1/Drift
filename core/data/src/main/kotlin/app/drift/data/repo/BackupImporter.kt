package app.drift.data.repo

import app.drift.model.Signal
import app.drift.model.SleepEpisode
import app.drift.model.SourceId
import java.io.InputStream

/** Parsed content of a third-party backup file, in canonical model types. */
data class ImportBatch(
    val episodes: List<SleepEpisode>,
    val signals: List<Signal>,
    val rowsRejected: Int,
    val warnings: List<String>,
)

/**
 * Parses a backup file from one source (currently Sleep as Android CSV). :core:data cannot depend on
 * the source modules, so :app binds an implementation per importable source into a multibinding set.
 */
interface BackupImporter {
    val sourceId: SourceId
    suspend fun parse(input: InputStream): ImportBatch
}
