package app.drift.model.source

import app.drift.model.Checkpoint
import app.drift.model.QualityTier
import app.drift.model.Signal
import app.drift.model.SleepEpisode
import app.drift.model.SourceId

/** C3. What an adapter can provide. */
enum class Capability { SLEEP_SESSIONS, SLEEP_STAGES, HEART_RATE, RESTING_HEART_RATE, HRV, STEPS, EXERCISE, SCREEN, MOTION, LIGHT, CHARGING, ALARMS }

/** C3. Adapter health. Every non-AVAILABLE state maps to a "fix" action in the UI. */
sealed interface SourceStatus {
    data object Available : SourceStatus
    data class NeedsPermission(val missing: Set<String>) : SourceStatus
    data object NotInstalled : SourceStatus
    /** e.g. Health Connect present but needs an update, or the OS version does not support it. */
    data object NotSupported : SourceStatus
    data class Error(val reason: String) : SourceStatus
}

/** C3. Result of one pull: new/updated data since the checkpoint plus the next checkpoint. */
data class PullResult(
    val episodes: List<SleepEpisode>,
    val signals: List<Signal>,
    val next: Checkpoint?,
    /** Record ids (SourceRef.recordId) the source reports as deleted since the checkpoint. */
    val deletedRecordIds: Set<String> = emptySet(),
    /** True if the checkpoint was invalid/expired and this is a bounded full re-read. */
    val fullResync: Boolean = false,
)

/**
 * C3. The boAt-ready seam. One implementation per data source. Only `:app` knows the concrete set.
 * Implementations must be safe to call from any thread and must not throw for expected failures
 * (permission revoked, app uninstalled): report those via [status] and return an empty [PullResult].
 */
interface SourceAdapter {
    val id: SourceId
    val capabilities: Set<Capability>
    val qualityTier: QualityTier
    suspend fun status(): SourceStatus
    suspend fun pull(since: Checkpoint?): PullResult
}
