package app.drift.engine.models

import app.drift.engine.ParameterSet
import app.drift.engine.internal.NightRecord
import app.drift.engine.internal.Preprocessed
import app.drift.engine.internal.Stats
import app.drift.model.ConfidenceLevel
import app.drift.model.DataQuality
import app.drift.model.Confidence
import app.drift.model.Provenance
import app.drift.model.Quantity
import app.drift.model.QualityTier
import app.drift.model.SourceId
import app.drift.model.engine.DailyDebt
import app.drift.model.engine.DebtBand
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import kotlin.math.roundToLong

internal class DebtResult(
    val minutes: Double,
    val lower: Double,
    val upper: Double,
    val band: DebtBand,
    val daily: List<DailyDebt>,
    val imputedNights: Int,
    val sources: Set<SourceId>,
    val level: ConfidenceLevel,
)

internal fun minutesToDuration(min: Double): Duration = Duration.ofSeconds((min * 60.0).roundToLong())

/**
 * Model 4.3: leaky accumulator over the last 14 sleep days with nap credit, same-weekday imputation,
 * and an unavailable result after 3 or more consecutive missing nights.
 */
internal class DebtModel(private val p: ParameterSet) {

    private class Day(val day: Long, val sleep: Double?, val night: NightRecord?)

    /** Returns null when debt is unavailable. */
    fun run(pre: Preprocessed, need: NeedResult): DebtResult? {
        val firstUsable = pre.usable.firstOrNull()?.day ?: return null
        val lambda = DebtMath.lambda(p)
        val eta = need.fittedEta
        val start = maxOf(pre.lastDay - p.debtWindowDays.i + 1, firstUsable)
        if (start > pre.lastDay) return null

        val raw = (start..pre.lastDay).map { d ->
            val n = pre.byDay[d]
            val main = n?.main
            Day(d, main?.let { it.tst + n.napCredit(p.napFullCredit.v, p.napOverRate.v) }, n)
        }

        // Runs of consecutive missing nights: a trailing run at the limit makes debt unavailable; an earlier
        // long run is left as unknown (the debt only leaks through it) rather than guessed.
        val limit = p.maxMissingRun.i
        val unknown = HashSet<Long>()
        var run = 0
        for (d in raw) {
            if (d.sleep == null) {
                run++
            } else {
                if (run >= limit) for (k in 1..run) unknown += d.day - k
                run = 0
            }
        }
        if (run >= limit) return null
        val days = raw
        if (days.none { it.sleep != null }) return null
        val widened = unknown.size

        // Imputation values for short gaps.
        val observedInWindow = days.mapNotNull { it.sleep }
        val overall = Stats.median(observedInWindow)
        val imputed = HashMap<Long, Double>()
        for (d in days) {
            if (d.sleep != null || d.day in unknown) continue
            val dow = LocalDate.ofEpochDay(d.day).dayOfWeek
            imputed[d.day] = imputeFor(pre, d.day, dow) ?: overall ?: need.displayed
        }

        fun accumulate(n: Double): Pair<Double, List<Triple<Double, Double, Double>>> {
            var debt = 0.0
            val rows = ArrayList<Triple<Double, Double, Double>>()
            for (d in days) {
                val s = d.sleep ?: imputed[d.day]
                val after = if (s == null) lambda * debt else DebtMath.step(debt, n, s, lambda, eta)
                rows += Triple(s ?: 0.0, after - lambda * debt, after)
                debt = after
            }
            return debt to rows
        }

        val (value, rows) = accumulate(need.displayed)
        val lowDebt = accumulate(need.debtLower).first
        val highDebt = accumulate(need.debtUpper).first
        val imputedCount = imputed.size
        val extra = (imputedCount + widened) * p.imputeBandPerDay.v
        val lower = maxOf(0.0, minOf(lowDebt, value) - extra)
        val upper = maxOf(highDebt, value) + extra

        val daily = days.mapIndexed { i, d ->
            val (_, contrib, after) = rows[i]
            val night = d.night
            val sleepQ = d.sleep?.let { s ->
                val main = night!!.main!!
                val prov = when {
                    main.userEdited || main.userConfirmed -> Provenance.USER_ENTERED
                    night.naps.isEmpty() && main.allRecorded && main.bestTier >= QualityTier.WEARABLE_WRIST -> Provenance.MEASURED
                    else -> Provenance.DERIVED
                }
                Quantity(minutesToDuration(s), prov, null, main.sources + night.naps.flatMap { it.sources })
            }
            DailyDebt(
                date = LocalDate.ofEpochDay(d.day),
                sleep = sleepQ,
                need = minutesToDuration(need.displayed),
                contribution = minutesToDuration(contrib),
                debtAfter = minutesToDuration(after),
                imputed = d.sleep == null && d.day !in unknown,
            )
        }
        val band = when {
            value < p.debtLowMax.v -> DebtBand.LOW
            value <= p.debtHighMin.v -> DebtBand.MODERATE
            else -> DebtBand.HIGH
        }
        val level = when {
            imputedCount > 0 || widened > 0 || !need.personal || days.size < 5 -> ConfidenceLevel.LOW
            need.level == ConfidenceLevel.HIGH && days.size >= 12 -> ConfidenceLevel.HIGH
            else -> ConfidenceLevel.MEDIUM
        }
        return DebtResult(value, lower, upper, band, daily, imputedCount, pre.sources, level)
    }

    private fun imputeFor(pre: Preprocessed, day: Long, dow: DayOfWeek): Double? {
        val from = day - p.imputeLookbackDays.i
        val sameDow = pre.usable
            .filter { it.day in from until day && it.date.dayOfWeek == dow }
            .map { it.main!!.tst + it.napCredit(p.napFullCredit.v, p.napOverRate.v) }
        if (sameDow.size >= p.imputeMinSamples.i) return Stats.median(sameDow)
        return null
    }
}
