package app.drift.background.reminders

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import app.drift.background.Fixtures
import app.drift.background.Fixtures.at
import app.drift.background.TestEnv
import app.drift.model.repository.AppSettings
import app.drift.model.repository.ReminderKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReminderSchedulerTest {
    @Test fun `arms one windowed inexact alarm per planned reminder`() {
        val env = TestEnv()
        val plan = env.reminders.reschedule(Fixtures.snapshot(), AppSettings())
        val alarms = env.scheduled()
        assertThat(alarms).hasSize(plan.size)
        alarms.forEach {
            assertThat(it.windowLengthMs).isEqualTo(Duration.ofMinutes(10).toMillis())
            assertThat(it.type).isEqualTo(android.app.AlarmManager.RTC_WAKEUP)
        }
        assertThat(alarms.map { it.triggerAtMs }).contains(at(21, 30).toEpochMilli())
    }

    @Test fun `rescheduling replaces rather than duplicates`() {
        val env = TestEnv()
        env.reminders.reschedule(Fixtures.snapshot(), AppSettings())
        env.reminders.reschedule(Fixtures.snapshot(windDown = at(21, 45)), AppSettings())
        assertThat(env.scheduled()).hasSize(5)
        assertThat(env.scheduled().map { it.triggerAtMs }).contains(at(21, 45).toEpochMilli())
        assertThat(env.scheduled().map { it.triggerAtMs }).doesNotContain(at(21, 30).toEpochMilli())
    }

    @Test fun `turning a reminder off cancels its alarm`() {
        val env = TestEnv()
        env.reminders.reschedule(Fixtures.snapshot(), AppSettings())
        env.reminders.reschedule(Fixtures.snapshot(), AppSettings(reminders = mapOf(ReminderKind.WIND_DOWN to true)))
        assertThat(env.scheduled()).hasSize(1)
    }

    @Test fun `cancel all clears every reminder alarm`() {
        val env = TestEnv()
        env.reminders.reschedule(Fixtures.snapshot(), AppSettings())
        env.reminders.cancelAll()
        assertThat(env.scheduled()).isEmpty()
    }

    @Test fun `the alarm receiver queues a notification job for a known kind only`() {
        val env = TestEnv()
        WorkManagerTestInitHelper.initializeTestWorkManager(env.context)
        val receiver = ReminderAlarmReceiver()
        receiver.onReceive(env.context, Intent(ReminderAlarmReceiver.ACTION).putExtra(ReminderAlarmReceiver.EXTRA_KIND, "BOGUS"))
        receiver.onReceive(env.context, Intent("something.else").putExtra(ReminderAlarmReceiver.EXTRA_KIND, "WIND_DOWN"))
        val wm = androidx.work.WorkManager.getInstance(env.context)
        assertThat(wm.getWorkInfosForUniqueWork("drift.reminder.WIND_DOWN").get()).isEmpty()
        receiver.onReceive(env.context, Intent(ReminderAlarmReceiver.ACTION).putExtra(ReminderAlarmReceiver.EXTRA_KIND, "WIND_DOWN"))
        assertThat(wm.getWorkInfosForUniqueWork("drift.reminder.WIND_DOWN").get()).hasSize(1)
        assertThat(shadowOf(env.context as android.app.Application)).isNotNull()
    }
}
