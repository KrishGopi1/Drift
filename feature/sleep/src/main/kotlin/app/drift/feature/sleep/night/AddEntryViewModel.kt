package app.drift.feature.sleep.night

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drift.feature.sleep.di.EpisodeIdGenerator
import app.drift.feature.sleep.di.SleepClock
import app.drift.model.EpisodeKind
import app.drift.model.repository.SettingsRepository
import app.drift.model.repository.SleepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Adds a missing night or a nap as a USER_ENTERED episode (plan 6.2). */
@HiltViewModel
class AddEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sleep: SleepRepository,
    settings: SettingsRepository,
    @SleepClock private val clock: Clock,
    private val ids: EpisodeIdGenerator,
) : ViewModel() {
    val kind: EpisodeKind = savedStateHandle.get<String>(ARG_KIND)?.let { runCatching { EpisodeKind.valueOf(it) }.getOrNull() } ?: EpisodeKind.MAIN
    val date: LocalDate = savedStateHandle.get<String>(ARG_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(clock)

    private val editorState = MutableStateFlow(
        EpisodeDraft.blank(kind, date, clock.zone).let { EditorUiState(it, it.errors(clock.instant())) },
    )
    private val savedChannel = Channel<Unit>(Channel.BUFFERED)

    val editor: StateFlow<EditorUiState> = editorState
    val saved: Flow<Unit> = savedChannel.receiveAsFlow()
    val use24hSetting: StateFlow<Boolean?> = settings.observeSettings().map { it.use24HourClock }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setStart(minutes: Int) = updateDraft { it.withStart(minutes) }

    fun setEnd(minutes: Int) = updateDraft { it.withEnd(minutes) }

    private fun updateDraft(change: (EpisodeDraft) -> EpisodeDraft) {
        editorState.update {
            val next = change(it.draft)
            it.copy(draft = next, errors = next.errors(clock.instant()), saveFailed = false)
        }
    }

    fun save() {
        val current = editorState.value
        if (!current.canSave) return
        editorState.value = current.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                sleep.saveUserEpisode(newManualEpisode(ids.next(), current.draft, kind))
                savedChannel.send(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                editorState.value = current.copy(saving = false, saveFailed = true)
            }
        }
    }

    companion object {
        const val ARG_DATE = "date"
        const val ARG_KIND = "kind"
    }
}
