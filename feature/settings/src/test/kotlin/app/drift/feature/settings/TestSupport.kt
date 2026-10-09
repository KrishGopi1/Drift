package app.drift.feature.settings

import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import app.drift.model.SourceId
import app.drift.model.repository.SourceFix
import app.drift.model.repository.SourceInfo
import app.drift.model.source.SourceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(UnconfinedTestDispatcher())
    override fun finished(description: Description) = Dispatchers.resetMain()
}

object Sources {
    val lastSync: Instant = Instant.parse("2026-10-14T06:00:00Z")

    fun healthConnect(
        status: SourceStatus = SourceStatus.Available,
        enabled: Boolean = true,
        fix: SourceFix? = null,
    ) = SourceInfo(SourceId.HEALTH_CONNECT, "Health Connect", status, enabled, lastSync, null, fix)

    fun sleepAsAndroid(
        status: SourceStatus = SourceStatus.Available,
        enabled: Boolean = false,
        fix: SourceFix? = null,
    ) = SourceInfo(SourceId.SLEEP_AS_ANDROID, "Sleep as Android", status, enabled, null, null, fix)

    fun phone(
        status: SourceStatus = SourceStatus.Available,
        enabled: Boolean = true,
        fix: SourceFix? = null,
    ) = SourceInfo(SourceId.PHONE, "Phone detection", status, enabled, lastSync, null, fix)

    val allGood = listOf(healthConnect(), sleepAsAndroid(), phone())
}

/** Records launches and answers each one immediately with [result] (null = dismissed). */
class RecordingRegistryOwner(var result: (Any?) -> Any? = { null }) : ActivityResultRegistryOwner {
    val launched = mutableListOf<Pair<ActivityResultContract<*, *>, Any?>>()

    override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
        override fun <I : Any?, O : Any?> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            launched += contract to input
            val out = result(input)
            if (out != null) dispatchResult(requestCode, out)
        }
    }
}
