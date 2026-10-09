package app.drift.feature.sleep.why

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drift.feature.sleep.di.SleepClock
import app.drift.feature.sleep.di.SleepTexts
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.engine.MetricKey
import app.drift.model.repository.AnalysisRepository
import app.drift.model.repository.SleepRepository
import app.drift.model.ui.ScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class WhyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val analysis: AnalysisRepository,
    private val sleep: SleepRepository,
    @SleepClock private val clock: Clock,
    private val texts: SleepTexts,
) : ViewModel() {
    val metric: MetricKey = savedStateHandle.get<String>(ARG_METRIC)
        ?.let { name -> MetricKey.entries.firstOrNull { it.name == name } }
        ?: MetricKey.SLEEP_DEBT

    private val attempt = MutableStateFlow(0)
    private val openChannel = Channel<EpisodeId>(Channel.BUFFERED)

    /** Night to open after the user taps a dated cause. */
    val openNight: Flow<EpisodeId> = openChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ScreenState<WhyUiModel>> = attempt.flatMapLatest {
        combine(analysis.observeLatest(), analysis.observePrevious()) { latest, previous ->
            ScreenState.Ready(buildWhyModel(metric, latest, previous)) as ScreenState<WhyUiModel>
        }.catch { emit(ScreenState.Error(texts.loadFailed(), retryable = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    fun retry() = attempt.update { it + 1 }

    /** Looks up the main episode of the sleep day [date]; nothing happens when that night has no data. */
    fun openNight(date: LocalDate) {
        viewModelScope.launch {
            val zone = clock.zone
            val window = Interval(
                date.minusDays(1).atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant(),
            )
            val episode = sleep.observeEpisodes(window).first()
                .filter { it.kind == EpisodeKind.MAIN && it.end.atZone(it.zone).toLocalDate() == date }
                .maxByOrNull { it.end.epochSecond - it.start.epochSecond }
            if (episode != null) openChannel.send(episode.id)
        }
    }

    companion object {
        const val ARG_METRIC = "metric"
    }
}
