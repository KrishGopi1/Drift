package app.drift.engine.models

import app.drift.engine.ParameterSet
import app.drift.engine.internal.Circ
import app.drift.engine.internal.DAY_MIN
import app.drift.engine.internal.DAY_MIN_L
import app.drift.engine.internal.Preprocessed
import app.drift.engine.internal.localToInstant
import app.drift.engine.internal.offsetMin
import app.drift.model.Confidence
import app.drift.model.ConfidenceLevel
import app.drift.model.Interval
import app.drift.model.Provenance
import app.drift.model.Quantity
import app.drift.model.engine.CircadianEstimate
import app.drift.model.engine.EngineInput
import app.drift.model.engine.Reentrainment
import java.time.Duration
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

internal class CircadianResult(
    val estimate: CircadianEstimate,
    /** DLMO on the local clock for the sleep day [today]. */
    val dlmo: Double,
    val sd: Double,
    val level: ConfidenceLevel,
    private val remaining: Double,
    private val advanceRate: Double,
    private val delayRate: Double,
) {
    /** DLMO for the day [dayOffset] days after today (re-entrainment keeps moving it). */
    fun dlmoAt(dayOffset: Int): Double {
        var phi = dlmo
        var rem = remaining
        repeat(maxOf(0, dayOffset)) {
            val rate = if (rem > 0) delayRate else advanceRate
            val s = minOf(abs(rem), rate)
            val dir = if (rem > 0) 1.0 else -1.0
            phi = Circ.mod(phi + dir * s)
            rem -= dir * s
        }
        return phi
    }
}

/**
 * Model 4.5: circular Kalman filter on the DLMO clock time with rate-limited re-entrainment after zone changes
 * and melatonin window / wind-down instants for the coming evening.
 */
internal class CircadianModel(private val p: ParameterSet) {

    private class State(var phi: Double, var pv: Double, var rem: Double)

