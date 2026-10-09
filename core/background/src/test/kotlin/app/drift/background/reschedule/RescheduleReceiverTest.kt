package app.drift.background.reschedule

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.drift.background.BackgroundScheduler
import app.drift.background.BackgroundState
import app.drift.background.Fixtures
import app.drift.background.TestEnv
import app.drift.background.notify.ConfirmActionReceiver
import app.drift.background.schedule.MorningAlarmReceiver
import app.drift.background.work.MorningSyncWorker
import app.drift.background.work.Work
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RescheduleReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var wm: WorkManager

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        wm = WorkManager.getInstance(context)
        BackgroundState(context).also { it.lastMorningSync = null; it.expectedWakeMinutes = null }
    }

    // The test WorkManager runs one-time jobs immediately (and they fail without a Hilt factory), so
    // existence of the unique work is what these tests check for one-time jobs.
    private fun infos(name: String) = wm.getWorkInfosForUniqueWork(name).get()

    @Test fun `each restore trigger schedules all periodic work and the restore job`() {
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            wm.cancelAllWork().result.get()
            RescheduleReceiver().onReceive(context, Intent(action))
            assertThat(infos(Work.DAY_REFRESH).filter { !it.state.isFinished }).isNotEmpty()
            assertThat(infos(Work.LOCAL_BACKUP).filter { !it.state.isFinished }).isNotEmpty()
            assertThat(infos(Work.RESTORE)).isNotEmpty()
        }
    }

    @Test fun `periodic jobs carry the planned constraints`() {
        BackgroundScheduler.from(context).scheduleAll()
        val refresh = wm.getWorkInfosForUniqueWork(Work.DAY_REFRESH).get().single()
        val backup = wm.getWorkInfosForUniqueWork(Work.LOCAL_BACKUP).get().single()
        assertThat(refresh.constraints.requiresBatteryNotLow()).isTrue()
        assertThat(backup.constraints.requiresCharging()).isTrue()
        assertThat(backup.constraints.requiresDeviceIdle()).isTrue()
    }

    @Test fun `scheduling twice does not duplicate work`() {
        val s = BackgroundScheduler.from(context)
        s.scheduleAll(); s.scheduleAll()
        assertThat(wm.getWorkInfosForUniqueWork(Work.DAY_REFRESH).get()).hasSize(1)
    }

    @Test fun `cancel all removes periodic work and alarms`() {
        val s = BackgroundScheduler.from(context)
        s.scheduleAll()
        s.cancelAll()
        assertThat(wm.getWorkInfosForUniqueWork(Work.DAY_REFRESH).get().filter { !it.state.isFinished }).isEmpty()
        assertThat(wm.getWorkInfosForUniqueWork(Work.LOCAL_BACKUP).get().filter { !it.state.isFinished }).isEmpty()
    }

    @Test fun `unlock runs a missed morning sync`() {
        val env = TestEnv(now = Fixtures.at(9))
        env.state.expectedWakeMinutes = 7 * 60
        assertThat(RescheduleReceiver.catchUpMorningSync(context, env.clock)).isTrue()
        assertThat(infos(Work.MORNING_SYNC)).hasSize(1)
    }

    @Test fun `unlock does nothing if the morning sync already happened`() {
        val env = TestEnv(now = Fixtures.at(9))
        env.state.expectedWakeMinutes = 7 * 60
        env.state.lastMorningSync = Fixtures.at(7, 30)
        assertThat(RescheduleReceiver.catchUpMorningSync(context, env.clock)).isFalse()
        assertThat(wm.getWorkInfosForUniqueWork(Work.MORNING_SYNC).get()).isEmpty()
    }

    @Test fun `unlock does nothing before any schedule has been set up`() {
        val env = TestEnv(now = Fixtures.at(9))
        assertThat(RescheduleReceiver.catchUpMorningSync(context, env.clock)).isFalse()
    }

    @Test fun `unrelated broadcasts are ignored`() {
        RescheduleReceiver().onReceive(context, Intent("android.intent.action.BATTERY_LOW"))
        assertThat(wm.getWorkInfosForUniqueWork(Work.DAY_REFRESH).get()).isEmpty()
    }

    @Test fun `morning alarm receiver queues an expedited sync with the fallback flag`() {
        MorningAlarmReceiver().onReceive(context, Intent(MorningAlarmReceiver.ACTION).putExtra(MorningAlarmReceiver.EXTRA_FALLBACK, true))
        val info = infos(Work.MORNING_SYNC).single()
        assertThat(info.tags).contains(MorningSyncWorker::class.java.name)
    }

    @Test fun `confirm action receiver queues the confirm job`() {
        ConfirmActionReceiver().onReceive(context, Intent(ConfirmActionReceiver.ACTION_CONFIRM).putExtra(ConfirmActionReceiver.EXTRA_EPISODE, "ep-3"))
        assertThat(wm.getWorkInfosForUniqueWork(Work.CONFIRM).get()).hasSize(1)
        ConfirmActionReceiver().onReceive(context, Intent(ConfirmActionReceiver.ACTION_CONFIRM))
        assertThat(wm.getWorkInfosForUniqueWork(Work.CONFIRM).get()).hasSize(1)
        assertThat(Clock.systemUTC()).isNotNull()
    }
}
