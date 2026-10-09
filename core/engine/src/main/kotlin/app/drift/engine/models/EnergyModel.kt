package app.drift.engine.models

import app.drift.engine.ParameterSet
import app.drift.engine.internal.DAY_MIN_L
import app.drift.engine.internal.Preprocessed
import app.drift.engine.internal.clamp
import app.drift.engine.internal.localToInstant
import app.drift.engine.internal.offsetMin
import app.drift.model.CheckIn
import app.drift.model.ConfidenceLevel
import app.drift.model.Interval
import app.drift.model.Provenance
import app.drift.model.engine.DayCurve
import app.drift.model.engine.EnergyCurve
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.EnergyPoint
import app.drift.model.engine.EnergySegment
import app.drift.model.engine.EngineInput
import java.time.Instant
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Model 4.6: three-process alertness model (Akerstedt and Folkard). A(t) = S(t) + C(t) + W(t), simulated at
 * 5-minute resolution over the last 14 days of real sleep and tomorrow's recommended sleep, normalised to 0..100,
 * lowered by that day's sleep debt (SAFTE-style reservoir, [debtPenalty]) and split into named phases by extrema
 * with a minimum prominence.
 */
internal class EnergyModel(private val p: ParameterSet) {

    private class Seg(val phase: EnergyPhase, val a: Int, val b: Int, val ext: Int?)
    private class RunSegs(val segs: List<Seg>, val dipFound: Boolean, val regionMin: Int)
    private class Fit(val amp: Double, val phase: Double, val used: Int)

