package app.drift.engine.models

import app.drift.engine.ParameterSet
import app.drift.engine.internal.Circ
import app.drift.engine.internal.DAY_MIN
import app.drift.engine.internal.DAY_MIN_L
import app.drift.engine.internal.Preprocessed
import app.drift.engine.internal.Stats
import app.drift.model.Confidence
import app.drift.model.ConfidenceLevel
import app.drift.model.Provenance
import app.drift.model.Quantity
import app.drift.model.engine.Chronotype
import app.drift.model.engine.ChronotypeLabel
import app.drift.model.engine.EngineInput
import app.drift.model.engine.RegularityReport
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Model 4.4: Sleep Regularity Index (Phillips et al. 2017), circular timing spread, social jetlag and
 * MSFsc chronotype blended with the questionnaire.
 */
internal class RegularityModel(private val p: ParameterSet) {

    /** Per-day asleep vectors on the local clock, plus pairwise agreement counts. */
    internal class SriGrid(private val pre: Preprocessed, private val p: ParameterSet) {
        val epochs: Int = (DAY_MIN / p.sriEpochMin.v).toInt().coerceAtLeast(1)
        private val epochLen = p.sriEpochMin.v
        private val days = HashMap<Long, BooleanArray>()
        private val matches = HashMap<Long, Int>()
        private val totals = HashMap<Long, Int>()

        init {
            val firstDay = pre.lastDay - p.sriTrendDays.i - p.sriLongDays.i - 1
            for (n in pre.nights) {
                for (ep in listOfNotNull(n.main) + n.naps) {
                    for (sp in ep.asleepLocal) {
                        var d = maxOf(Math.floorDiv(sp.start, DAY_MIN_L), firstDay)
                        val lastD = Math.floorDiv(sp.end - 1, DAY_MIN_L)
                        while (d <= lastD) {
                            val arr = days.getOrPut(d) { BooleanArray(epochs) }
                            val a = maxOf(sp.start, d * DAY_MIN_L) - d * DAY_MIN_L
                            val b = minOf(sp.end, (d + 1) * DAY_MIN_L) - d * DAY_MIN_L
                            // Epoch e is asleep when its centre minute lies inside the span.
                            var e = Math.ceil((a - epochLen / 2.0) / epochLen).toInt().coerceAtLeast(0)
                            while (e < epochs && e * epochLen + epochLen / 2.0 < b) {
                                arr[e] = true
                                e++
                            }
                            d++
                        }
                    }
                }
            }
        }

        private fun observed(day: Long): Boolean =
            pre.main(day) != null && (day == pre.lastDay || pre.main(day + 1) != null)

        private fun limit(day: Long): Int =
            if (day == pre.lastDay && pre.lastDay == pre.todayDay) {
                (pre.nowMinuteOfDay / epochLen).toInt().coerceIn(0, epochs)
            } else epochs

        /** Returns (matches, total) for the pair (day, day + 1) or null if either day is unobserved. */
        fun pair(day: Long): Pair<Int, Int>? {
            if (day + 1 > pre.lastDay || !observed(day) || !observed(day + 1)) return null
            val cached = totals[day]
            if (cached != null) return matches[day]!! to cached
            val a = days[day] ?: BooleanArray(epochs)
            val b = days[day + 1] ?: BooleanArray(epochs)
            val lim = limit(day + 1)
            var m = 0
            for (e in 0 until lim) if (a[e] == b[e]) m++
            matches[day] = m
            totals[day] = lim
            return m to lim
        }

        /** SRI over [days] ending at [end] (inclusive), or null with fewer than the minimum paired days. */
        fun sri(end: Long, windowDays: Int): Double? {
            var m = 0L
            var t = 0L
            var pairs = 0
            for (d in (end - windowDays + 1) until end) {
                val (mm, tt) = pair(d) ?: continue
                if (tt == 0) continue
                m += mm
                t += tt
                pairs++
            }
            if (pairs < p.sriMinPairs.i || t == 0L) return null
            return (-100.0 + 200.0 * m / t).coerceIn(0.0, 100.0)
        }
    }

