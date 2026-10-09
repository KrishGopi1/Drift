package app.drift.engine.models

import app.drift.engine.ParameterSet
import app.drift.engine.internal.Circ
import app.drift.model.engine.Cause
import app.drift.model.engine.CauseKind
import app.drift.model.engine.DailyDebt
import app.drift.model.engine.EngineState
import app.drift.model.engine.EngineVersion
import app.drift.model.engine.Explanation
import app.drift.model.engine.MetricKey
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** Headline numbers of one run: what the next run diffs against, and what this run reports. */
internal class Headline(
    val todayDay: Long,
    val needMin: Double,
    val needPersonal: Boolean,
    val informative: Int,
    val debtMin: Double?,
    val daily: List<DailyDebt>,
    val editedDays: Set<Long>,
    val sri7: Double?,
    val dlmo: Double,
    val reentraining: Boolean,
    val sourceCount: Int,
    val calibration: Int,
    val bedClock: Double,
    val nights: Int,
) {
    val lastDay: Long? get() = daily.lastOrNull()?.date?.toEpochDay()
}

/** Model 4.8: diff against the previous snapshot's state and attribute each headline change to a cause. */
internal class ExplanationModel(private val p: ParameterSet) {

    fun toState(h: Headline, version: EngineVersion, asOf: Instant): EngineState {
        val v = LinkedHashMap<String, Double>()
        v["prev.day"] = h.todayDay.toDouble()
        v["prev.needMinutes"] = h.needMin
        v["prev.needPersonal"] = if (h.needPersonal) 1.0 else 0.0
        v["prev.informative"] = h.informative.toDouble()
        h.debtMin?.let { v["prev.debtMinutes"] = it }
        h.lastDay?.let { v["prev.debtLastDay"] = it.toDouble() }
        h.sri7?.let { v["prev.sri7"] = it }
        v["prev.dlmoMinutes"] = h.dlmo
        v["prev.reentraining"] = if (h.reentraining) 1.0 else 0.0
        v["prev.sourceCount"] = h.sourceCount.toDouble()
        v["prev.calibration"] = h.calibration.toDouble()
        v["prev.bedClock"] = h.bedClock
        v["prev.nights"] = h.nights.toDouble()
        for (d in h.daily) {
            val key = d.date.toEpochDay()
            v["contrib.$key"] = d.contribution.seconds / 60.0
            d.sleep?.let { v["sleep.$key"] = it.value.seconds / 60.0 }
            if (key in h.editedDays) v["ue.$key"] = 1.0
        }
        return EngineState(version, asOf, v)
    }

    fun explain(prior: EngineState?, version: EngineVersion, cur: Headline): List<Explanation> {
        val usable = prior != null && prior.engineVersion.major == version.major &&
            prior.engineVersion.minor == version.minor && prior.values.containsKey("prev.day")
        if (!usable) return firstRun(cur)
        val pv = prior!!.values
        val out = ArrayList<Explanation>()
        debtExplanation(pv, cur)?.let { out += it }
        needExplanation(pv, cur)?.let { out += it }
        val prevSri = pv["prev.sri7"]
        if (prevSri != null && cur.sri7 != null && abs(cur.sri7 - prevSri) >= 1.0) {
            val d = cur.sri7 - prevSri
            out += Explanation(
                MetricKey.REGULARITY,
                "Regularity ${if (d > 0) "rose" else "fell"} from ${prevSri.roundToLong()} to ${cur.sri7.roundToLong()}.",
                listOf(Cause(CauseKind.NIGHT_ENTERED_WINDOW, cur.lastDay?.let { LocalDate.ofEpochDay(it) }, d,
                    "Your latest night entered the 7-day window and the oldest one dropped out.")),
            )
        }
        val prevDlmo = pv["prev.dlmoMinutes"]
        if (prevDlmo != null) {
            val shift = Circ.wrap(cur.dlmo - prevDlmo)
            if (abs(shift) >= 10.0) {
                val dir = if (shift > 0) "later" else "earlier"
                val why = if (cur.reentraining || (pv["prev.reentraining"] ?: 0.0) > 0.5) "as it adjusts to your new time zone" else "following your recent sleep timing"
                out += Explanation(
                    MetricKey.CIRCADIAN,
                    "Your body clock estimate moved ${RecommendationModel.durText(abs(shift).roundToLong())} $dir $why.",
                    listOf(Cause(CauseKind.CIRCADIAN_SHIFT, null, shift, "Estimated melatonin onset moved $dir by ${RecommendationModel.durText(abs(shift).roundToLong())}.")),
                )
            }
        }
        val prevCal = (pv["prev.calibration"] ?: 0.0).toInt()
        if (cur.calibration != prevCal) {
            out += Explanation(
                MetricKey.ENERGY,
                if (cur.calibration > 0) "Your energy curve is now shaped by ${cur.calibration} check-ins." else "Your energy curve is no longer calibrated by check-ins.",
                listOf(Cause(CauseKind.CHECK_IN_CALIBRATION, null, (cur.calibration - prevCal).toDouble(), "Check-ins used for calibration changed from $prevCal to ${cur.calibration}.")),
            )
        }
        val prevBed = pv["prev.bedClock"]
        if (prevBed != null) {
            val shift = Circ.wrap(cur.bedClock - prevBed)
            if (abs(shift) >= 10.0) {
                val needMoved = abs(cur.needMin - (pv["prev.needMinutes"] ?: cur.needMin)) >= 1.0
                out += Explanation(
                    MetricKey.RECOMMENDATION,
                    "Your recommended bedtime moved ${RecommendationModel.durText(abs(shift).roundToLong())} ${if (shift > 0) "later" else "earlier"}.",
                    listOf(Cause(if (needMoved) CauseKind.NEED_REESTIMATED else CauseKind.NIGHT_ENTERED_WINDOW, null, shift,
                        if (needMoved) "Your sleep need estimate changed." else "Your sleep debt or schedule changed.")),
                )
            }
        }
        return out
    }

