package app.drift.source.sleepasandroid

import app.drift.model.Checkpoint
import app.drift.model.QualityTier
import app.drift.model.SourceId
import app.drift.model.source.Capability
import app.drift.model.source.PullResult
import app.drift.model.source.SourceAdapter
import app.drift.model.source.SourceStatus
import java.io.InputStream
import javax.inject.Inject

/**
 * Sleep as Android (plan 5.2).
 *
 * - Path 1 (its Health Connect sync) arrives through the Health Connect adapter, tagged by origin.
 * - Path 2 (CSV backup) is read by [importBackup]; `:app` hands the result to the data layer through the
 *   `BackupImporter` seam.
 * - Path 3 (tracking-finished broadcast) is handled by [SleepAsAndroidBroadcastReceiver], which asks `:app` to run a sync.
 *
 * Because nothing can be pulled from the app directly, [pull] returns an empty result; [status] reports
 * whether the app is installed (when it is not, the fix offered to the user is the CSV import).
 */
class SleepAsAndroidSourceAdapter(
    private val installed: () -> Boolean,
    private val parser: SleepAsAndroidCsvParser,
) : SourceAdapter {

    @Inject constructor(detector: SleepAsAndroidInstallDetector) :
        this({ detector.isInstalled() }, SleepAsAndroidCsvParser())

    override val id: SourceId = SourceId.SLEEP_AS_ANDROID
    override val qualityTier: QualityTier = QualityTier.PHONE_APP
    override val capabilities: Set<Capability> = setOf(Capability.SLEEP_SESSIONS, Capability.SLEEP_STAGES, Capability.MOTION)

    override suspend fun status(): SourceStatus = if (installed()) SourceStatus.Available else SourceStatus.NotInstalled

    override suspend fun pull(since: Checkpoint?): PullResult = PullResult(emptyList(), emptyList(), since)

    /** Parses a Sleep as Android CSV backup. Never throws for malformed content; bad rows are counted. */
    fun importBackup(input: InputStream): SleepAsAndroidImport = parser.parse(input)
}
