package app.drift.testing.fakes

import app.drift.model.SleepEpisode
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.repository.AnalysisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/**
 * In-memory [AnalysisRepository]. [refresh] returns what [onRefresh] produces (default: the current snapshot
 * unchanged) and rotates latest into previous, like the real repository. [preview] never persists.
 */
class FakeAnalysisRepository(
    latest: AnalysisSnapshot? = null,
    previous: AnalysisSnapshot? = null,
    var onRefresh: ((Instant) -> AnalysisSnapshot)? = null,
    /** What [preview] returns for a set of edits (default: the current snapshot unchanged). */
    var onPreview: ((List<SleepEpisode>, Instant) -> AnalysisSnapshot)? = null,
) : Recording(), AnalysisRepository {
    private val latestFlow = MutableStateFlow(latest)
    private val previousFlow = MutableStateFlow(previous)

    val latest: AnalysisSnapshot? get() = latestFlow.value

    fun setLatest(snapshot: AnalysisSnapshot?) { latestFlow.value = snapshot }
    fun setPrevious(snapshot: AnalysisSnapshot?) { previousFlow.value = snapshot }

    override fun observeLatest(): Flow<AnalysisSnapshot?> = latestFlow
    override fun observePrevious(): Flow<AnalysisSnapshot?> = previousFlow

    override suspend fun refresh(now: Instant): AnalysisSnapshot {
        record("refresh", now)
        val next = onRefresh?.invoke(now) ?: latestFlow.value
            ?: throw IllegalStateException("FakeAnalysisRepository.refresh: seed a snapshot or set onRefresh")
        if (next !== latestFlow.value) {
            previousFlow.value = latestFlow.value
            latestFlow.value = next
        }
        return next
    }

    override suspend fun preview(edits: List<SleepEpisode>, now: Instant): AnalysisSnapshot {
        record("preview", edits, now)
        return onPreview?.invoke(edits, now) ?: latestFlow.value
            ?: throw IllegalStateException("FakeAnalysisRepository.preview: seed a snapshot or set onPreview")
    }
}
