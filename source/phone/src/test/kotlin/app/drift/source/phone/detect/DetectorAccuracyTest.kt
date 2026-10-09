package app.drift.source.phone.detect

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DetectorAccuracyTest {
    private val zone = ZoneId.of("Europe/London")
    private val first = LocalDate.of(2026, 3, 2)
    private val nights = 40

    private class Report(val label: String, val onsetErr: List<Long>, val wakeErr: List<Long>, val detected: Int, val total: Int, val meanConfidence: Double) {
        val medOnset get() = median(onsetErr)
        val medWake get() = median(wakeErr)
        override fun toString() = "%-34s detected %2d/%2d  median onset err %3d min  median wake err %3d min  mean confidence %.2f"
            .format(label, detected, total, medOnset, medWake, meanConfidence)
        companion object { fun median(l: List<Long>) = if (l.isEmpty()) -1 else l.sorted()[l.size / 2] }
    }

    private fun evaluate(label: String, sensors: Sensors, seed: Long, shift: Boolean = false, gapHours: Int = 0): Report {
        val user = SyntheticUser(zone, seed, nightShift = shift)
        val g = user.generate(first, nights, sensors, gapHours)
        val onsetErr = mutableListOf<Long>(); val wakeErr = mutableListOf<Long>(); val conf = mutableListOf<Double>()
        var detected = 0
        // Skip the first night: its preceding day has no history of phone use.
        for (t in g.truth.drop(1)) {
            val now = t.wake.plus(Duration.ofMinutes(20))
            val found = NightAnalyzer.analyze(t.day, zone, now, g.data)
            if (found != null) {
                detected++
                onsetErr += Math.abs(Duration.between(t.onset, found.onset).toMinutes())
                wakeErr += Math.abs(Duration.between(t.wake, found.wake).toMinutes())
                conf += found.confidence
            }
        }
        return Report(label, onsetErr, wakeErr, detected, g.truth.size - 1, if (conf.isEmpty()) 0.0 else conf.average())
    }

    @Test fun `median onset and wake error stay within 30 minutes on every fixture`() {
        val reports = listOf(
            evaluate("screen only", Sensors.SCREEN_ONLY, 1),
            evaluate("screen + sleep api", Sensors.SCREEN_AND_API, 2),
            evaluate("sleep api only", Sensors.API_ONLY, 3),
            evaluate("screen + charging", Sensors.SCREEN_AND_CHARGING, 4),
            evaluate("screen only, 8 h daytime gap", Sensors.SCREEN_ONLY, 5, gapHours = 8),
            evaluate("night shift, screen + api", Sensors.SCREEN_AND_API, 6, shift = true),
        )
        reports.forEach { println("DETECTOR ACCURACY: $it") }
        for (r in reports) {
            // Sleep API alone has no screen anchor, so it is allowed to miss a few more nights.
            assertThat(r.detected.toDouble() / r.total).isAtLeast(if (r.label.startsWith("sleep api only")) 0.8 else 0.95)
            assertThat(r.medOnset).isAtMost(30)
            assertThat(r.medWake).isAtMost(30)
        }
    }

    @Test fun `sleep api adds confidence over screen alone`() {
        val screenOnly = evaluate("a", Sensors.SCREEN_ONLY, 11)
        val both = evaluate("b", Sensors.SCREEN_AND_API, 11)
        assertThat(both.meanConfidence).isGreaterThan(screenOnly.meanConfidence)
        assertThat(screenOnly.meanConfidence).isGreaterThan(0.2)
        assertThat(both.meanConfidence).isAtMost(1.0)
    }

    @Test fun `a long daytime gap without phone use is not sleep`() {
        val g = SyntheticUser(zone, 21).generate(first, 6, Sensors.SCREEN_ONLY, daytimeGapHours = 10)
        // Look only at 07:30..18:00 of one day: no corroboration other than silence.
        val day = first.plusDays(3)
        val window = app.drift.model.Interval(day.atTime(7, 30).atZone(zone).toInstant(), day.atTime(18, 0).atZone(zone).toInstant())
        val bins = Binner.build(window, zone, g.data)
        assertThat(SleepDetector.detect(bins, g.data.screen)).isNull()
    }

    @Test fun `silence alone at night with no screen or api data is not reported`() {
        val day = first.plusDays(1)
        val window = NightAnalyzer.windowFor(day, zone)
        val empty = PhoneDataSet(null, emptyList(), emptyList(), ChargeTimeline(emptyList()), emptyList(), sleepApiActive = false)
        assertThat(NightAnalyzer.analyze(day, zone, window.end, empty)).isNull()
    }

    @Test fun `unfinished night is not reported before a wake is observed`() {
        val g = SyntheticUser(zone, 31).generate(first, 3, Sensors.SCREEN_ONLY)
        val t = g.truth[1]
        val midSleep = t.onset.plus(Duration.ofHours(3))
        assertThat(NightAnalyzer.analyze(t.day, zone, midSleep, g.data)).isNull()
    }

    @Test fun `confidence and times are well formed`() {
        val g = SyntheticUser(zone, 41).generate(first, 5, Sensors.SCREEN_AND_API)
        val t = g.truth[2]
        val found = NightAnalyzer.analyze(t.day, zone, t.wake.plus(Duration.ofMinutes(20)), g.data)!!
        assertThat(found.confidence).isIn(com.google.common.collect.Range.closed(0.0, 1.0))
        assertThat(found.wake).isGreaterThan(found.onset)
        assertThat(found.completeness).isIn(com.google.common.collect.Range.closed(0.0, 1.0))
    }
}