    private fun firstRun(cur: Headline): List<Explanation> {
        fun first(metric: MetricKey, text: String) =
            Explanation(metric, text, listOf(Cause(CauseKind.FIRST_RUN, null, null, text)))
        val out = ArrayList<Explanation>()
        out += first(
            MetricKey.SLEEP_NEED,
            if (cur.needPersonal) "First analysis: your sleep need is estimated from ${cur.informative} free nights."
            else "First analysis: your sleep need is based on people like you until ${maxOf(0, p.minInformative.i - cur.informative)} more free nights are recorded.",
        )
        if (cur.debtMin != null) out += first(MetricKey.SLEEP_DEBT, "First analysis: debt is built from ${cur.nights} recorded nights.")
        if (cur.sri7 != null) out += first(MetricKey.REGULARITY, "First analysis: regularity is measured over your last 7 days.")
        out += first(MetricKey.CIRCADIAN, "First analysis: your body clock is estimated from ${cur.nights} nights of sleep timing.")
        out += first(MetricKey.ENERGY, "First analysis: your energy curve is simulated from your recent sleep.")
        out += first(MetricKey.RECOMMENDATION, "First analysis: bedtime follows your sleep need and wake-up schedule.")
        return out
    }

    private fun needExplanation(pv: Map<String, Double>, cur: Headline): Explanation? {
        val prev = pv["prev.needMinutes"] ?: return null
        val wasPersonal = (pv["prev.needPersonal"] ?: 0.0) > 0.5
        if (cur.needPersonal && !wasPersonal) {
            return Explanation(
                MetricKey.SLEEP_NEED,
                "Your sleep need now comes from your own sleep (${RecommendationModel.durText(cur.needMin.roundToLong())}) instead of the population estimate.",
                listOf(Cause(CauseKind.NEED_REESTIMATED, null, cur.needMin - prev, "${cur.informative} free nights are now recorded.")),
            )
        }
        val d = cur.needMin - prev
        if (abs(d) < 1.0) return null
        val text = "Your sleep need moved from ${RecommendationModel.durText(prev.roundToLong())} to ${RecommendationModel.durText(cur.needMin.roundToLong())}."
        val freshNights = cur.informative - (pv["prev.informative"] ?: 0.0).toInt()
        val why = if (freshNights > 0) "$freshNights new free night${if (freshNights == 1) "" else "s"} updated the estimate." else "Recent nights adjusted the estimate."
        return Explanation(MetricKey.SLEEP_NEED, text, listOf(Cause(CauseKind.NEED_REESTIMATED, null, d, why)))
    }