    fun run(pre: Preprocessed, types: Map<Long, NightType>, input: EngineInput): RegularityReport {
        val grid = SriGrid(pre, p)
        val src = pre.sources
        fun q(v: Double?): Quantity<Double>? = v?.let { Quantity(it, Provenance.DERIVED, null, src) }
        val sri7 = grid.sri(pre.lastDay, p.sriShortDays.i)
        val sri14 = grid.sri(pre.lastDay, p.sriLongDays.i)
        val trend = ArrayList<Pair<LocalDate, Double>>()
        for (d in (pre.lastDay - p.sriTrendDays.i + 1)..pre.lastDay) {
            grid.sri(d, p.sriShortDays.i)?.let { trend += LocalDate.ofEpochDay(d) to it }
        }

        val recent = pre.usable.filter { it.day > pre.lastDay - p.spreadDays.i }
        fun spread(sel: (app.drift.engine.internal.ProcEpisode) -> Double): Quantity<java.time.Duration>? {
            if (recent.size < p.spreadMinNights.i) return null
            val sd = Circ.sd(recent.map { sel(it.main!!) }) ?: return null
            return Quantity(minutesToDuration(sd), Provenance.DERIVED, null, src)
        }

        val look = pre.usable.filter { it.day > pre.lastDay - p.chronoLookbackDays.i }
        val free = look.filter { types[it.day] == NightType.FREE }
        val work = look.filter { types[it.day] == NightType.ALARM }

        val sjl = if (free.size >= p.sjlMinNights.i && work.size >= p.sjlMinNights.i) {
            val f = Circ.mean(free.map { it.main!!.midClock })!!
            val w = Circ.mean(work.map { it.main!!.midClock })!!
            Quantity(minutesToDuration(abs(Circ.wrap(f - w))), Provenance.DERIVED, null, src)
        } else null

        return RegularityReport(
            sri7 = q(sri7),
            sri14 = q(sri14),
            onsetSpread = spread { it.onsetClock },
            wakeSpread = spread { it.wakeClock },
            midpointSpread = spread { it.midClock },
            socialJetlag = sjl,
            chronotype = chronotype(free, work, input),
            trend = trend,
        )
    }

    private fun chronotype(
        free: List<app.drift.engine.internal.NightRecord>,
        work: List<app.drift.engine.internal.NightRecord>,
        input: EngineInput,
    ): Chronotype {
        val sched = input.profile.schedule
        val prior = if (sched.freeDayBedtime != null && sched.freeDayWakeTime != null) {
            Circ.mean(
                listOf(
                    sched.freeDayBedtime!!.let { it.hour * 60.0 + it.minute },
                    sched.freeDayWakeTime!!.let { it.hour * 60.0 + it.minute },
                ),
            )!!
        } else p.chronoPrior(input.profile.chronotype)
        val nFree = free.size
        val wq = ((p.chronoBlendNights.v - nFree) / p.chronoBlendNights.v).coerceIn(0.0, 1.0)
        val sigmaQ = p.chronoQuestionnaireSd.v
        var value = prior
        var sd = sigmaQ
        if (nFree > 0) {
            val msf = Circ.mean(free.map { it.main!!.midClock })!!
            val sdF = Stats.mean(free.map { it.main!!.tst })!!
            val corrected = if (work.isNotEmpty()) {
                val sdW = Stats.mean(work.map { it.main!!.tst })!!
                val nW = input.profile.schedule.workdays.size.coerceIn(0, 7).toDouble()
                val sdWeek = (nW * sdW + (7 - nW) * sdF) / 7.0
                if (sdF > sdW) msf - 0.5 * (sdF - sdWeek) else msf
            } else msf
            val data = Circ.mod(corrected)
            value = Circ.mod(prior + (1 - wq) * Circ.wrap(data - prior))
            val sigmaD = p.chronoDataSd.v
            sd = sqrt(wq * sigmaQ * sigmaQ + (1 - wq) * (sigmaD * sigmaD / nFree + 20.0 * 20.0))
        }
        val half = p.bandZ.v * sd
        val level = when {
            nFree >= 8 -> ConfidenceLevel.HIGH
            nFree >= p.chronoBlendNights.i -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
        val src = (free + work).flatMap { it.main!!.sources }.toSet()
        val wrapped = if (value > 900.0) value - DAY_MIN else value
        val label = when {
            wrapped < p.chronoEarlyBelow.v -> ChronotypeLabel.EARLY
            wrapped > p.chronoLateAbove.v -> ChronotypeLabel.LATE
            else -> ChronotypeLabel.INTERMEDIATE
        }
        return Chronotype(
            msfscMinutes = Quantity(value, Provenance.ESTIMATED, Confidence(value - half, value + half, 0.9, level), if (nFree > 0) src else emptySet()),
            label = label,
            freeNightsUsed = nFree,
            questionnaireWeight = wq,
        )
    }
}
