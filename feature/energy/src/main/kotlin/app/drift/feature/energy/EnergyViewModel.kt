package app.drift.feature.energy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drift.feature.energy.di.EnergyClock
import app.drift.model.CheckIn
import app.drift.model.EpisodeId
import app.drift.model.Interval
import app.drift.model.SleepEpisode
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.repository.AnalysisRepository
import app.drift.model.repository.AppSettings
import app.drift.model.repository.CheckInRepository
import app.drift.model.repository.SettingsRepository
import app.drift.model.repository.SleepRepository
import app.drift.model.ui.ScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Energy home screen state. Combines the latest analysis, the pending sleep confirmation, settings and recent
 * check-ins with a clock that ticks every minute (so the now-marker and phase stay live). Refreshes the analysis
 * once on creation.
 */
@HiltViewModel
class EnergyViewModel @Inject constructor(
    private val analysis: AnalysisRepository,
    private val sleep: SleepRepository,
    settings: SettingsRepository,
    private val checkIns: CheckInRepository,
    @EnergyClock private val clock: Clock,
) : ViewModel() {

    private val refreshFailed = MutableStateFlow(false)
    private val actionError = MutableStateFlow<EnergyActionError?>(null)
    private val dismissedEpisodes = MutableStateFlow<Set<EpisodeId>>(emptySet())

    /** The sleep block being dragged, not saved yet. Each change reruns the engine as a preview. */
    private val draft = MutableStateFlow<SleepEpisode?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val preview: Flow<AnalysisSnapshot?> = draft.mapLatest { edit ->
        edit?.let {
            try {
                analysis.preview(listOf(it), clock.instant())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Sleep from yesterday evening on, for the timeline's blocks. */
    private val episodes: Flow<List<SleepEpisode>> = flow {
        val now = clock.instant()
        val range = Interval(now.minus(Duration.ofHours(48)), now.plus(Duration.ofHours(24)))
        emitAll(sleep.observeEpisodes(range).catch { emit(emptyList()) })
    }

    private val snapshots: Flow<Result<AnalysisSnapshot?>> =
        analysis.observeLatest().map { Result.success(it) }.catch { emit(Result.failure(it)) }

    /** The pending card is optional: if its flow fails the rest of the screen still works. */
    private val pending: Flow<SleepEpisode?> = flow {
        emitAll(sleep.observePendingConfirmation().catch { emit(null) })
    }

    private val recentCheckIns: Flow<List<CheckIn>> = flow {
        emitAll(checkIns.observeCheckIns(clock.instant().minus(Duration.ofHours(24))).catch { emit(emptyList()) })
    }

    private val data = combine(
        snapshots,
        pending,
        settings.observeSettings().catch { emit(AppSettings()) },
        recentCheckIns,
    ) { snapshot, pending, appSettings, recent -> Data(snapshot, pending, appSettings, recent) }

    private val local = combine(ticker(), refreshFailed, actionError, dismissedEpisodes) { now, failed, error, dismissed ->
        Local(now, failed, error, dismissed)
    }

    private val sleepData = combine(episodes, preview) { e, p -> e to p }

    val state: StateFlow<ScreenState<EnergyUiModel>> = combine(data, local, sleepData) { d, l, (eps, pv) ->
        mapEnergyState(
            EnergyInputs(
                snapshot = d.snapshot.getOrNull(),
                analysisFailed = d.snapshot.isFailure,
                refreshFailed = l.refreshFailed,
                pending = d.pending,
                settings = d.settings,
                checkIns = d.checkIns,
                now = l.now,
                zone = clock.zone,
                actionError = l.actionError,
                dismissedEpisodes = l.dismissed,
                episodes = eps,
                preview = pv,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ScreenState.Loading)

    init {
        refresh()
    }

    /** Try again after an Error state. */
    fun retry() {
        refreshFailed.value = false
        refresh()
    }

    fun confirm(id: EpisodeId) = act(EnergyActionError.CONFIRM_FAILED) {
        sleep.confirmEpisode(id)
        dismissedEpisodes.update { it + id }
    }

    /** Saves the adjusted night as a user edit. */
    fun saveAdjusted(episode: SleepEpisode, start: Instant, end: Instant) = act(EnergyActionError.SAVE_FAILED) {
        sleep.saveUserEpisode(episode.copy(start = start, end = end, userEdited = true))
        dismissedEpisodes.update { it + episode.id }
    }

    /** A sleep block is being dragged: preview the curve with [start]..[end] without saving. */
    fun dragSleep(episode: SleepEpisode, start: Instant, end: Instant) {
        draft.value = episode.copy(start = start, end = end, userEdited = true)
    }

    /** The drag ended: save the edit, refresh the analysis, then drop the preview. */
    fun commitSleep(episode: SleepEpisode, start: Instant, end: Instant) {
        val edited = episode.copy(start = start, end = end, userEdited = true)
        draft.value = edited
        actionError.value = null
        viewModelScope.launch {
            try {
                sleep.saveUserEpisode(edited)
                analysis.refresh(clock.instant())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                actionError.value = EnergyActionError.SAVE_FAILED
            } finally {
                draft.value = null
            }
        }
    }

    fun checkIn(rating: Int) = act(EnergyActionError.CHECK_IN_FAILED) {
        checkIns.add(CheckIn.EnergyRating(clock.instant(), rating))
    }

    fun dismissActionError() {
        actionError.value = null
    }

    private fun refresh() {
        viewModelScope.launch {
            try {
                analysis.refresh(clock.instant())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                refreshFailed.value = true
            }
        }
    }

    private fun act(failure: EnergyActionError, block: suspend () -> Unit) {
        actionError.value = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                actionError.value = failure
            }
        }
    }

    /** Emits "now" immediately, then at the start of each minute. */
    private fun ticker(): Flow<Instant> = flow {
        while (true) {
            val now = clock.instant()
            emit(now)
            delay(millisToNextMinute(now))
        }
    }

    private data class Data(
        val snapshot: Result<AnalysisSnapshot?>,
        val pending: SleepEpisode?,
        val settings: AppSettings,
        val checkIns: List<CheckIn>,
    )

    private data class Local(
        val now: Instant,
        val refreshFailed: Boolean,
        val actionError: EnergyActionError?,
        val dismissed: Set<EpisodeId>,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
