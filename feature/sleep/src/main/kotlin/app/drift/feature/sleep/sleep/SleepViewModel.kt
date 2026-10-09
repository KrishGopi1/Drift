package app.drift.feature.sleep.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drift.feature.sleep.common.SnapshotFeed
import app.drift.feature.sleep.common.MIN_NIGHTS
import app.drift.feature.sleep.common.snapshotFeed
import app.drift.feature.sleep.common.toState
import app.drift.feature.sleep.di.SleepClock
import app.drift.feature.sleep.di.SleepTexts
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.repository.AnalysisRepository
import app.drift.model.repository.SettingsRepository
import app.drift.model.repository.SleepRepository
import app.drift.model.repository.SourceRepository
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
class SleepViewModel @Inject constructor(
    private val analysis: AnalysisRepository,
    private val sources: SourceRepository,
    private val settings: SettingsRepository,
    private val sleep: SleepRepository,
    @SleepClock private val clock: Clock,
    private val texts: SleepTexts,
) : ViewModel() {
    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ScreenState<SleepUiModel>> = attempt.flatMapLatest {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val window = Interval(
            today.minusDays(15).atStartOfDay(zone).toInstant(),
            today.plusDays(1).atStartOfDay(zone).toInstant(),
        )
        val ids = sleep.observeEpisodes(window).map { list ->
            list.filter { it.kind == EpisodeKind.MAIN }
                .associate { it.end.atZone(it.zone).toLocalDate() to it.id }
        }
        combine(snapshotFeed(analysis, sources, settings, texts), ids) { feed, nightIds ->
            when (feed) {
                is SnapshotFeed.Data -> ScreenState.Ready(feed.snapshot.toSleepUiModel(zone, feed.use24hSetting, nightIds))
                is SnapshotFeed.NoSnapshot -> feed.toState(MIN_NIGHTS)
            }
        }.catch { emit(ScreenState.Error(texts.loadFailed(), retryable = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    fun retry() = attempt.update { it + 1 }
}
