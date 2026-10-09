package app.drift.sources

import app.drift.data.repo.BackupImporter
import app.drift.data.repo.ImportBatch
import app.drift.model.SourceId
import app.drift.source.sleepasandroid.SleepAsAndroidSourceAdapter
import java.io.InputStream
import javax.inject.Inject

/** Bridges the CSV parser in :source:sleepasandroid to the import pipeline in :core:data (docs/issues/a2-import-seam.md). */
class SleepAsAndroidBackupImporter @Inject constructor(
    private val adapter: SleepAsAndroidSourceAdapter,
) : BackupImporter {
    override val sourceId: SourceId = SourceId.SLEEP_AS_ANDROID

    override suspend fun parse(input: InputStream): ImportBatch =
        adapter.importBackup(input).let { ImportBatch(it.episodes, it.signals, it.rowsRejected, it.warnings) }
}
