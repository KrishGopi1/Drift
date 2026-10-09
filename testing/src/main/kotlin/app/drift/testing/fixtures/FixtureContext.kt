package app.drift.testing.fixtures

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToLong

/**
 * A fixed "now" and zone so screenshots and assertions are stable. Nothing in `:testing` reads the system clock.
 *
 * ```
 * val snap = SnapshotFixtures.full(FixtureContext.IST)
 * ```
 */
data class FixtureContext(val now: Instant, val zone: ZoneId) {
    val clock: Clock get() = Clock.fixed(now, zone)
    val localNow: ZonedDateTime get() = now.atZone(zone)
    val today: LocalDate get() = localNow.toLocalDate()

    companion object {
        /** 2026-10-14 13:00 in Kolkata (a Wednesday). */
        val IST = FixtureContext(Instant.parse("2026-10-14T07:30:00Z"), ZoneId.of("Asia/Kolkata"))

        /** 2026-10-14 10:00 in New York (EDT). */
        val US_EASTERN = FixtureContext(Instant.parse("2026-10-14T14:00:00Z"), ZoneId.of("America/New_York"))

        val ALL: List<FixtureContext> = listOf(IST, US_EASTERN)
    }
}

internal fun hours(h: Double): Duration = Duration.ofMinutes((h * 60).roundToLong())
internal fun Duration.toHoursDouble(): Double = toMinutes() / 60.0
