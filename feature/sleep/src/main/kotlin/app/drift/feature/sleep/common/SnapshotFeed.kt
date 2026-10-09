package app.drift.feature.sleep.common

import app.drift.feature.sleep.di.SleepTexts
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.repository.AnalysisRepository
import app.drift.model.repository.SettingsRepository
import app.drift.model.repository.SourceRepository
import app.drift.model.ui.InsufficientReason
import app.drift.model.ui.ScreenState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The latest snapshot plus the user's clock setting, or why there is none. */
internal sealed interface SnapshotFeed {
    data class Data(val snapshot: AnalysisSnapshot, val use24hSetting: Boolean?) : SnapshotFeed
    data class NoSnapshot(val problem: ScreenState.SourceProblem?) : SnapshotFeed
}

/**
 * Combines analysis, sources and settings. With no snapshot yet, a source that is enabled and has a fix
 * turns into a SourceProblem state; otherwise the screen is Insufficient (no nights yet).
 */
internal fun snapshotFeed(
    analysis: AnalysisRepository,
    sources: SourceRepository,
    settings: SettingsRepository,
    texts: SleepTexts,
): Flow<SnapshotFeed> = combine(analysis.observeLatest(), sources.observeSources(), settings.observeSettings()) { snapshot, infos, prefs ->
    if (snapshot != null) {
        SnapshotFeed.Data(snapshot, prefs.use24HourClock)
    } else {
        val broken = infos.firstOrNull { it.enabled && it.fix != null }
        SnapshotFeed.NoSnapshot(broken?.let { ScreenState.SourceProblem(it.fix!!, texts.sourceProblem(it.displayName)) })
    }
}

internal fun SnapshotFeed.NoSnapshot.toState(daysNeeded: Int): ScreenState<Nothing> =
    problem ?: ScreenState.Insufficient(InsufficientReason.NO_NIGHTS_YET, daysNeeded)

/** Nights of data before the Sleep screen shows personal debt and regularity (plan 4.9: personal figures begin at 5). */
internal const val MIN_NIGHTS = 5