    fun run(
        pre: Preprocessed,
        input: EngineInput,
        needMin: Double,
        debtMin: Double,
        circ: CircadianResult,
        habitual: Habitual,
        plan: SleepPlan,
        level: ConfidenceLevel,
        debtPenalty: (level: Double, day: Long) -> Double = { _, _ -> 0.0 },
    ): EnergyCurve {
        val step = p.simStepMin.i
        val zone = input.zone
        val gridStart = (pre.todayDay - p.simHistoryDays.i) * DAY_MIN_L
        val gridEnd = (pre.todayDay + 2) * DAY_MIN_L
        val n = ((gridEnd - gridStart) / step).toInt()
        val asleep = BooleanArray(n)
        fun idxOf(local: Long): Int = Math.floorDiv(local - gridStart, step.toLong()).toInt()
        fun fill(a: Long, b: Long) {
            if (b <= a) return
            val i0 = Math.ceil((a - gridStart).toDouble() / step).toInt().coerceAtLeast(0)
            val i1 = Math.ceil((b - gridStart).toDouble() / step).toInt().coerceAtMost(n)
            for (i in i0 until i1) asleep[i] = true
        }

        // Observed sleep, then imputed habitual nights where the main sleep is missing, then recommended sleep.
        for (night in pre.nights) {
            for (ep in listOfNotNull(night.main) + night.naps) {
                for (sp in ep.asleepLocal) fill(sp.start, minOf(sp.end, pre.nowLocal))
            }
        }
        val dur = habitual.durationMin.coerceIn(240.0, 720.0)
        for (d in (pre.todayDay - p.simHistoryDays.i)..pre.todayDay) {
            if (pre.main(d) != null) continue
            val wake = d * DAY_MIN_L + habitual.wake.toLong()
            val onset = wake - dur.toLong()
            fill(onset, minOf(wake, pre.nowLocal))
        }
        val bedAbs = Math.floorDiv(plan.bed.epochSecond, 60L)
        val wakeAbs = Math.floorDiv(plan.wake.epochSecond, 60L)
        val bedLocal = bedAbs + offsetMin(zone, bedAbs)
        val wakeLocal = wakeAbs + offsetMin(zone, wakeAbs)
        fill(maxOf(bedLocal, pre.nowLocal), wakeLocal)
        fill(bedLocal + DAY_MIN_L, wakeLocal + DAY_MIN_L)

        // Homeostatic and inertia components.
        val s = DoubleArray(n)
        val w = DoubleArray(n)
        val tauW = p.tauWake.v
        val tauS = p.tauSleep.v
        val scale = (p.refNeed.v / needMin).coerceIn(0.6, 1.6)
        var h = p.simStartLevel.v
        var runStart = 0
        var w0 = 0.0
        var tw = -1
        val decayWake = exp(-step / tauW)
        val decaySleep = exp(-step * scale / tauS)
        for (i in 0 until n) {
            s[i] = h
            if (i > 0 && asleep[i - 1] && !asleep[i]) {
                w0 = inertiaStart((i - runStart) * step.toDouble(), needMin, debtMin)
                tw = i
            }
            if (asleep[i]) {
                if (i == 0 || !asleep[i - 1]) runStart = i
                w[i] = 0.0
                h = 1 - (1 - h) * decaySleep
            } else {
                w[i] = if (tw >= 0) -w0 * exp(-(i - tw) * step / p.inertiaTau.v) else 0.0
                h *= decayWake
            }
        }

        val dlmoByDay = DoubleArray(18) { circ.dlmoAt(it) }
        fun dlmoFor(day: Long): Double = dlmoByDay[((day - pre.todayDay).toInt()).coerceIn(0, dlmoByDay.size - 1)]
        fun levelAt(i: Int, amp: Double, phase: Double): Double {
            val t = gridStart + i.toLong() * step
            val day = Math.floorDiv(t, DAY_MIN_L)
            val tm = (t - day * DAY_MIN_L).toDouble()
            val dlmo = dlmoFor(day)
            val c1 = p.circAmp.v * cos(2 * PI * (tm - (dlmo + p.circAcrophase.v + phase)) / 1440.0)
            val c2 = p.circHarmonicAmp.v * cos(4 * PI * (tm - (dlmo + p.harmonicAcrophase.v + phase)) / 1440.0)
            val a = p.homeoWeight.v * s[i] + amp * (c1 + c2) + w[i]
            val raw = (100.0 * (a - p.levelLow.v) / (p.levelHigh.v - p.levelLow.v)).clamp(0.0, 100.0)
            // Chronic debt (model 4.6b): lower peaks, deeper dips.
            return (raw - debtPenalty(raw, day)).clamp(0.0, 100.0)
        }

        val fit = calibrate(input, pre, gridStart, step, n, ::levelAt)
        val amp = fit?.amp ?: 1.0
        val phase = fit?.phase ?: 0.0
        val lv = DoubleArray(n) { levelAt(it, amp, phase) }

        // Awake runs and their segmentation.
        val runs = ArrayList<IntArray>()
        run {
            var i = 0
            while (i < n) {
                if (!asleep[i]) {
                    var j = i
                    while (j < n && !asleep[j]) j++
                    runs += intArrayOf(i, j)
                    i = j
                } else i++
            }
        }
        val runSegs = runs.map { r -> segmentRun(r[0], r[1], n, lv, w, gridStart, step, ::dlmoFor, circ, pre) }

        val days = ArrayList<DayCurve>()
        for (k in -1..1) {
            val day = pre.todayDay + k
            val d0 = idxOf(day * DAY_MIN_L)
            val d1 = idxOf((day + 1) * DAY_MIN_L)
            val points = ArrayList<EnergyPoint>(d1 - d0)
            for (i in d0 until d1) {
                val local = gridStart + i.toLong() * step
                val inst = localToInstant(zone, local)
                if (java.time.LocalDateTime.ofEpochSecond(local * 60, 0, java.time.ZoneOffset.UTC) !=
                    java.time.LocalDateTime.ofInstant(inst, zone)
                ) continue
                points += EnergyPoint(inst, Math.round(lv[i] * 100.0) / 100.0)
            }
            val segments = ArrayList<EnergySegment>()
            var dip = false
            var longest = 0
            var flatCandidate = false
            for ((ri, r) in runs.withIndex()) {
                val rs = r[0]
                val re = r[1]
                val ov = minOf(re, d1) - maxOf(rs, d0)
                if (ov <= 0) continue
                val rsg = runSegs[ri]
                if (ov > longest) {
                    longest = ov
                    flatCandidate = !rsg.dipFound && rsg.regionMin >= 360
                }
                for (sg in rsg.segs) {
                    val a = maxOf(sg.a, d0)
                    val b = minOf(sg.b, d1)
                    if (b <= a) continue
                    segments += EnergySegment(sg.phase, interval(gridStart, step, a, b, zone), sg.ext?.takeIf { it in d0 until d1 }?.let { inst(gridStart, step, it, zone) })
                }
            }
            // Sleep segments.
            var i = d0
            while (i < d1) {
                if (asleep[i]) {
                    var j = i
                    while (j < d1 && asleep[j]) j++
                    segments += EnergySegment(EnergyPhase.SLEEP, interval(gridStart, step, i, j, zone), null)
                    i = j
                } else i++
            }
            segments.sortBy { it.interval.start }
            val lvl = if (k > 0) lower(level) else level
            days += DayCurve(LocalDate.ofEpochDay(day), points, segments, flatCandidate, lvl, Provenance.ESTIMATED)
        }
        return EnergyCurve(days, fit?.used ?: 0)
    }

