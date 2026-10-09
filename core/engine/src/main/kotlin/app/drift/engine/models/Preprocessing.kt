package app.drift.engine.models

import app.drift.engine.ParameterSet
import app.drift.engine.internal.DAY_MIN_L
import app.drift.engine.internal.NightRecord
import app.drift.engine.internal.Preprocessed
import app.drift.engine.internal.ProcEpisode
import app.drift.engine.internal.Span
import app.drift.engine.internal.offsetMin
import app.drift.model.EpisodeKind
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.Signal
import app.drift.model.SleepEpisode
import app.drift.model.SleepStage
import app.drift.model.SourceId
import app.drift.model.engine.EngineInput
import java.time.ZoneId

/**
 * Model 4.1: merge fragments, classify MAIN/NAP, assign sleep days, flag implausible episodes
 * (excluded unless user-confirmed), detect shift-work mode and compute quality weights.
 */
internal class Preprocessor(private val p: ParameterSet) {

    private class Raw(
        val id: String,
        val startAbs: Long,
        val endAbs: Long,
        val zone: ZoneId,
        val asleep: List<Span>,
        val weight: Double,
        val confirmed: Boolean,
        val edited: Boolean,
        val source: SourceId,
        val kind: EpisodeKind,
        val tier: QualityTier,
        val se: Double?,
        val recorded: Boolean,
    )

    fun run(input: EngineInput): Preprocessed {
        val nowAbs = Math.floorDiv(input.now.epochSecond, 60L)
        val nowLocal = nowAbs + offsetMin(input.zone, nowAbs)
        val todayDay = Math.floorDiv(nowLocal, DAY_MIN_L)

        var excluded = 0
        val raws = ArrayList<Raw>()
        for (e in input.episodes.sortedWith(compareBy({ it.start }, { it.end }, { it.id.value }))) {
            val raw = toRaw(e)
            if (raw == null) continue
            val inFuture = raw.endAbs > nowAbs
            if (inFuture && !raw.confirmed) { excluded++; continue }
            raws += raw
        }

        val merged = mergeFragments(raws)
        val byDay = merged.groupBy { it.sleepDay }.toSortedMap()
        val nights = ArrayList<NightRecord>()
        for ((day, eps) in byDay) {
            val mainIdx = pickMain(day, eps)
            var main: ProcEpisode? = null
            var excludedMain: ProcEpisode? = null
            val naps = ArrayList<ProcEpisode>()
            for ((i, ep) in eps.withIndex()) {
                if (i == mainIdx) {
                    if (isPlausibleMain(ep)) main = ep else { excludedMain = ep; excluded++ }
                } else naps += ep
            }
            nights += NightRecord(day, main, naps, excludedMain)
        }
        val nightMap = nights.associateBy { it.day }
        val nowHour = Math.floorMod(nowLocal, DAY_MIN_L) / 60.0
        val lastDay = if (nightMap[todayDay]?.main != null || nowHour >= p.lastDayCutoffHour.v) todayDay else todayDay - 1

        val lookback = p.shiftLookbackDays.i
        val dayShift = HashSet<Long>()
        for (d in (lastDay - lookback + 1)..lastDay) {
            val m = nightMap[d]?.main ?: continue
            if (daytimeFraction(m) >= p.shiftOverlapFraction.v) dayShift += d
        }
        val shiftMode = dayShift.size >= p.shiftMinDays.i
        val shiftNights = if (shiftMode) {
            nights.mapNotNull { n -> n.main?.takeIf { daytimeFraction(it) >= p.shiftOverlapFraction.v }?.let { n.day } }.toSet()
        } else emptySet()

        val alarms = input.signals.signals.filterIsInstance<Signal.AlarmEvent>()
            .map { Math.floorDiv(it.at.epochSecond, 60L) }
            .sorted()

        return Preprocessed(nights, nightMap, todayDay, lastDay, nowLocal, nowAbs, shiftMode, shiftNights, alarms, excluded)
    }

    private fun toRaw(e: SleepEpisode): Raw? {
        val s = Math.floorDiv(e.start.epochSecond, 60L)
        val en = Math.floorDiv(e.end.epochSecond, 60L)
        if (en <= s) return null
        val awake = e.stages.orEmpty().filter { it.stage == SleepStage.AWAKE }.mapNotNull { st ->
            val a = maxOf(Math.floorDiv(st.interval.start.epochSecond, 60L), s)
            val b = minOf(Math.floorDiv(st.interval.end.epochSecond, 60L), en)
            if (b > a) Span(a, b) else null
        }
        val asleep = subtract(Span(s, en), awake)
        if (asleep.isEmpty()) return null
        val q = e.quality
        val confirmed = QualityFlag.USER_CONFIRMED in q.flags
        val edited = e.userEdited
        var w = p.tierWeight(e.origin.qualityTier) *
            (p.completenessFloor.v + (1 - p.completenessFloor.v) * q.completeness) *
            (0.5 + 0.5 * q.detectionConfidence)
        if (confirmed || edited) w *= p.userConfirmedBoost.v
        val se = e.inBed?.let { ib ->
            val a = Math.floorDiv(ib.start.epochSecond, 60L)
            val b = Math.floorDiv(ib.end.epochSecond, 60L)
            if (b > a) {
                val inside = asleep.sumOf { sp -> maxOf(0L, minOf(sp.end, b) - maxOf(sp.start, a)) }
                (inside.toDouble() / (b - a)).coerceIn(0.0, 1.0)
            } else null
        }
        return Raw(
            e.id.value, s, en, e.zone, asleep, w, confirmed, edited, e.origin.sourceId, e.kind,
            e.origin.qualityTier, se, q.detectionConfidence >= 1.0 && q.completeness >= 1.0,
        )
    }

