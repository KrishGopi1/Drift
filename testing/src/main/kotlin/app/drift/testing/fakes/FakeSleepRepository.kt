package app.drift.testing.fakes

import app.drift.model.DayAnnotation
import app.drift.model.EpisodeId
import app.drift.model.Interval
import app.drift.model.QualityFlag
import app.drift.model.SleepEpisode
import app.drift.model.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** In-memory [SleepRepository]. Seed with [setEpisodes] / [setSourceEpisodes] / [setPending] / [setAnnotations]. */
class FakeSleepRepository(
    episodes: List<SleepEpisode> = emptyList(),
    sourceEpisodes: List<SleepEpisode> = emptyList(),
    pending: SleepEpisode? = null,
    annotations: List<DayAnnotation> = emptyList(),
) : Recording(), SleepRepository {
    private val merged = MutableStateFlow(episodes)
    private val perSource = MutableStateFlow(sourceEpisodes)
    private val pendingFlow = MutableStateFlow(pending)
    private val annotationFlow = MutableStateFlow(annotations)

    val episodes: List<SleepEpisode> get() = merged.value
    val annotations: List<DayAnnotation> get() = annotationFlow.value

    fun setEpisodes(list: List<SleepEpisode>) { merged.value = list }
    fun setSourceEpisodes(list: List<SleepEpisode>) { perSource.value = list }
    fun setPending(episode: SleepEpisode?) { pendingFlow.value = episode }
    fun setAnnotations(list: List<DayAnnotation>) { annotationFlow.value = list }

    override fun observeEpisodes(range: Interval): Flow<List<SleepEpisode>> = guarded("observeEpisodes", range) {
        merged.map { l -> l.filter { it.interval.overlaps(range) }.sortedBy { it.start } }
    }

    override fun observeEpisode(id: EpisodeId): Flow<SleepEpisode?> = guarded("observeEpisode", id) {
        merged.map { l -> l.firstOrNull { it.id == id } }
    }

    override fun observeSourceEpisodes(range: Interval): Flow<List<SleepEpisode>> = guarded("observeSourceEpisodes", range) {
        perSource.map { l -> l.filter { it.interval.overlaps(range) }.sortedBy { it.start } }
    }

    override fun observePendingConfirmation(): Flow<SleepEpisode?> = guarded("observePendingConfirmation") { pendingFlow }

    override suspend fun saveUserEpisode(episode: SleepEpisode) {
        record("saveUserEpisode", episode)
        val saved = episode.copy(userEdited = true)
        merged.update { l -> l.filterNot { it.id == saved.id } + saved }
    }

    override suspend fun confirmEpisode(id: EpisodeId) {
        record("confirmEpisode", id)
        merged.update { l ->
            l.map { if (it.id == id) it.copy(quality = it.quality.copy(flags = it.quality.flags + QualityFlag.USER_CONFIRMED)) else it }
        }
        pendingFlow.update { if (it?.id == id) null else it }
    }

    override suspend fun deleteEpisode(id: EpisodeId) {
        record("deleteEpisode", id)
        merged.update { l -> l.filterNot { it.id == id } }
        pendingFlow.update { if (it?.id == id) null else it }
    }

    override fun observeAnnotations(from: LocalDate, to: LocalDate): Flow<List<DayAnnotation>> =
        guarded("observeAnnotations", from, to) {
            annotationFlow.map { l -> l.filter { it.date >= from && it.date <= to }.sortedBy { it.date } }
        }

    override suspend fun setAnnotation(annotation: DayAnnotation) {
        record("setAnnotation", annotation)
        annotationFlow.update { l -> l.filterNot { it.date == annotation.date } + annotation }
    }

    /** Records the call and applies scripted faults when the flow is collected, not when it is created. */
    private fun <T> guarded(name: String, vararg args: Any?, source: () -> Flow<T>): Flow<T> = flow {
        record(name, *args)
        emitAll(source())
    }
}

private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<T>.emitAll(f: Flow<T>) =
    f.collect { emit(it) }
