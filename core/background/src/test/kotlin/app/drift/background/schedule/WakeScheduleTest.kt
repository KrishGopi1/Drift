package app.drift.background.schedule

import app.drift.background.Fixtures
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class WakeScheduleTest {
    private val utc = ZoneId.of("UTC")
    private val wake = LocalTime.of(7, 0)

    @Test fun `next trigger is wake plus 20 minutes today when still ahead`() {
        val now = Instant.parse("2026-03-04T03:00:00Z")
        assertThat(WakeSchedule.nextTrigger(wake, WakeSchedule.SYNC_DELAY, now, utc)).isEqualTo(Instant.parse("2026-03-04T07:20:00Z"))
    }

    @Test fun `next trigger rolls to tomorrow once passed`() {
        val now = Instant.parse("2026-03-04T07:21:00Z")
        assertThat(WakeSchedule.nextTrigger(wake, WakeSchedule.SYNC_DELAY, now, utc)).isEqualTo(Instant.parse("2026-03-05T07:20:00Z"))
    }

    @Test fun `trigger is strictly after now`() {
        val now = Instant.parse("2026-03-04T07:20:00Z")
        assertThat(WakeSchedule.nextTrigger(wake, WakeSchedule.SYNC_DELAY, now, utc)).isEqualTo(Instant.parse("2026-03-05T07:20:00Z"))
    }

    @Test fun `trigger follows the local zone across a time change`() {
        val ny = ZoneId.of("America/New_York")
        // 2026-03-08 is the spring-forward day: 07:00 local is UTC-4, not UTC-5.
        val now = Instant.parse("2026-03-08T00:00:00Z")
        assertThat(WakeSchedule.nextTrigger(wake, WakeSchedule.SYNC_DELAY, now, ny)).isEqualTo(Instant.parse("2026-03-08T11:20:00Z"))
    }

    @Test fun `last trigger is the most recent one at or before now`() {
        val now = Instant.parse("2026-03-04T12:00:00Z")
        assertThat(WakeSchedule.lastTrigger(wake, WakeSchedule.SYNC_DELAY, now, utc)).isEqualTo(Instant.parse("2026-03-04T07:20:00Z"))
        val early = Instant.parse("2026-03-04T05:00:00Z")
        assertThat(WakeSchedule.lastTrigger(wake, WakeSchedule.SYNC_DELAY, early, utc)).isEqualTo(Instant.parse("2026-03-03T07:20:00Z"))
    }

    @Test fun `missed when no sync happened since the last trigger`() {
        val now = Instant.parse("2026-03-04T09:00:00Z")
        assertThat(WakeSchedule.isMissed(wake, null, now, utc)).isTrue()
        assertThat(WakeSchedule.isMissed(wake, Instant.parse("2026-03-03T07:30:00Z"), now, utc)).isTrue()
        assertThat(WakeSchedule.isMissed(wake, Instant.parse("2026-03-04T07:25:00Z"), now, utc)).isFalse()
    }

    @Test fun `not missed before the first trigger of the day if yesterday was synced`() {
        val now = Instant.parse("2026-03-04T06:00:00Z")
        assertThat(WakeSchedule.isMissed(wake, Instant.parse("2026-03-03T07:30:00Z"), now, utc)).isFalse()
    }

    @Test fun `a very old miss is not caught up at unlock`() {
        val now = Instant.parse("2026-03-04T23:00:00Z") // 15 h20 after the trigger
        assertThat(WakeSchedule.isMissed(wake, null, now, utc)).isFalse()
    }

    @Test fun `expected wake prefers the snapshot then the profile then the default`() {
        val snap = Fixtures.snapshot(wake = Instant.parse("2026-03-05T06:45:30Z"))
        assertThat(WakeSchedule.expectedWake(snap, LocalTime.of(9, 0), utc)).isEqualTo(LocalTime.of(6, 45))
        assertThat(WakeSchedule.expectedWake(null, LocalTime.of(9, 0), utc)).isEqualTo(LocalTime.of(9, 0))
        assertThat(WakeSchedule.expectedWake(null, null, utc)).isEqualTo(WakeSchedule.DEFAULT_WAKE)
        assertThat(WakeSchedule.SYNC_DELAY).isEqualTo(Duration.ofMinutes(20))
    }
}