    fun run(
        pre: Preprocessed,
        types: Map<Long, NightType>,
        input: EngineInput,
        habitual: Habitual,
    ): CircadianResult {
        val shift = pre.shiftWorkMode
        val qSd = if (shift) p.dlmoShiftProcessSd.v else p.dlmoProcessSd.v
        val q = qSd * qSd
        val st = State(Circ.mod(habitual.onset - p.dlmoOnsetOffset.v), p.dlmoPriorSd.v * p.dlmoPriorSd.v, 0.0)

        fun advance(days: Long) {
            if (days <= 0) return
            st.pv += q * days
            var d = days
            while (d > 0 && st.rem != 0.0) {
                val rate = if (st.rem > 0) p.delayRate.v else p.advanceRate.v
                val dir = if (st.rem > 0) 1.0 else -1.0
                val s = minOf(abs(st.rem), rate)
                st.phi = Circ.mod(st.phi + dir * s)
                st.rem -= dir * s
                if (abs(st.rem) < 1e-6) st.rem = 0.0
                d--
            }
        }

        fun travel(delta: Double) {
            val target = st.phi + st.rem
            st.phi = Circ.mod(st.phi + delta)
            val x = Circ.mod(target - st.phi)
            if (x < 1e-6 || DAY_MIN - x < 1e-6) { st.rem = 0.0; return }
            val delayDays = x / p.delayRate.v
            val advDays = (DAY_MIN - x) / p.advanceRate.v
            st.rem = if (delayDays <= advDays) x else -(DAY_MIN - x)
        }

        var prevDay: Long? = null
        var prevZoneId: String? = null
        var prevOffset = 0L
        for (n in pre.usable) {
            val m = n.main!!
            prevDay?.let { advance(n.day - it) }
            val off = m.localEnd - m.endAbs
            if (prevZoneId != null && m.zone.id != prevZoneId) {
                val delta = Circ.wrap((off - prevOffset).toDouble())
                if (abs(delta) >= p.zoneChangeMin.v) travel(delta)
            }
            prevZoneId = m.zone.id
            prevOffset = off
            prevDay = n.day

            val fromOnset = m.onsetClock - p.dlmoOnsetOffset.v
            val fromMid = m.midClock - p.dlmoMidpointOffset.v
            val alarm = types[n.day] == NightType.ALARM
            val shiftNight = n.day in pre.shiftNightDays
            val obs = if (alarm || shiftNight) {
                Circ.mod(fromOnset)
            } else {
                val a = p.dlmoOnsetWeight.v
                Circ.mod(fromOnset + (1 - a) * Circ.wrap(fromMid - fromOnset))
            }
            var mult = 1.0
            if (alarm) mult *= p.dlmoAlarmVarMult.v
            if (shiftNight) mult *= p.dlmoShiftVarMult.v
            if (st.rem != 0.0) mult *= p.dlmoReentrainVarMult.v
            val r = p.dlmoObsSd.v * p.dlmoObsSd.v * mult / m.weight.coerceAtLeast(0.05)
            val k = st.pv / (st.pv + r)
            val innov = Circ.wrap(obs - st.phi)
            st.phi = Circ.mod(st.phi + k * innov)
            st.pv *= (1 - k)
        }

        prevDay?.let { advance(pre.todayDay - it) }
        // The wearer is now in a different zone than the last recorded night: treat it as a trip.
        val nowOff = pre.nowLocal - pre.nowAbs
        if (prevZoneId != null && input.zone.id != prevZoneId) {
            val delta = Circ.wrap((nowOff - prevOffset).toDouble())
            if (abs(delta) >= p.zoneChangeMin.v) travel(delta)
        }

        val remaining = if (abs(st.rem) < p.reentrainReportMin.v) 0.0 else st.rem
        val sdTotal = sqrt(st.pv + p.dlmoMappingSd.v * p.dlmoMappingSd.v) * (if (shift) p.shiftBandMult.v else 1.0)
        val nights = pre.usable.size
        val level = when {
            nights < 5 || shift || remaining != 0.0 -> ConfidenceLevel.LOW
            sdTotal > 38.0 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.HIGH
        }
        val half = p.bandZ.v * sdTotal
        val dlmoQ = Quantity(
            st.phi, Provenance.ESTIMATED,
            Confidence(st.phi - half, st.phi + half, 0.9, level),
            if (nights > 0) pre.sources else emptySet(),
        )

        val result = CircadianResult(
            estimate = CircadianEstimate(dlmoQ, null, null, shift, null),
            dlmo = st.phi, sd = sdTotal, level = level, remaining = st.rem,
            advanceRate = p.advanceRate.v, delayRate = p.delayRate.v,
        )
        val (window, windDown) = coming(result, pre, input.zone, input.now)
        val reent = if (remaining != 0.0) {
            val rate = if (remaining > 0) p.delayRate.v else p.advanceRate.v
            Reentrainment(
                remainingShift = Duration.ofMinutes(abs(remaining).toLong()),
                eastward = remaining < 0,
                estimatedDaysLeft = ceil(abs(remaining) / rate).toInt(),
            )
        } else null
        return CircadianResult(
            estimate = CircadianEstimate(dlmoQ, window, windDown, shift, reent),
            dlmo = st.phi, sd = sdTotal, level = level, remaining = st.rem,
            advanceRate = p.advanceRate.v, delayRate = p.delayRate.v,
        )
    }

    /** Melatonin window and wind-down start of the current or next evening. */
    private fun coming(c: CircadianResult, pre: Preprocessed, zone: ZoneId, now: java.time.Instant): Pair<Interval, java.time.Instant> {
        for (k in -1..2) {
            val dlmo = c.dlmoAt(k)
            val startLocal = (pre.todayDay + k) * DAY_MIN_L + (dlmo + p.melatoninStartAfterDlmo.v).toLong()
            val start = localToInstant(zone, startLocal)
            val end = start.plus(Duration.ofMinutes(p.melatoninLength.i.toLong()))
            if (end.isAfter(now)) {
                return Interval(start, end) to start.minus(Duration.ofMinutes(p.windDownBeforeWindow.i.toLong()))
            }
        }
        val dlmo = c.dlmoAt(2)
        val start = localToInstant(zone, (pre.todayDay + 2) * DAY_MIN_L + (dlmo + p.melatoninStartAfterDlmo.v).toLong())
        return Interval(start, start.plus(Duration.ofMinutes(p.melatoninLength.i.toLong()))) to start.minus(Duration.ofMinutes(p.windDownBeforeWindow.i.toLong()))
    }
}
