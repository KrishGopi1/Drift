package app.drift.feature.sleep.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drift.feature.sleep.di.SleepClock
import app.drift.feature.sleep.di.SleepTexts
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.repository.AnalysisRepository
import app.drift.model.repository.SettingsRepository
import app.drift.model.repository.SleepRepository
import app.drift.model.repository.SourceRepository
import app.drift.model.ui.InsufficientReason
import app.drift.model.ui.ScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sleep: SleepRepository,
    private val analysis: AnalysisRepository,
    private val sources: SourceRepository,
    private val settings: SettingsRepository,
    @SleepClock private val clock: Clock,
    private val texts: SleepTexts,
) : ViewModel() {
    private val range = MutableStateFlow(HistoryRange.WEEK)
    private val attempt = MutableStateFlow(0)

    val selectedRange: StateFlow<HistoryRange> = range

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ScreenState<HistoryUiModel>> = attempt.flatMapLatest {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val widest = HistoryRange.QUARTER.timing.days.toLong()
        val window = Interval(
            today.minusDays(widest).atStartOfDay(zone).toInstant(),
            today.plusDays(1).atStartOfDay(zone).toInstant(),
        )
        combine(
            sleep.observeEpisodes(window),
            analysis.observeLatest().map { it?.sleepNeed?.value },
            settings.observeSettings(),
            sources.observeSources(),
            range,
        ) { episodes, need, prefs, infos, selected ->
            val broken = infos.firstOrNull { it.enabled && it.fix != null }
            val problem = broken?.let { HistoryProblem(it.fix!!, texts.sourceProblem(it.displayName)) }
            when {
                episodes.none { it.kind == EpisodeKind.MAIN } && problem != null ->
                    ScreenState.SourceProblem(problem.fix, problem.message)
                episodes.none { it.kind == EpisodeKind.MAIN } ->
                    ScreenState.Insufficient(InsufficientReason.NO_NIGHTS_YET, 1)
                else -> ScreenState.Ready(buildHistoryModel(selected, episodes, today, zone, need, prefs.use24HourClock, problem))
            }
        }.catch { emit(ScreenState.Error(texts.loadFailed(), retryable = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    fun selectRange(next: HistoryRange) { range.value = next }

    fun retry() = attempt.update { it + 1 }
}