    private fun lower(l: ConfidenceLevel): ConfidenceLevel = when (l) {
        ConfidenceLevel.HIGH -> ConfidenceLevel.MEDIUM
        else -> ConfidenceLevel.LOW
    }

    private fun inertiaStart(sleepMin: Double, needMin: Double, debtMin: Double): Double {
        if (sleepMin < p.napDurationMax.v) {
            return p.inertiaBase.v * p.inertiaNapScale.v * minOf(1.0, sleepMin / 60.0)
        }
        val shortfallH = maxOf(0.0, needMin - sleepMin) / 60.0
        val scale = (1 + p.inertiaPerShortHour.v * shortfallH + p.inertiaPerDebtHour.v * debtMin / 60.0)
            .coerceAtMost(p.inertiaMaxScale.v)
        return p.inertiaBase.v * scale
    }

    private fun inst(gridStart: Long, step: Int, idx: Int, zone: java.time.ZoneId): Instant =
        localToInstant(zone, gridStart + idx.toLong() * step)

    private fun interval(gridStart: Long, step: Int, a: Int, b: Int, zone: java.time.ZoneId): Interval {
        val s = inst(gridStart, step, a, zone)
        val e = inst(gridStart, step, b, zone)
        return Interval(s, if (e.isBefore(s)) s else e)
    }

    // ---- calibration -------------------------------------------------------------------------------

    private fun calibrate(
        input: EngineInput,
        pre: Preprocessed,
        gridStart: Long,
        step: Int,
        n: Int,
        levelAt: (Int, Double, Double) -> Double,
    ): Fit? {
        val idx = ArrayList<Int>()
        val target = ArrayList<Double>()
        for (c in input.checkIns) {
            val abs = Math.floorDiv(c.at.epochSecond, 60L)
            if (abs > pre.nowAbs) continue
            val local = abs + offsetMin(input.zone, abs)
            val i = Math.floorDiv(local - gridStart, step.toLong()).toInt()
            if (i < 0 || i >= n) continue
            val y = when (c) {
                is CheckIn.EnergyRating -> (c.rating - 1) / 4.0 * 100.0
                is CheckIn.SleepRating -> continue
                is CheckIn.ReactionTest -> {
                    val base = ((p.rtWorst.v - c.medianRtMillis) / (p.rtWorst.v - p.rtBest.v)).clamp(0.0, 1.0) * 100.0
                    val lapseRate = if (c.trials > 0) c.lapses.toDouble() / c.trials else 0.0
                    (base - p.rtLapsePenalty.v * lapseRate).clamp(0.0, 100.0)
                }
            }
            idx += i
            target += y
        }
        if (idx.size < p.calibMinCheckIns.i) return null
        val ampSpan = maxOf(p.calibAmpMax.v - 1.0, 1.0 - p.calibAmpMin.v)
        fun cost(a: Double, ph: Double): Double {
            var sse = 0.0
            for (k in idx.indices) {
                val e = levelAt(idx[k], a, ph) - target[k]
                sse += e * e
            }
            val r = (a - 1.0) / ampSpan
            val q = ph / p.calibPhaseMax.v
            return sse + p.calibRidge.v * (r * r + q * q)
        }
        var bestA = 1.0
        var bestP = 0.0
        var best = cost(bestA, bestP)
        fun search(aLo: Double, aHi: Double, aStep: Double, pLo: Double, pHi: Double, pStep: Double) {
            var a = aLo
            while (a <= aHi + 1e-9) {
                var ph = pLo
                while (ph <= pHi + 1e-9) {
                    val c = cost(a, ph)
                    if (c < best - 1e-9) { best = c; bestA = a; bestP = ph }
                    ph += pStep
                }
                a += aStep
            }
        }
        search(p.calibAmpMin.v, p.calibAmpMax.v, 0.1, -p.calibPhaseMax.v, p.calibPhaseMax.v, 15.0)
        search(
            maxOf(p.calibAmpMin.v, bestA - 0.1), minOf(p.calibAmpMax.v, bestA + 0.1), 0.025,
            maxOf(-p.calibPhaseMax.v, bestP - 15), minOf(p.calibPhaseMax.v, bestP + 15), 3.0,
        )
        return Fit(bestA, bestP, idx.size)
    }

    // ---- segmentation ------------------------------------------------------------------------------------

