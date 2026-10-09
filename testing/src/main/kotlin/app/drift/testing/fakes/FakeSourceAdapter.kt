package app.drift.testing.fakes

import app.drift.model.Checkpoint
import app.drift.model.QualityTier
import app.drift.model.SourceId
import app.drift.model.source.Capability
import app.drift.model.source.PullResult
import app.drift.model.source.SourceAdapter
import app.drift.model.source.SourceStatus

/**
 * Configurable [SourceAdapter]. Set [currentStatus]; queue pull outcomes with [enqueue] / [enqueueFailure]
 * (consumed in order; when the queue is empty [defaultResult] is returned). Checkpoints seen are in [pullCheckpoints].
 */
class FakeSourceAdapter(
    override val id: SourceId = SourceId.HEALTH_CONNECT,
    override val capabilities: Set<Capability> = setOf(Capability.SLEEP_SESSIONS),
    override val qualityTier: QualityTier = QualityTier.WEARABLE_WRIST,
    var currentStatus: SourceStatus = SourceStatus.Available,
    var defaultResult: PullResult = PullResult(emptyList(), emptyList(), null),
) : Recording(), SourceAdapter {
    private val script = ArrayDeque<Result<PullResult>>()
    val pullCheckpoints = mutableListOf<Checkpoint?>()

    fun enqueue(vararg results: PullResult) { results.forEach { script.addLast(Result.success(it)) } }
    fun enqueueFailure(error: Throwable) { script.addLast(Result.failure(error)) }

    override suspend fun status(): SourceStatus {
        record("status")
        return currentStatus
    }

    override suspend fun pull(since: Checkpoint?): PullResult {
        record("pull", since)
        pullCheckpoints += since
        // Contract: expected failures are reported via status() with an empty result.
        if (currentStatus != SourceStatus.Available) return PullResult(emptyList(), emptyList(), since)
        return (script.removeFirstOrNull() ?: Result.success(defaultResult)).getOrThrow()
    }
}
