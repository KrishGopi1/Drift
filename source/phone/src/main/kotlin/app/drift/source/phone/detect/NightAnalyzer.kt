package app.drift.source.phone.detect

import app.drift.model.Interval
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Maps a sleep day to its analysis window and runs the detector over it. */
internal object NightAnalyzer {
    private val WINDOW_START = LocalTime.of(18, 0)

    /** The night that ends on [day] is searched between 18:00 the previous evening and 18:00 on [day]. */
    fun windowFor(day: LocalDate, zone: ZoneId): Interval {
        val start = day.minusDays(1).atTime(WINDOW_START).atZone(zone).toInstant()
        val end = day.atTime(WINDOW_START).atZone(zone).toInstant()
        return Interval(start, end)
    }

    /** Analyses [day]'s night using only data up to [now]; later bins are not invented. */
    fun analyze(day: LocalDate, zone: ZoneId, now: Instant, data: PhoneDataSet, params: HmmParams = HmmParams()): DetectedSleep? {
        val full = windowFor(day, zone)
        if (!now.isAfter(full.start)) return null
        val end = if (now < full.end) floorToBin(now, full.start) else full.end
        if (!end.isAfter(full.start)) return null
        val bins = Binner.build(Interval(full.start, end), zone, data)
        return SleepDetector.detect(bins, data.screen, params)
    }

    private fun floorToBin(t: Instant, origin: Instant): Instant {
        val binMs = BIN.toMillis()
        val k = (t.toEpochMilli() - origin.toEpochMilli()) / binMs
        return origin.plusMillis(k * binMs)
    }
}
