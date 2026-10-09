package app.drift.testing.fixtures

import app.drift.model.ConfidenceLevel
import app.drift.model.Interval
import app.drift.model.Provenance
import app.drift.model.engine.DayCurve
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.EnergyPoint
import app.drift.model.engine.EnergySegment
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max

/**
 * Simple three-process model (homeostatic S + circadian C + sleep inertia W) sampled every 5 minutes, used to
 * give fixtures realistic curves without the engine. It is NOT the engine's model; it only has to look right.
 *
 * - S falls during wake (tau 18.2 h) and recovers during sleep (tau 4.2 h).
 * - C is a 24 h cosine peaking 5 h before DLMO plus a 12 h harmonic that carves the post-lunch dip.
 * - W decays with tau 40 min after waking, larger after short sleep.
 */
class EnergyModel(
    val zone: ZoneId,
    sleeps: List<Interval>,
    val dlmoMinutes: Double,
    /** Scales C. Around 0.2 gives a curve too flat for a dip. */
    val circadianScale: Double = 1.0,
) {
    val sleeps: List<Interval> = sleeps.sortedBy { it.start }
    val start: Instant = floor5(this.sleeps.first().start)
    private val count: Int = (Duration.between(start, this.sleeps.last().end).toMinutes() / STEP_MIN).toInt() + 1
    private val levels = DoubleArray(count)
    private val asleepAt = BooleanArray(count)

    init {
        var s = 0.7
        var sleepIdx = 0
        for (i in 0 until count) {
            val t = at(i)
            while (sleepIdx < this.sleeps.size && !t.isBefore(this.sleeps[sleepIdx].end)) sleepIdx++
            val current = this.sleeps.getOrNull(sleepIdx)
            val asleep = current != null && current.contains(t)
            asleepAt[i] = asleep
            s = if (asleep) 1 - (1 - s) * exp(-DT_H / 4.2) else s * exp(-DT_H / 18.2)
            val lastSleep = this.sleeps.getOrNull(if (asleep) sleepIdx - 1 else sleepIdx - 1)
            var w = 0.0
            if (!asleep && lastSleep != null) {
                val sinceH = Duration.between(lastSleep.end, t).toMinutes() / 60.0
                val durH = lastSleep.duration.toMinutes() / 60.0
                val amp = (10 + 4 * max(0.0, 7.5 - durH)).coerceAtMost(24.0)
                w = -amp * exp(-sinceH / (40.0 / 60.0))
            }
            val raw = 40 * s + 30 * circadian(t) * circadianScale + w - (if (asleep) 12 else 0)
            levels[i] = ((raw - 10) * 1.9).coerceIn(0.0, 100.0)
        }
    }

    private fun circadian(t: Instant): Double {
        val local = t.atZone(zone).toLocalTime()
        val minutes = local.toSecondOfDay() / 60.0
        val rel = ((minutes - dlmoMinutes + 720 + 1440) % 1440) - 720 // signed minutes from DLMO
        val h = rel / 60.0
        return cos(2 * PI * (h + 5) / 24) + 0.7 * cos(2 * PI * (h + 12.5) / 12)
    }

    val end: Instant get() = at(count - 1)
    fun at(i: Int): Instant = start.plus(Duration.ofMinutes(i.toLong() * STEP_MIN))
    fun indexOf(t: Instant): Int = (Duration.between(start, t).toMinutes() / STEP_MIN).toInt()
    fun level(i: Int): Double = levels[i]
    fun isAsleep(i: Int): Boolean = asleepAt[i]

    companion object {
        const val STEP_MIN = 5L
        private const val DT_H = STEP_MIN / 60.0
        private fun floor5(t: Instant): Instant = Instant.ofEpochSecond(t.epochSecond - t.epochSecond % (STEP_MIN * 60))
    }
}

/** Builds [DayCurve]s (5-min points, contiguous segments covering the whole local day) from an [EnergyModel]. */
object EnergyCurveFactory {
    private const val DIP_MIN_PROMINENCE = 6.0

    /** The dip segment spans where the curve is within this fraction of the way up from the trough to the neighbouring peak. */
    private const val DIP_EDGE = 0.3
    private val GROGGY_MAX = Duration.ofMinutes(90)
    private val WIND_DOWN_LEAD = Duration.ofMinutes(90)
    private val MEL_BEFORE_BED = Duration.ofMinutes(30)