    private fun subtract(whole: Span, holes: List<Span>): List<Span> {
        val out = ArrayList<Span>()
        var cur = whole.start
        for (h in holes.sortedBy { it.start }) {
            if (h.start > cur) out += Span(cur, minOf(h.start, whole.end))
            cur = maxOf(cur, h.end)
            if (cur >= whole.end) break
        }
        if (cur < whole.end) out += Span(cur, whole.end)
        return out.filter { it.length > 0 }
    }

    private fun union(spans: List<Span>): List<Span> {
        val out = ArrayList<Span>()
        for (s in spans.sortedBy { it.start }) {
            val last = out.lastOrNull()
            if (last != null && s.start <= last.end) {
                out[out.size - 1] = Span(last.start, maxOf(last.end, s.end))
            } else out += s
        }
        return out
    }

    private fun mergeFragments(raws: List<Raw>): List<ProcEpisode> {
        if (raws.isEmpty()) return emptyList()
        val clusters = ArrayList<MutableList<Raw>>()
        var curEnd = Long.MIN_VALUE
        for (r in raws) {
            val cur = clusters.lastOrNull()
            if (cur != null && r.startAbs - curEnd < p.mergeGapMin.v) {
                cur += r
                curEnd = maxOf(curEnd, r.endAbs)
            } else {
                clusters += mutableListOf(r)
                curEnd = r.endAbs
            }
        }
        return clusters.map { build(it) }
    }

    private fun build(c: List<Raw>): ProcEpisode {
        val first = c.first()
        val last = c.maxByOrNull { it.endAbs }!!
        val startAbs = c.minOf { it.startAbs }
        val endAbs = c.maxOf { it.endAbs }
        val spans = union(c.flatMap { it.asleep })
        val tst = spans.sumOf { it.length }.toDouble()
        val offStart = offsetMin(first.zone, startAbs)
        val offEnd = offsetMin(last.zone, endAbs)
        val local = spans.map { sp ->
            val o = offsetMin(first.zone, sp.start)
            Span(sp.start + o, sp.end + o)
        }
        val wSum = c.sumOf { it.weight * (it.asleep.sumOf { s -> s.length }) }
        val tSum = c.sumOf { it.asleep.sumOf { s -> s.length } }.toDouble()
        val seSamples = c.filter { it.se != null }
        val se = if (seSamples.isEmpty()) null else seSamples.sumOf { it.se!! } / seSamples.size
        val forced = when {
            c.any { it.edited && it.kind == EpisodeKind.MAIN } -> EpisodeKind.MAIN
            c.any { it.edited && it.kind == EpisodeKind.NAP } -> EpisodeKind.NAP
            else -> null
        }
        return ProcEpisode(
            ids = c.map { it.id },
            startAbs = startAbs,
            endAbs = endAbs,
            zone = last.zone,
            localStart = startAbs + offStart,
            localEnd = endAbs + offEnd,
            asleepLocal = local,
            tst = tst,
            weight = if (tSum > 0) wSum / tSum else 0.5,
            userConfirmed = c.any { it.confirmed },
            userEdited = c.any { it.edited },
            sources = c.map { it.source }.toSet(),
            forcedKind = forced,
            bestTier = c.maxOf { it.tier },
            sleepEfficiency = se,
            allRecorded = c.all { it.recorded },
        )
    }

    /** Index of the MAIN episode of a sleep day, or -1. */
    private fun pickMain(day: Long, eps: List<ProcEpisode>): Int {
        val winStart = (day - 1) * DAY_MIN_L + p.nightWindowStartMin.i
        val winEnd = day * DAY_MIN_L + p.nightWindowEndMin.i
        fun best(cands: List<Int>): Int = cands.maxWithOrNull(
            compareBy<Int>({ eps[it].tst }, { -eps[it].localStart }),
        ) ?: -1
        val forcedMain = eps.indices.filter { eps[it].forcedKind == EpisodeKind.MAIN }
        if (forcedMain.isNotEmpty()) return best(forcedMain)
        val notNap = eps.indices.filter { eps[it].forcedKind != EpisodeKind.NAP }
        val inWindow = notNap.filter { eps[it].localStart < winEnd && eps[it].localEnd > winStart }
        if (inWindow.isNotEmpty()) return best(inWindow)
        val longest = best(notNap)
        return if (longest >= 0 && eps[longest].tst >= p.implausibleMinMin.v) longest else -1
    }

    private fun isPlausibleMain(e: ProcEpisode): Boolean =
        e.userConfirmed || (e.tst >= p.implausibleMinMin.v && e.spanMin <= p.implausibleMaxMin.v)

    private fun daytimeFraction(e: ProcEpisode): Double = daytimeFraction(e, p)

    companion object {
        /** Fraction of the episode lying inside the daytime band (08:00-18:00 local). */
        fun daytimeFraction(e: ProcEpisode, p: ParameterSet): Double {
            val total = (e.localEnd - e.localStart).toDouble()
            if (total <= 0) return 0.0
            var inside = 0L
            var day = Math.floorDiv(e.localStart, DAY_MIN_L)
            val lastDay = Math.floorDiv(e.localEnd, DAY_MIN_L)
            while (day <= lastDay) {
                val a = day * DAY_MIN_L + p.shiftDayStartMin.i
                val b = day * DAY_MIN_L + p.shiftDayEndMin.i
                inside += maxOf(0L, minOf(e.localEnd, b) - maxOf(e.localStart, a))
                day++
            }
            return inside / total
        }
    }

}
