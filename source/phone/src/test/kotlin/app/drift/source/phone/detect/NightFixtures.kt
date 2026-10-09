package app.drift.source.phone.detect

import app.drift.source.phone.ChargeChange
import app.drift.source.phone.ClassifySample
import app.drift.source.phone.ScreenChange
import app.drift.source.phone.SleepSegment
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Random

/** Ground truth for one synthetic night. */
internal data class TruthNight(val day: LocalDate, val onset: Instant, val wake: Instant)

internal enum class Sensors { SCREEN_ONLY, SCREEN_AND_API, API_ONLY, SCREEN_AND_CHARGING }

/** Seeded synthetic phone logs for a run of nights. Pure and deterministic. */
internal class SyntheticUser(
    private val zone: ZoneId,
    seed: Long,
    private val typicalOnset: LocalTime = LocalTime.of(23, 20),
    private val typicalWake: LocalTime = LocalTime.of(7, 10),
    private val nightShift: Boolean = false,
) {
    private val rnd = Random(seed)

    class Generated(val truth: List<TruthNight>, val data: PhoneDataSet)

    fun generate(firstDay: LocalDate, nights: Int, sensors: Sensors, daytimeGapHours: Int = 0): Generated {
        val screen = mutableListOf<ScreenChange>()
        val segments = mutableListOf<SleepSegment>()
        val classify = mutableListOf<ClassifySample>()
        val charge = mutableListOf<ChargeChange>()
        val truth = mutableListOf<TruthNight>()

        var prevWake: Instant? = null
        for (i in 0 until nights) {
            val day = firstDay.plusDays(i.toLong())
            val onset: Instant
            val wake: Instant
            if (nightShift) {
                onset = day.atTime(LocalTime.of(8, 30)).plusMinutes(rnd.nextInt(91) - 45L).atZone(zone).toInstant()
                wake = onset.plus(Duration.ofMinutes(420L + rnd.nextInt(61)))
            } else {
                val onsetTod = typicalOnset.plusMinutes(rnd.nextInt(91) - 45L)
                val wakeTod = typicalWake.plusMinutes(rnd.nextInt(61) - 30L)
                onset = (if (onsetTod.isBefore(LocalTime.NOON)) day else day.minusDays(1)).atTime(onsetTod).atZone(zone).toInstant()
                wake = day.atTime(wakeTod).atZone(zone).toInstant()
            }
            truth += TruthNight(day, onset, wake)

            if (sensors != Sensors.API_ONLY) {
                val awakeFrom = prevWake ?: onset.minus(Duration.ofHours(16))
                val lastUse = onset.minus(Duration.ofMinutes(4L + rnd.nextInt(14)))
                // Waking interaction (alarm dismissal) then short sessions through the day.
                screen += ScreenChange(awakeFrom.plus(Duration.ofMinutes(rnd.nextInt(4).toLong())), true)
                screen += ScreenChange(awakeFrom.plus(Duration.ofMinutes(6)), false)
                val gapStart = awakeFrom.plus(Duration.ofHours(2))
                val gapEnd = gapStart.plus(Duration.ofHours(daytimeGapHours.toLong()))
                var t = awakeFrom.plus(Duration.ofMinutes(20))
                while (t < lastUse.minus(Duration.ofMinutes(30))) {
                    if (daytimeGapHours == 0 || t < gapStart || t >= gapEnd) {
                        screen += ScreenChange(t, true)
                        screen += ScreenChange(t.plus(Duration.ofMinutes(1L + rnd.nextInt(6))), false)
                    }
                    t = t.plus(Duration.ofMinutes(8L + rnd.nextInt(35)))
                }
                screen += ScreenChange(lastUse.minus(Duration.ofMinutes(10)), true)
                screen += ScreenChange(lastUse, false)
                if (rnd.nextInt(100) < 40) {
                    val at = onset.plus(Duration.ofMinutes(90L + rnd.nextInt(120)))
                    screen += ScreenChange(at, true)
                    screen += ScreenChange(at.plusSeconds(60), false)
                }
            }
            prevWake = wake.plus(Duration.ofMinutes(rnd.nextInt(4).toLong()))
            if (sensors != Sensors.API_ONLY) screen += ScreenChange(prevWake, true)

            if (sensors == Sensors.SCREEN_AND_API || sensors == Sensors.API_ONLY) {
                if (rnd.nextInt(100) < 70) {
                    segments += SleepSegment(onset.plus(Duration.ofMinutes(rnd.nextInt(21) - 10L)), wake.plus(Duration.ofMinutes(rnd.nextInt(21) - 10L)), 0)
                }
                var c = onset.minus(Duration.ofHours(2))
                while (c < wake.plus(Duration.ofHours(2))) {
                    val asleep = c >= onset && c < wake
                    val conf = if (asleep) 78 + rnd.nextInt(20) else 3 + rnd.nextInt(25)
                    classify += ClassifySample(c, conf, if (asleep) rnd.nextInt(5) else 30 + rnd.nextInt(60), 0)
                    c = c.plus(Duration.ofMinutes(10))
                }
            }

            if (sensors == Sensors.SCREEN_AND_CHARGING) {
                charge += ChargeChange(onset.minus(Duration.ofMinutes(20)), true)
                charge += ChargeChange(wake.plus(Duration.ofMinutes(15)), false)
            }
        }

        // Normalise events: strictly ordered, alternating states.
        val cleaned = mutableListOf<ScreenChange>()
        for (e in screen.sortedBy { it.at }) if (cleaned.lastOrNull()?.interactive != e.interactive) cleaned += e
        val screenTimeline = if (sensors == Sensors.API_ONLY) null else ScreenTimeline(cleaned)
        return Generated(
            truth,
            PhoneDataSet(
                screen = screenTimeline,
                segments = segments,
                classify = classify,
                charging = ChargeTimeline(charge),
                steps = emptyList(),
                sleepApiActive = sensors == Sensors.SCREEN_AND_API || sensors == Sensors.API_ONLY,
            ),
        )
    }
}