    fun dayCurve(
        model: EnergyModel,
        date: LocalDate,
        confidence: ConfidenceLevel,
        provenance: Provenance = Provenance.ESTIMATED,
    ): DayCurve {
        val zone = model.zone
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val points = generateSequence(dayStart) { it.plus(Duration.ofMinutes(EnergyModel.STEP_MIN)) }
            .takeWhile { it.isBefore(dayEnd) }
            .map { EnergyPoint(it, model.level(model.indexOf(it))) }
            .toList()

        val all = mutableListOf<EnergySegment>()
        var tooFlat = true
        var sawAwake = false
        val sleeps = model.sleeps
        all += EnergySegment(EnergyPhase.SLEEP, sleeps.first(), null)
        for (k in 0 until sleeps.size - 1) {
            val awake = Interval(sleeps[k].end, sleeps[k + 1].start)
            if (!awake.overlaps(Interval(dayStart, dayEnd))) {
                all += EnergySegment(EnergyPhase.SLEEP, sleeps[k + 1], null)
                continue
            }
            val (segs, flat) = awakeSegments(model, awake)
            all += segs
            all += EnergySegment(EnergyPhase.SLEEP, sleeps[k + 1], null)
            // The day counts as "too flat" only if no awake cycle overlapping it had a dip.
            if (!flat) tooFlat = false
            sawAwake = true
        }
        if (!sawAwake) tooFlat = true

        val day = Interval(dayStart, dayEnd)
        val clipped = all.mapNotNull { seg ->
            val iv = seg.interval.intersect(day) ?: return@mapNotNull null
            if (iv.duration.isZero) null
            else EnergySegment(seg.phase, iv, seg.extremumAt?.takeIf { day.contains(it) })
        }.sortedBy { it.interval.start }
        return DayCurve(date, points, clipped, tooFlat, confidence, provenance)
    }

    /** Awake stretch -> GROGGINESS, MORNING_PEAK, [AFTERNOON_DIP], EVENING_PEAK, WIND_DOWN, MELATONIN_WINDOW. */
    private fun awakeSegments(model: EnergyModel, awake: Interval): Pair<List<EnergySegment>, Boolean> {
        require(awake.duration >= Duration.ofHours(8)) { "Awake stretch too short for a fixture curve: ${awake.duration}" }
        val wake = awake.start
        val bed = awake.end
        val dlmo = dlmoAtOrAfter(model, wake)
        val melStart = minOf(dlmo, bed.minus(MEL_BEFORE_BED))
        val windDown = melStart.minus(WIND_DOWN_LEAD)
        val groggyEnd = wake.plus(minOf(GROGGY_MAX, Duration.between(wake, bed).dividedBy(4)))

        val lo = model.indexOf(groggyEnd)
        val hi = model.indexOf(windDown)
        val hour = 12
        val d = argMin(model, lo + hour, hi - hour)
        var mp = argMax(model, lo, d)
        var ep = argMax(model, d, hi)
        val prominence = minOf(model.level(mp), model.level(ep)) - model.level(d)
        val hasDip = mp < d && d < ep && prominence >= DIP_MIN_PROMINENCE

        val segs = mutableListOf<EnergySegment>()
        segs += EnergySegment(EnergyPhase.GROGGINESS, Interval(wake, groggyEnd), null)
        if (hasDip) {
            var dipStart = mp
            while (dipStart < d && model.level(dipStart) > model.level(d) + DIP_EDGE * (model.level(mp) - model.level(d))) dipStart++
            var dipEnd = ep
            while (dipEnd > d && model.level(dipEnd) > model.level(d) + DIP_EDGE * (model.level(ep) - model.level(d))) dipEnd--
            segs += EnergySegment(EnergyPhase.MORNING_PEAK, Interval(groggyEnd, model.at(dipStart)), model.at(mp))
            segs += EnergySegment(EnergyPhase.AFTERNOON_DIP, Interval(model.at(dipStart), model.at(dipEnd)), model.at(d))
            segs += EnergySegment(EnergyPhase.EVENING_PEAK, Interval(model.at(dipEnd), windDown), model.at(ep))
        } else {
            val mid = (lo + hi) / 2
            mp = argMax(model, lo, mid)
            ep = argMax(model, mid, hi)
            val split = model.at((mp + ep) / 2)
            segs += EnergySegment(EnergyPhase.MORNING_PEAK, Interval(groggyEnd, split), model.at(mp))
            segs += EnergySegment(EnergyPhase.EVENING_PEAK, Interval(split, windDown), model.at(ep))
        }
        segs += EnergySegment(EnergyPhase.WIND_DOWN, Interval(windDown, melStart), null)
        segs += EnergySegment(EnergyPhase.MELATONIN_WINDOW, Interval(melStart, bed), null)
        return segs to !hasDip
    }

    /** First DLMO clock time at or after [after], in the model's zone. */
    fun dlmoAtOrAfter(model: EnergyModel, after: Instant): Instant {
        var date = after.atZone(model.zone).toLocalDate()
        while (true) {
            val cand = date.atStartOfDay(model.zone).toLocalDateTime()
                .plusMinutes(model.dlmoMinutes.toLong()).atZone(model.zone).toInstant()
            if (!cand.isBefore(after)) return cand
            date = date.plusDays(1)
        }
    }

    private fun argMin(m: EnergyModel, from: Int, to: Int): Int =
        (from..to).minByOrNull { m.level(it) } ?: from

    private fun argMax(m: EnergyModel, from: Int, to: Int): Int =
        (from..to).maxByOrNull { m.level(it) } ?: from
}

internal fun LocalTime.minutesOfDay(): Double = toSecondOfDay() / 60.0
