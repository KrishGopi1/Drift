package app.drift.source.sleepasandroid

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SleepAsAndroidBroadcastReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val received = ArrayList<SleepAsAndroidEvent>()
    private val receiver = SleepAsAndroidBroadcastReceiver().also { r -> r.callbackResolver = { SleepAsAndroidSyncCallback { received += it } } }

    @Before fun reset() = SleepAsAndroidBroadcastReceiver.resetDebounceForTests()

    @Test fun trackingStoppedTriggersCallback() {
        receiver.onReceive(context, Intent(SleepAsAndroidActions.TRACKING_STOPPED))
        assertThat(received).containsExactly(SleepAsAndroidEvent.TRACKING_STOPPED)
    }

    @Test fun alarmDismissTriggersCallbackAndUnknownActionsAreIgnored() {
        receiver.onReceive(context, Intent(SleepAsAndroidActions.ALARM_DISMISSED))
        receiver.onReceive(context, Intent("com.example.other"))
        receiver.onReceive(context, Intent())
        assertThat(received).containsExactly(SleepAsAndroidEvent.ALARM_DISMISSED)
    }

    @Test fun rapidRepeatsAreDebounced() {
        repeat(3) { receiver.onReceive(context, Intent(SleepAsAndroidActions.TRACKING_STOPPED)) }
        assertThat(received).hasSize(1)
    }

    @Test fun missingCallbackIsHarmless() {
        val bare = SleepAsAndroidBroadcastReceiver().also { it.callbackResolver = { null } }
        bare.onReceive(context, Intent(SleepAsAndroidActions.TRACKING_STOPPED))
    }

    @Test fun manifestRegistersTheReceiverForDocumentedActions() {
        val info = context.packageManager.queryBroadcastReceivers(Intent(SleepAsAndroidActions.TRACKING_STOPPED), 0)
        // Robolectric merges the library manifest into the test application manifest.
        assertThat(info.map { it.activityInfo.name }).contains(SleepAsAndroidBroadcastReceiver::class.java.name)
    }

    @Test fun runtimeRegistrationDeliversBroadcasts() {
        val registered = SleepAsAndroidBroadcastReceiver.register(context)
        registered.callbackResolver = { SleepAsAndroidSyncCallback { received += it } }
        context.sendBroadcast(Intent(SleepAsAndroidActions.ALARM_DISMISSED))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertThat(received).containsExactly(SleepAsAndroidEvent.ALARM_DISMISSED)
        context.unregisterReceiver(registered)
    }
}
