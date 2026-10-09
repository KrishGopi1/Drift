package app.drift.engine.models

import app.drift.engine.ParameterSet
import app.drift.engine.internal.NightRecord
import app.drift.engine.internal.Preprocessed
import app.drift.engine.internal.Stats
import app.drift.engine.internal.clamp
import app.drift.model.ConfidenceLevel
import app.drift.model.SourceId
import app.drift.model.engine.EngineInput
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

internal enum class NightType { FREE, ALARM }

/** Decides which nights are free (informative about need) and which were cut short by an alarm. */
internal class FreeNightDetector(private val p: ParameterSet) {
    class Result(val types: Map<Long, NightType>, val workdayMedianWake: Double?)

    fun classify(pre: Preprocessed, input: EngineInput): Result {
        val ann = HashMap<LocalDate, app.drift.model.DayAnnotation>()
        for (a in input.annotations) ann[a.date] = a
        val workdays = input.profile.schedule.workdays
        val lookStart = pre.lastDay - p.chronoLookbackDays.i
        val workdayWakes = pre.usable
            .filter { it.day >= lookStart && it.date.dayOfWeek in workdays && ann[it.date]?.freeDay != true }
            .map { it.main!!.wakeMinuteOfDay.toDouble() }
        val median = Stats.median(workdayWakes)
            ?: input.profile.schedule.wakeTime?.let { it.hour * 60.0 + it.minute }
        val types = HashMap<Long, NightType>()
        for (n in pre.usable) {
            val m = n.main!!
            val a = ann[n.date]
            val alarmNear = pre.alarmAbs.any { it >= m.endAbs - p.alarmNearWakeMin.v && it <= m.endAbs + p.alarmNearWakeMin.v }
            types[n.day] = when {
                a?.freeDay == true -> NightType.FREE
                a?.alarmSet == true || a?.alarmDismissedAt != null -> NightType.ALARM
                a?.freeDay == false -> NightType.ALARM
                a?.alarmSet == false -> NightType.FREE
                alarmNear -> NightType.ALARM
                median != null && m.wakeMinuteOfDay > median + p.freeWakeLateMin.v -> NightType.FREE
                n.date.dayOfWeek in workdays -> NightType.ALARM
                else -> NightType.FREE
            }
        }
        return Result(types, median)
    }
}

internal class NeedResult(
    val priorMean: Double,
    val mean: Double,
    val sd: Double,
    val informativeNights: Int,
    val personal: Boolean,
    val displayed: Double,
    val lower: Double,
    val upper: Double,
    val level: ConfidenceLevel,
    val sources: Set<SourceId>,
    val fittedEta: Double,
    val etaPairs: Int,
) {
    /** Need band used when computing debt (90%). */
    val debtLower: Double get() = lower
    val debtUpper: Double get() = upper
}

internal object DebtMath {
    fun lambda(p: ParameterSet): Double = 0.5.pow(1.0 / p.debtHalfLifeDays.v)

    /** One day of the leaky accumulator (plan 4.3). All values in minutes. */
    fun step(prev: Double, need: Double, sleep: Double, lambda: Double, eta: Double): Double {
        val d = if (sleep <= need) lambda * prev + (need - sleep) else lambda * prev - eta * (sleep - need)
        return if (d < 0.0) 0.0 else d
    }
}

/**
 * Model 4.2: 1-D local-level Kalman filter for sleep need with rebound correction on free nights and a
 * Tobit-style censored update for alarm nights. The filter is replayed over the whole input history on every
 * run, so the result is a pure function of the input.
 */
internal class SleepNeedModel(private val p: ParameterSet) {