    private fun segmentRun(
        rs: Int, re: Int, n: Int, lv: DoubleArray, w: DoubleArray, gridStart: Long, step: Int,
        dlmoFor: (Long) -> Double, circ: CircadianResult, pre: Preprocessed,
    ): RunSegs {
        val segs = ArrayList<Seg>()
        val isStart = rs == 0
        // Grogginess: until the inertia term has decayed.
        var gEnd = rs
        if (!isStart) {
            while (gEnd < re && -w[gEnd] > p.inertiaEndLevel.v) gEnd++
            if (gEnd > rs) segs += Seg(EnergyPhase.GROGGINESS, rs, gEnd, null)
        }
        // Evening structure for this run: the first wind-down whose melatonin window starts after grogginess.
        var wdIdx = re
        var melIdx = re
        val dayOfRs = Math.floorDiv(gridStart + rs.toLong() * step, DAY_MIN_L)
        for (d in (dayOfRs - 1)..(dayOfRs + 2)) {
            val dlmo = dlmoFor(d)
            val ms = d * DAY_MIN_L + (dlmo + p.melatoninStartAfterDlmo.v).toLong()
            val msIdx = Math.floorDiv(ms - gridStart, step.toLong()).toInt()
            if (msIdx >= gEnd) {
                melIdx = msIdx
                wdIdx = Math.floorDiv(ms - p.windDownBeforeWindow.i - gridStart, step.toLong()).toInt()
                break
            }
        }
        val regionEnd = minOf(maxOf(wdIdx, gEnd), re)
        val regionStart = gEnd
        val len = regionEnd - regionStart
        var dipFound = false
        if (len > 0) {
            val y = smooth(lv, regionStart, regionEnd)
            val dipRel = findDip(y)
            if (dipRel >= 0) {
                dipFound = true
                var pk = 0
                for (i in 0..dipRel) if (y[i] > y[pk]) pk = i
                var ev = dipRel
                for (i in dipRel until y.size) if (y[i] > y[ev]) ev = i
                val dipStart = regionStart + (pk + dipRel) / 2
                val dipEnd = regionStart + (dipRel + ev) / 2
                segs += Seg(EnergyPhase.MORNING_PEAK, regionStart, dipStart, regionStart + pk)
                segs += Seg(EnergyPhase.AFTERNOON_DIP, dipStart, dipEnd, regionStart + dipRel)
                segs += Seg(EnergyPhase.EVENING_PEAK, dipEnd, regionEnd, regionStart + ev)
            } else {
                var mx = 0
                for (i in y.indices) if (y[i] > y[mx]) mx = i
                val t = gridStart + (regionStart + mx).toLong() * step
                val minuteOfDay = Math.floorMod(t, DAY_MIN_L)
                val phase = if (minuteOfDay < 13 * 60) EnergyPhase.MORNING_PEAK else EnergyPhase.EVENING_PEAK
                segs += Seg(phase, regionStart, regionEnd, regionStart + mx)
            }
        }
        if (wdIdx < re) {
            val a = maxOf(wdIdx, gEnd)
            val b = minOf(melIdx, re)
            if (b > a) segs += Seg(EnergyPhase.WIND_DOWN, a, b, null)
        }
        if (melIdx < re) {
            val a = maxOf(melIdx, gEnd)
            if (re > a) segs += Seg(EnergyPhase.MELATONIN_WINDOW, a, re, null)
        }
        val regionMin = len * step
        return RunSegs(segs, dipFound, regionMin)
    }

    private fun smooth(lv: DoubleArray, a: Int, b: Int): DoubleArray {
        val width = p.smoothPoints.i.coerceAtLeast(1)
        val half = width / 2
        return DoubleArray(b - a) { k ->
            val i = a + k
            var sum = 0.0
            var c = 0
            for (j in (i - half)..(i + half)) {
                if (j in a until b) { sum += lv[j]; c++ }
            }
            sum / c
        }
    }

    /** Index of the most prominent interior valley with prominence of at least the dip threshold, or -1. */
    private fun findDip(y: DoubleArray): Int {
        var best = -1
        var bestProm = p.dipProminence.v
        for (i in 1 until y.size - 1) {
            if (!(y[i] <= y[i - 1] && y[i] < y[i + 1])) continue
            var maxL = y[i]
            var j = i - 1
            while (j >= 0 && y[j] >= y[i]) { maxL = maxOf(maxL, y[j]); j-- }
            var maxR = y[i]
            j = i + 1
            while (j < y.size && y[j] >= y[i]) { maxR = maxOf(maxR, y[j]); j++ }
            val prom = minOf(maxL, maxR) - y[i]
            if (prom >= bestProm && prom > 0) { bestProm = prom; best = i }
        }
        return best
    }
}
