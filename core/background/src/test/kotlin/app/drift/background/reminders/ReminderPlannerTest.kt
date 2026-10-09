package app.drift.background.reminders

import app.drift.background.Fixtures
import app.drift.background.Fixtures.at
import app.drift.model.repository.AppSettings
import app.drift.model.repository.ReminderKind
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ReminderPlannerTest {
    private val zone = Fixtures.zone
    private val now = Fixtures.at(12)
    private val allOn = AppSettings(reminders = ReminderKind.entries.associateWith { true })

    private fun plan(settings: AppSettings = AppSettings(), snapshot: app.drift.model.engine.AnalysisSnapshot = Fixtures.snapshot(), at: Instant = now) =
        ReminderPlanner.plan(snapshot, settings, at, zone).associate { it.kind to it.at }

    @Test fun `default settings schedule everything except the energy check-in`() {
        val p = plan()
        assertThat(p.keys).containsExactly(
            ReminderKind.WIND_DOWN, ReminderKind.MELATONIN_WINDOW, ReminderKind.CAFFEINE_CUTOFF,
            ReminderKind.MORNING_LIGHT, ReminderKind.CONFIRM_SLEEP,
        )
    }

    @Test fun `each reminder lands on the matching snapshot instant`() {
        val p = plan(allOn)
        assertThat(p[ReminderKind.WIND_DOWN]).isEqualTo(at(21, 30))
        assertThat(p[ReminderKind.MELATONIN_WINDOW]).isEqualTo(at(22, 15))
        assertThat(p[ReminderKind.CAFFEINE_CUTOFF]).isEqualTo(at(14, 0))
        assertThat(p[ReminderKind.MORNING_LIGHT]).isEqualTo(at(7, 30, 1))
        // wake 07:00 tomorrow + 45 minutes
        assertThat(p[ReminderKind.CONFIRM_SLEEP]).isEqualTo(at(7, 45, 1))
        // the afternoon dip's extremum on the same day
        assertThat(p[ReminderKind.ENERGY_CHECK_IN]).isEqualTo(at(14, 0))
    }

    @Test fun `disabled kinds are not planned`() {
        val settings = AppSettings(reminders = ReminderKind.entries.associateWith { it == ReminderKind.WIND_DOWN })
        assertThat(plan(settings).keys).containsExactly(ReminderKind.WIND_DOWN)
        assertThat(plan(AppSettings(reminders = emptyMap()))).isEmpty()
    }

    @Test fun `instants in the past are dropped`() {
        val late = plan(allOn, at = at(21, 45))
        assertThat(late).doesNotContainKey(ReminderKind.WIND_DOWN)
        assertThat(late).doesNotContainKey(ReminderKind.CAFFEINE_CUTOFF)
        assertThat(late).containsKey(ReminderKind.MELATONIN_WINDOW)
    }

    @Test fun `anything beyond the horizon is dropped`() {
        val snap = Fixtures.snapshot(windDown = now.plus(ReminderPlanner.HORIZON).plusSeconds(60))
        assertThat(plan(allOn, snap)).doesNotContainKey(ReminderKind.WIND_DOWN)
    }

    @Test fun `missing circadian estimate skips wind-down and melatonin`() {
        val snap = Fixtures.snapshot(windDown = null, melatoninStart = null)
        val p = plan(allOn, snap)
        assertThat(p).doesNotContainKey(ReminderKind.WIND_DOWN)
        assertThat(p).doesNotContainKey(ReminderKind.MELATONIN_WINDOW)
    }

    @Test fun `confirm time rolls to the next day once this morning has passed`() {
        val p = plan(allOn, at = at(8, 0))
        assertThat(p[ReminderKind.CONFIRM_SLEEP]).isEqualTo(at(7, 45, 1))
    }

    @Test fun `habits without a reminder kind are ignored and the earliest future card wins`() {
        val snap = Fixtures.snapshot(
            habits = listOf(
                app.drift.model.engine.HabitCard(app.drift.model.engine.HabitKind.CAFFEINE_CUTOFF, app.drift.model.Interval(at(16), at(20)), "b"),
                app.drift.model.engine.HabitCard(app.drift.model.engine.HabitKind.CAFFEINE_CUTOFF, app.drift.model.Interval(at(14), at(20)), "a"),
                app.drift.model.engine.HabitCard(app.drift.model.engine.HabitKind.DIM_LIGHTS, app.drift.model.Interval(at(20), at(21)), "c"),
            ),
        )
        assertThat(plan(allOn, snap)[ReminderKind.CAFFEINE_CUTOFF]).isEqualTo(at(14))
    }

    @Test fun `window length is ten minutes`() {
        assertThat(ReminderPlanner.WINDOW).isEqualTo(Duration.ofMinutes(10))
    }

    @Test fun `plan is sorted by time`() {
        val times = ReminderPlanner.plan(Fixtures.snapshot(), allOn, now, zone).map { it.at }
        assertThat(times).isInOrder()
    }
}