    fun run(pre: Preprocessed, types: Map<Long, NightType>, input: EngineInput): NeedResult {
        val prior = p.needPrior(input.profile.ageBand)
        val priorVar = p.priorSd.v * p.priorSd.v
        val qDay = p.processNoise.v * p.processNoise.v / 30.0
        val lambda = DebtMath.lambda(p)
        val beta = p.reboundBeta.v

        var m = prior
        var pv = priorVar
        var debt = 0.0
        var lastDay = Long.MIN_VALUE
        var informative = 0
        var prevFreeDay = Long.MIN_VALUE
        var prevFreeSurplus = 0.0
        val etaSamples = ArrayList<Double>()

        for (n in pre.usable) {
            val main = n.main!!
            val gap = if (lastDay == Long.MIN_VALUE) 0L else n.day - lastDay
            if (gap > 0) {
                pv += qDay * gap
                debt *= lambda.pow(gap.toDouble())
            }
            lastDay = n.day
            val type = types[n.day] ?: NightType.ALARM
            val rebound = (beta * debt).coerceAtMost(p.reboundCap.v)
            val y = (main.tst - rebound).clamp(p.needObsClampLow.v, p.needObsClampHigh.v)
            val w = main.weight.coerceAtLeast(0.05)
            if (type == NightType.FREE) {
                val r = p.obsSd.v * p.obsSd.v / w
                val k = pv / (pv + r)
                m += k * (y - m)
                pv *= (1 - k)
                informative++
                val surplus = main.tst - m
                if (n.day == prevFreeDay + 1 && prevFreeSurplus >= p.debtEtaMinSurplus.v) {
                    val q = (surplus / prevFreeSurplus).coerceIn(0.0, 1.5)
                    etaSamples += (lambda - q) / beta
                }
                prevFreeDay = n.day
                prevFreeSurplus = surplus
            } else {
                val r = p.alarmObsSd.v * p.alarmObsSd.v / w
                val s = sqrt(pv + r)
                val alpha = (y - m) / s
                val lam = Stats.inverseMills(alpha)
                val k = pv / (pv + r)
                val vz = (s * s * (1 + alpha * lam - lam * lam)).coerceAtLeast(0.0)
                m += k * s * lam
                pv = pv * (1 - k) + k * k * vz
            }
            m = m.clamp(p.needMin.v, p.needMax.v)
            if (pv < 1.0) pv = 1.0
            debt = DebtMath.step(debt, m, main.tst + n.napCredit(p.napFullCredit.v, p.napOverRate.v), lambda, p.debtEta.v)
        }
        if (lastDay != Long.MIN_VALUE && pre.lastDay > lastDay) pv += qDay * (pre.lastDay - lastDay)

        val personal = informative >= p.minInformative.i
        val sd = sqrt(pv)
        val z = p.bandZ.v
        val displayed: Double
        val lo: Double
        val hi: Double
        val level: ConfidenceLevel
        if (personal) {
            displayed = m.clamp(p.needMin.v, p.needMax.v)
            lo = (displayed - z * sd).clamp(p.needMin.v, p.needMax.v)
            hi = (displayed + z * sd).clamp(p.needMin.v, p.needMax.v)
            level = when {
                sd <= 15.0 -> ConfidenceLevel.HIGH
                sd <= 25.0 -> ConfidenceLevel.MEDIUM
                else -> ConfidenceLevel.LOW
            }
        } else {
            displayed = prior
            lo = (prior - z * p.priorSd.v).clamp(p.needMin.v, p.needMax.v)
            hi = (prior + z * p.priorSd.v).clamp(p.needMin.v, p.needMax.v)
            level = ConfidenceLevel.LOW
        }
        val n = etaSamples.size
        val eta0 = p.debtEta.v
        val eta = if (n < p.debtEtaMinPairs.i) eta0 else {
            val med = Stats.median(etaSamples)!!.clamp(p.debtEtaMin.v, p.debtEtaMax.v)
            val wt = n / (n + p.debtEtaShrink.v)
            (wt * med + (1 - wt) * eta0).clamp(p.debtEtaMin.v, p.debtEtaMax.v)
        }
        return NeedResult(
            priorMean = prior, mean = m, sd = sd, informativeNights = informative, personal = personal,
            displayed = displayed, lower = lo, upper = hi, level = level,
            sources = if (personal) pre.sources else emptySet(),
            fittedEta = eta, etaPairs = n,
        )
    }
}
