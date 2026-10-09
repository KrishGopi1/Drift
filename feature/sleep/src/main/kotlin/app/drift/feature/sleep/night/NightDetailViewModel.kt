package app.drift.feature.sleep.night

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drift.feature.sleep.di.SleepClock
import app.drift.feature.sleep.di.SleepTexts
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeId
import app.drift.model.Interval
import app.drift.model.QualityFlag
import app.drift.model.SleepEpisode
import app.drift.model.repository.SettingsRepository
import app.drift.model.repository.SleepRepository
import app.drift.model.ui.ScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/** One-shot results the screen reacts to by navigating. */
enum class NightEvent { DELETED }

@HiltViewModel
class NightDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sleep: SleepRepository,
    private val settings: SettingsRepository,
    @SleepClock private val clock: Clock,
    private val texts: SleepTexts,
) : ViewModel() {
    val episodeId = EpisodeId(checkNotNull(savedStateHandle.get<String>(ARG_EPISODE_ID)) { "episode id missing" })

    private val attempt = MutableStateFlow(0)
    private val removed = MutableStateFlow(false)
    private val editorState = MutableStateFlow<EditorUiState?>(null)
    private val eventChannel = Channel<NightEvent>(Channel.BUFFERED)

    /** Non-null while the times are being edited. */
    val editor: StateFlow<EditorUiState?> = editorState
    val events: Flow<NightEvent> = eventChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ScreenState<NightDetailUiModel>> = attempt.flatMapLatest {
        sleep.observeEpisode(episodeId).flatMapLatest { episode ->
            when {
                episode != null -> detailFlow(episode)
                removed.value -> flowOf<ScreenState<NightDetailUiModel>>(ScreenState.Loading)
                else -> flowOf(ScreenState.Error(texts.nightUnavailable(), retryable = false))
            }
        }.catch { emit(ScreenState.Error(texts.loadFailed(), retryable = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    private fun detailFlow(episode: SleepEpisode): Flow<ScreenState<NightDetailUiModel>> {
        val day = episode.end.atZone(episode.zone).toLocalDate()
        val sources: Flow<List<SleepEpisode>> = if (QualityFlag.SOURCE_CONFLICT in episode.quality.flags) {
            sleep.observeSourceEpisodes(Interval(episode.start.minus(Duration.ofHours(1)), episode.end.plus(Duration.ofHours(1))))
        } else {
            flowOf(emptyList())
        }
        return combine(sources, sleep.observeAnnotations(day, day), settings.observeSettings()) { raw, annotations, prefs ->
            ScreenState.Ready(buildNightDetail(episode, raw, annotations.firstOrNull { it.date == day }, prefs.use24HourClock))
        }
    }

    fun retry() = attempt.update { it + 1 }

    fun startEdit() {
        viewModelScope.launch {
            val episode = sleep.observeEpisode(episodeId).first() ?: return@launch
            val draft = EpisodeDraft.of(episode)
            editorState.value = EditorUiState(draft, draft.errors(clock.instant()))
        }
    }

    fun cancelEdit() { editorState.value = null }

    fun setStart(minutes: Int) = updateDraft { it.withStart(minutes) }

    fun setEnd(minutes: Int) = updateDraft { it.withEnd(minutes) }

    private fun updateDraft(change: (EpisodeDraft) -> EpisodeDraft) {
        editorState.update { current ->
            current?.let {
                val next = change(it.draft)
                it.copy(draft = next, errors = next.errors(clock.instant()), saveFailed = false)
            }
        }
    }

    fun save() {
        val current = editorState.value ?: return
        if (!current.canSave) return
        editorState.value = current.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                val episode = sleep.observeEpisode(episodeId).first() ?: return@launch
                sleep.saveUserEpisode(episode.withDraft(current.draft))
                editorState.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                editorState.value = current.copy(saving = false, saveFailed = true)
            }
        }
    }

    fun setFreeDay(day: java.time.LocalDate, free: Boolean) {
        viewModelScope.launch {
            val existing = sleep.observeAnnotations(day, day).first().firstOrNull { it.date == day }
            sleep.setAnnotation(existing?.copy(freeDay = free) ?: DayAnnotation(day, freeDay = free))
        }
    }

    fun useSource(option: SourceOption) {
        viewModelScope.launch {
            val episode = sleep.observeEpisode(episodeId).first() ?: return@launch
            sleep.saveUserEpisode(episode.adoptSource(option.episode))
        }
    }

    fun delete() {
        viewModelScope.launch {
            removed.value = true
            try {
                sleep.deleteEpisode(episodeId)
                eventChannel.send(NightEvent.DELETED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                removed.value = false
            }
        }
    }

    companion object {
        const val ARG_EPISODE_ID = "episodeId"
    }
}
