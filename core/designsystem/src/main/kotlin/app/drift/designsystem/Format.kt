package app.drift.designsystem

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure, locale-light formatters shared by components and chart descriptions. */
object DriftFormat {
    /** "7 h 20 min", "45 min", "8 h". Rounds to the nearest minute. */
    fun duration(d: Duration): String {
        val total = (d.seconds / 60.0).roundToInt()
        val sign = if (total < 0) "-" else ""
        val m = abs(total)
        val h = m / 60
        val r = m % 60
        return when {
            h == 0 -> "$sign$r min"
            r == 0 -> "$sign$h h"
            else -> "$sign$h h $r min"
        }
    }

    /** Spoken form for screen readers: "7 hours 20 minutes". */
    fun durationSpoken(d: Duration): String {
        val total = (d.seconds / 60.0).roundToInt()
        val sign = if (total < 0) "minus " else ""
        val m = abs(total)
        val h = m / 60
        val r = m % 60
        fun unit(n: Int, one: String) = if (n == 1) "$n $one" else "$n ${one}s"
        return sign + when {
            h == 0 -> unit(r, "minute")
            r == 0 -> unit(h, "hour")
            else -> unit(h, "hour") + " " + unit(r, "minute")
        }
    }

    /** "+45 min" / "-20 min" / "0 min". */
    fun signedDuration(d: Duration): String {
        val minutes = (d.seconds / 60.0).roundToInt()
        return when {
            minutes > 0 -> "+" + duration(Duration.ofMinutes(minutes.toLong()))
            minutes < 0 -> duration(Duration.ofMinutes(minutes.toLong()))
            else -> "0 min"
        }
    }

    /** Clock time of an instant in [zone]: "07:10" or "7:10 AM". */
    fun clock(at: Instant, zone: ZoneId, use24h: Boolean): String =
        clockMinutes(ZonedDateTime.ofInstant(at, zone).let { it.hour * 60 + it.minute }, use24h)

    /** Clock time from minutes after local midnight (wraps at 24 h). */
    fun clockMinutes(minutesAfterMidnight: Int, use24h: Boolean): String {
        val m = ((minutesAfterMidnight % 1440) + 1440) % 1440
        val h = m / 60
        val mm = m % 60
        return if (use24h) {
            String.format(Locale.US, "%02d:%02d", h, mm)
        } else {
            val suffix = if (h < 12) "AM" else "PM"
            val h12 = if (h % 12 == 0) 12 else h % 12
            String.format(Locale.US, "%d:%02d %s", h12, mm, suffix)
        }
    }

    /** One decimal place, dot separator regardless of device locale for stable test output. */
    fun decimal(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)
}
