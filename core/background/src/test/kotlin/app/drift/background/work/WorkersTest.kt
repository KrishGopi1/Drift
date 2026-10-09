package app.drift.background.work

import android.app.Notification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.drift.background.Fixtures
import app.drift.background.Fixtures.at
import app.drift.background.TestEnv
import app.drift.background.notify.Notifier
import app.drift.model.repository.ReminderKind
import app.drift.model.repository.SyncReason
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WorkersTest {
    private inline fun <reified W : ListenableWorker> TestEnv.run(input: Data = Data.EMPTY, attempt: Int = 0): ListenableWorker.Result =
        runBlocking {
            TestListenableWorkerBuilder<W>(context).setWorkerFactory(factory).setInputData(input).setRunAttemptCount(attempt).build()
                .let { (it as androidx.work.CoroutineWorker).doWork() }
        }

    private fun TestEnv.titles() = posted().map { it.extras.getString(Notification.EXTRA_TITLE) }

    // --- MorningSyncWorker -------------------------------------------------------------------------------

    @Test fun `morning sync runs sync then refresh then offers confirmation and updates the widget`() {
        val env = TestEnv()
        env.sleep.pending.value = Fixtures.episode()
        val result = env.run<MorningSyncWorker>()
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(env.sources.syncs).containsExactly(SyncReason.MORNING)
        assertThat(env.analysis.refreshCount).isEqualTo(1)
        assertThat(env.titles()).contains("Confirm your sleep")
        val body = env.posted().single { it.extras.getString(Notification.EXTRA_TITLE) == "Confirm your sleep" }.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertThat(body).startsWith("Looks like you slept")
        assertThat(env.widget.updates).isEqualTo(1)
        assertThat(env.state.lastMorningSync).isEqualTo(Fixtures.noon)
    }

    @Test fun `morning sync re-arms reminders and tomorrow's morning alarm`() {
        val env = TestEnv()
        env.run<MorningSyncWorker>()
        // 5 default reminders + primary and fallback morning alarms
        assertThat(env.scheduled()).hasSize(7)
        assertThat(env.state.expectedWakeMinutes).isEqualTo(7 * 60)
    }

    @Test fun `no confirmation notification when nothing is pending`() {
        val env = TestEnv()
        env.run<MorningSyncWorker>()
        assertThat(env.titles()).doesNotContain("Confirm your sleep")
    }

    @Test fun `confirmation is skipped when the reminder is turned off`() {
        val env = TestEnv()
        env.settings.settings.value = env.settings.settings.value.copy(reminders = mapOf(ReminderKind.CONFIRM_SLEEP to false))
        env.sleep.pending.value = Fixtures.episode()
        env.run<MorningSyncWorker>()
        assertThat(env.posted()).isEmpty()
    }

    @Test fun `the same night does not alert twice`() {
        val env = TestEnv()
        env.sleep.pending.value = Fixtures.episode()
        env.run<MorningSyncWorker>()
        env.notificationManager.cancelAll()
        env.run<MorningSyncWorker>()
        assertThat(env.posted()).isEmpty()
    }

    @Test fun `without notification permission it works and posts nothing`() {
        val env = TestEnv(grantNotifications = false)
        env.sleep.pending.value = Fixtures.episode()
        assertThat(env.run<MorningSyncWorker>()).isEqualTo(ListenableWorker.Result.success())
        assertThat(env.posted()).isEmpty()
        assertThat(env.widget.updates).isEqualTo(1)
    }

    @Test fun `a failing source retries then still recomputes`() {
        val env = TestEnv()
        env.sources.syncFails = true
        assertThat(env.run<MorningSyncWorker>()).isEqualTo(ListenableWorker.Result.retry())
        assertThat(env.analysis.refreshCount).isEqualTo(1)
        assertThat(env.state.lastMorningSync).isNull()
        assertThat(env.run<MorningSyncWorker>(attempt = 3)).isEqualTo(ListenableWorker.Result.success())
    }

    @Test fun `engine failure retries and eventually fails`() {
        val env = TestEnv()
        env.analysis.refreshFails = true
        assertThat(env.run<MorningSyncWorker>()).isEqualTo(ListenableWorker.Result.retry())
        assertThat(env.run<MorningSyncWorker>(attempt = 3)).isEqualTo(ListenableWorker.Result.failure())
    }

    @Test fun `the late fallback does nothing if the primary sync already ran`() {
        val env = TestEnv(now = at(10))
        env.state.expectedWakeMinutes = 7 * 60
        env.state.lastMorningSync = at(7, 25)
        val input = Data.Builder().putBoolean(Work.KEY_FALLBACK, true).build()
        assertThat(env.run<MorningSyncWorker>(input)).isEqualTo(ListenableWorker.Result.success())
        assertThat(env.sources.syncs).isEmpty()
    }

    @Test fun `the late fallback syncs if the primary never ran`() {
        val env = TestEnv(now = at(10))
        env.state.expectedWakeMinutes = 7 * 60
        env.state.lastMorningSync = at(7, 25, -1)
        env.run<MorningSyncWorker>(Data.Builder().putBoolean(Work.KEY_FALLBACK, true).build())
        assertThat(env.sources.syncs).hasSize(1)
    }

    // --- DayRefreshWorker --------------------------------------------------------------------------------

    @Test fun `day refresh recomputes reschedules and updates the widget`() {
        val env = TestEnv()
        assertThat(env.run<DayRefreshWorker>()).isEqualTo(ListenableWorker.Result.success())
        assertThat(env.analysis.refreshCount).isEqualTo(1)
        assertThat(env.widget.updates).isEqualTo(1)
        assertThat(env.scheduled()).hasSize(5)
    }

    @Test fun `day refresh failure retries a few times then waits for the next period`() {
        val env = TestEnv()
        env.analysis.refreshFails = true
        assertThat(env.run<DayRefreshWorker>()).isEqualTo(ListenableWorker.Result.retry())
        assertThat(env.run<DayRefreshWorker>(attempt = 3)).isEqualTo(ListenableWorker.Result.success())
        assertThat(env.widget.updates).isEqualTo(0)
    }

    // --- LocalBackupWorker -------------------------------------------------------------------------------

    @Test fun `backup worker creates one local backup`() {
        val env = TestEnv()
        assertThat(env.run<LocalBackupWorker>()).isEqualTo(ListenableWorker.Result.success())
        assertThat(env.backup.created).isEqualTo(1)
    }

    @Test fun `backup failure retries then fails`() {
        val env = TestEnv()
        env.backup.fails = true
        assertThat(env.run<LocalBackupWorker>()).isEqualTo(ListenableWorker.Result.retry())
        assertThat(env.run<LocalBackupWorker>(attempt = 3)).isEqualTo(ListenableWorker.Result.failure())
    }

    // --- Reminder and confirm workers ---------------------------------------------------------------------

    private fun kindInput(kind: ReminderKind) = Data.Builder().putString(Work.KEY_KIND, kind.name).build()

    @Test fun `reminder worker posts the matching notification`() {
        val env = TestEnv()
        env.run<ReminderNotifyWorker>(kindInput(ReminderKind.WIND_DOWN))
        assertThat(env.titles()).containsExactly("Time to wind down")
        assertThat(env.posted().single().channelId ?: env.posted().single().extras.getString("channel")).isNotNull()
    }

    @Test fun `reminder worker respects a reminder that was turned off after scheduling`() {
        val env = TestEnv()
        env.settings.settings.value = env.settings.settings.value.copy(reminders = mapOf(ReminderKind.WIND_DOWN to false))
        env.run<ReminderNotifyWorker>(kindInput(ReminderKind.WIND_DOWN))
        assertThat(env.posted()).isEmpty()
    }

    @Test fun `confirm reminder only fires while a confirmation is pending`() {
        val env = TestEnv()
        env.run<ReminderNotifyWorker>(kindInput(ReminderKind.CONFIRM_SLEEP))
        assertThat(env.posted()).isEmpty()
        env.sleep.pending.value = Fixtures.episode()
        env.run<ReminderNotifyWorker>(kindInput(ReminderKind.CONFIRM_SLEEP))
        assertThat(env.titles()).containsExactly("Confirm your sleep")
    }

    @Test fun `reminder worker without a kind fails`() {
        assertThat(TestEnv().run<ReminderNotifyWorker>()).isEqualTo(ListenableWorker.Result.failure())
    }

    @Test fun `confirm action marks the episode confirmed clears the card and refreshes`() {
        val env = TestEnv()
        env.sleep.pending.value = Fixtures.episode("ep-9")
        env.notifier.postSleepConfirmation(Fixtures.episode("ep-9"), Fixtures.zone, true)
        assertThat(env.posted()).hasSize(1)
        val result = env.run<ConfirmSleepWorker>(Data.Builder().putString(Work.KEY_EPISODE, "ep-9").build())
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(env.sleep.confirmed.map { it.value }).containsExactly("ep-9")
        assertThat(env.posted()).isEmpty()
        assertThat(env.analysis.refreshCount).isEqualTo(1)
        assertThat(env.widget.updates).isEqualTo(1)
    }

    @Test fun `confirm failure retries`() {
        val env = TestEnv()
        env.sleep.confirmFails = true
        assertThat(env.run<ConfirmSleepWorker>(Data.Builder().putString(Work.KEY_EPISODE, "x").build())).isEqualTo(ListenableWorker.Result.retry())
        assertThat(env.run<ConfirmSleepWorker>()).isEqualTo(ListenableWorker.Result.failure())
    }

    // --- RestoreWorker -----------------------------------------------------------------------------------

    @Test fun `restore re-arms every alarm from the stored snapshot without recomputing`() {
        val env = TestEnv()
        assertThat(env.run<RestoreWorker>()).isEqualTo(ListenableWorker.Result.success())
        assertThat(env.scheduled()).hasSize(7)
        assertThat(env.analysis.refreshCount).isEqualTo(0)
        assertThat(env.widget.updates).isEqualTo(1)
    }

    @Test fun `restore with no snapshot still arms the morning sync`() {
        val env = TestEnv()
        env.analysis.snapshot = null
        env.run<RestoreWorker>()
        assertThat(env.scheduled()).hasSize(2)
        assertThat(env.state.expectedWakeMinutes).isEqualTo(8 * 60)
    }

    @Test fun `foreground notice for old expedited jobs uses the quiet sync channel`() {
        val env = TestEnv()
        val info = syncForegroundInfo(env.context, 1)
        assertThat(info.notification.channelId).isEqualTo(app.drift.background.notify.NotificationChannels.SYNC)
        assertThat(Notifier.reminderId(ReminderKind.WIND_DOWN)).isNotEqualTo(Notifier.ID_CONFIRMATION)
        assertThat(Duration.ofMinutes(20)).isNotNull()
    }
}
