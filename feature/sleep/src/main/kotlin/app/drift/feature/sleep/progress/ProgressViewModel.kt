package app.drift.feature.sleep.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drift.feature.sleep.di.SleepClock
import app.drift.feature.sleep.di.SleepTexts
import app.drift.model.CheckIn
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.repository.AnalysisRepository
import app.drift.model.repository.CheckInRepository
import app.drift.model.repository.SettingsRepository
import app.drift.model.repository.SleepRepository
import app.drift.model.ui.InsufficientReason
import app.drift.model.ui.ScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Progress tab: two weeks of sleep times, debt and self-rated quality, plus rating last night. */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val sleep: SleepRepository,
    private val analysis: AnalysisRepository,
    private val settings: SettingsRepository,
    private val checkIns: CheckInRepository,
    @SleepClock private val clock: Clock,
    private val texts: SleepTexts,
) : ViewModel() {
    private val attempt = MutableStateFlow(0)
    private val rateFailed = MutableStateFlow(false)

    /** True after saving a rating failed; cleared by the next attempt. */
    val ratingFailed: StateFlow<Boolean> = rateFailed

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ScreenState<ProgressUiModel>> = attempt.flatMapLatest {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val from = today.minusDays(PROGRESS_NIGHTS.toLong()).atStartOfDay(zone).toInstant()
        val window = Interval(from, today.plusDays(1).atStartOfDay(zone).toInstant())
        combine(
            sleep.observeEpisodes(window),
            analysis.observeLatest(),
            checkIns.observeCheckIns(from),
            settings.observeSettings(),
        ) { episodes, snapshot, ins, prefs ->
            if (episodes.none { it.kind == EpisodeKind.MAIN }) {
                ScreenState.Insufficient(InsufficientReason.NO_NIGHTS_YET, 1)
            } else {
                ScreenState.Ready(buildProgressModel(today, zone, episodes, snapshot, ins, prefs.use24HourClock))
            }
        }.catch { emit(ScreenState.Error(texts.loadFailed(), retryable = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    /** Saves how [night] felt, 1 (poor) to 5 (great). */
    fun rate(night: LocalDate, rating: Int) {
        rateFailed.value = false
        viewModelScope.launch {
            try {
                checkIns.add(CheckIn.SleepRating(clock.instant(), night, rating))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rateFailed.value = true
            }
        }
    }

    fun retry() = attempt.update { it + 1 }
}
