package app.drift.sources

import app.drift.model.repository.AnalysisRepository
import app.drift.model.repository.SourceRepository
import app.drift.model.repository.SyncReason
import app.drift.source.sleepasandroid.SleepAsAndroidEvent
import app.drift.source.sleepasandroid.SleepAsAndroidSyncCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Syncs and re-runs the engine a few moments after Sleep as Android stops tracking (plan 5.2, path 3). */
@Singleton
class BroadcastSync @Inject constructor(
    private val sources: SourceRepository,
    private val analysis: AnalysisRepository,
    @AppScope private val scope: CoroutineScope,
) : SleepAsAndroidSyncCallback {
    override fun onSleepAsAndroidEvent(event: SleepAsAndroidEvent) {
        scope.launch {
            runCatching {
                sources.sync(SyncReason.SOURCE_BROADCAST)
                analysis.refresh()
            }
        }
    }
}
