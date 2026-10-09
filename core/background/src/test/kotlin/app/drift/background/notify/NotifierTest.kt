package app.drift.background.notify

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.background.Fixtures
import app.drift.background.TestEnv
import app.drift.model.repository.ReminderKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NotifierTest {
    @Test fun `times follow the clock preference`() {
        val env = TestEnv()
        val e = Fixtures.episode()
        assertThat(NotificationContent.confirmation(env.context, e, Fixtures.zone, true).body).isEqualTo("Looks like you slept 23:40 to 07:05")
        val twelve = NotificationContent.confirmation(env.context, e, Fixtures.zone, false).body
        assertThat(twelve).contains("11:40")
        assertThat(twelve).contains("7:05")
    }

    @Test fun `posting creates the reminder and confirmation channels`() {
        val env = TestEnv()
        env.notifier.postReminder(ReminderKind.MORNING_LIGHT)
        val nm = env.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertThat(nm.notificationChannels.map { it.id }).containsAtLeast(NotificationChannels.REMINDERS, NotificationChannels.SLEEP_CONFIRMATION)
    }

    @Test fun `confirmation carries a confirm action and every reminder kind has text`() {
        val env = TestEnv()
        env.notifier.postSleepConfirmation(Fixtures.episode(), Fixtures.zone, true)
        val n = env.posted().single()
        assertThat(n.actions.map { it.title.toString() }).contains("Confirm")
        ReminderKind.entries.forEach {
            val t = NotificationContent.reminder(env.context, it)
            assertThat(t.title).isNotEmpty(); assertThat(t.body).isNotEmpty()
        }
    }

    @Test fun `denied permission posts nothing`() {
        val env = TestEnv(grantNotifications = false)
        assertThat(env.notifier.canPost()).isFalse()
        assertThat(env.notifier.postReminder(ReminderKind.WIND_DOWN)).isFalse()
        assertThat(env.posted()).isEmpty()
    }
}