    private fun debtExplanation(pv: Map<String, Double>, cur: Headline): Explanation? {
        val prevDebt = pv["prev.debtMinutes"]
        val sourceAdded = cur.sourceCount > (pv["prev.sourceCount"] ?: cur.sourceCount.toDouble())
        val causes = ArrayList<Cause>()
        if (prevDebt != null && cur.debtMin == null) {
            return Explanation(
                MetricKey.SLEEP_DEBT,
                "Debt is no longer shown because 3 or more nights in a row are missing.",
                listOf(Cause(CauseKind.NIGHT_ENTERED_WINDOW, null, null, "Missing nights make a debt figure a guess.")),
            )
        }
        if (cur.debtMin == null) return null
        if (prevDebt == null) {
            return Explanation(
                MetricKey.SLEEP_DEBT,
                "Debt is available again: ${RecommendationModel.durText(cur.debtMin.roundToLong())}.",
                listOf(Cause(CauseKind.NIGHT_ENTERED_WINDOW, null, cur.debtMin, "Enough recent nights are recorded.")),
            )
        }
        val lambda = DebtMath.lambda(p)
        val curLast = cur.lastDay ?: return null
        val prevLast = (pv["prev.debtLastDay"] ?: curLast.toDouble()).toLong()
        val firstCur = cur.daily.first().date.toEpochDay()
        var explained = 0.0
        var needShift = 0.0
        for (d in cur.daily) {
            val key = d.date.toEpochDay()
            val c = d.contribution.seconds / 60.0
            val oldC = pv["contrib.$key"]
            val w = lambda.pow((curLast - key).toDouble())
            if (oldC == null) {
                val impact = w * c
                explained += impact
                val sleepMin = d.sleep?.value?.seconds?.div(60.0)
                causes += Cause(CauseKind.NIGHT_ENTERED_WINDOW, d.date, impact, enteredText(d, key == curLast, sleepMin, c))
            } else {
                val wOld = lambda.pow((prevLast - key).toDouble())
                val delta = w * c - wOld * oldC
                val oldSleep = pv["sleep.$key"]
                val newSleep = d.sleep?.value?.seconds?.div(60.0)
                val sleepChanged = oldSleep != null && newSleep != null && abs(oldSleep - newSleep) >= 2.0
                if (sleepChanged && abs(delta) >= 1.0) {
                    val edited = key in cur.editedDays && (pv["ue.$key"] ?: 0.0) < 0.5
                    explained += delta
                    causes += Cause(
                        if (edited) CauseKind.USER_EDIT else CauseKind.SOURCE_ADDED, d.date, delta,
                        "The night of ${dateText(d.date)} was ${if (edited) "edited" else "updated"} from ${RecommendationModel.durText(oldSleep!!.roundToLong())} to ${RecommendationModel.durText(newSleep!!.roundToLong())}.",
                    )
                } else {
                    needShift += delta
                }
            }
        }
        for ((k, v) in pv) {
            if (!k.startsWith("contrib.")) continue
            val key = k.removePrefix("contrib.").toLong()
            if (key >= firstCur) continue
            val impact = -lambda.pow((prevLast - key).toDouble()) * v
            explained += impact
            causes += Cause(CauseKind.NIGHT_LEFT_WINDOW, LocalDate.ofEpochDay(key), impact, "${dateText(LocalDate.ofEpochDay(key))} dropped out of your ${p.debtWindowDays.i}-day window")
        }
        val total = cur.debtMin - prevDebt
        if (abs(needShift) >= 1.0) {
            val prevNeed = pv["prev.needMinutes"] ?: cur.needMin
            if (abs(cur.needMin - prevNeed) >= 1.0) {
                explained += needShift
                causes += Cause(CauseKind.NEED_REESTIMATED, null, needShift,
                    "your sleep need moved from ${RecommendationModel.durText(prevNeed.roundToLong())} to ${RecommendationModel.durText(cur.needMin.roundToLong())}")
            }
        }
        val residual = total - explained
        if (abs(residual) >= 10.0) {
            causes += Cause(CauseKind.NIGHT_LEFT_WINDOW, null, residual, "older shortfalls kept fading with time")
        }
        if (sourceAdded) {
            causes += Cause(CauseKind.SOURCE_ADDED, null, null, "a new data source was added")
        }
        if (abs(total) < 1.0 && causes.isEmpty()) return null
        val ranked = causes.sortedByDescending { abs(it.delta ?: 0.0) }
        val lead = when {
            total > 0.5 -> "Debt rose ${RecommendationModel.durText(total.roundToLong())}"
            total < -0.5 -> "Debt fell ${RecommendationModel.durText((-total).roundToLong())}"
            else -> "Debt is unchanged"
        }
        val top = ranked.filter { (it.delta ?: 99.0).let { dd -> abs(dd) >= 5.0 } }.take(3).map { it.text.trimEnd('.') }
        val headline = if (top.isEmpty()) "$lead." else lead + ": " + join(top) + "."
        return Explanation(MetricKey.SLEEP_DEBT, headline, ranked)
    }

    private fun enteredText(d: DailyDebt, last: Boolean, sleepMin: Double?, contribution: Double): String {
        val verb = if (contribution < 0) "repaid ${RecommendationModel.durText((-contribution).roundToLong())}" else "added ${RecommendationModel.durText(contribution.roundToLong())}"
        val who = if (last) "last night's" else "the night of ${dateText(d.date)},"
        return if (d.imputed || sleepMin == null) {
            "$who missing sleep, estimated from your usual ${d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ROOT)}, $verb"
        } else {
            "$who ${RecommendationModel.durText(sleepMin.roundToLong())} of sleep $verb"
        }
    }

    private fun join(parts: List<String>): String = when (parts.size) {
        1 -> parts[0]
        2 -> "${parts[0]}, and ${parts[1]}"
        else -> parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
    }

    private fun dateText(d: LocalDate): String = d.format(DATE)

    companion object {
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    }
}
