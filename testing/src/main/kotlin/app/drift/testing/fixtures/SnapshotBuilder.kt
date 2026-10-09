package app.drift.testing.fixtures

import app.drift.model.Confidence
import app.drift.model.ConfidenceLevel
import app.drift.model.Interval
import app.drift.model.Provenance
import app.drift.model.Quantity
import app.drift.model.SourceId
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.CircadianEstimate
import app.drift.model.engine.Chronotype
import app.drift.model.engine.ChronotypeLabel
import app.drift.model.engine.DailyDebt
import app.drift.model.engine.DayCurve
import app.drift.model.engine.DebtBand
import app.drift.model.engine.EnergyCurve
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.EngineState
import app.drift.model.engine.EngineVersion
import app.drift.model.engine.HabitCard
import app.drift.model.engine.HabitKind
import app.drift.model.engine.NapAdvice
import app.drift.model.engine.Recommendations
import app.drift.model.engine.Reentrainment
import app.drift.model.engine.RegularityReport
import app.drift.model.engine.Sufficiency
import app.drift.model.engine.SufficiencyTier
import app.drift.model.engine.TradeOff
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Which explanation set a fixture carries. */
enum class ExplanationStyle { NONE, FIRST_RUN, RICH }

/**
 * Everything [SnapshotBuilder] needs. Defaults describe a healthy "full" snapshot.
 *
 * @property nightHours asleep hours per night, oldest first; the last entry is the night that ended on
 *   [FixtureContext.today]. `null` = no data for that night.
 */
data class SnapshotSpec(
    val ctx: FixtureContext = FixtureContext.IST,
    val tier: SufficiencyTier = SufficiencyTier.FULL,
    val nightHours: List<Double?> = emptyList(),
    val needHours: Double = 7.75,
    /** Weekday (workday) wake time; free days wake [freeDayWakeShift] later. */
    val wakeTime: LocalTime = LocalTime.of(7, 0),
    val freeDayWakeShift: Duration = Duration.ofMinutes(75),
    /** Null = two hours before the recommended bedtime. */
    val dlmoMinutes: Double? = null,
    /** Added to the default DLMO (bedtime minus 2 h); positive = body clock later than the schedule. */
    val dlmoOffsetMinutes: Double = 0.0,
    val shiftWork: Boolean = false,
    val reentrainment: Reentrainment? = null,
    /** Scales the circadian process; ~0.2 yields a curve too flat for a dip. */
    val circadianScale: Double = 1.0,
    val calibrationCheckIns: Int = 0,
    val explanations: ExplanationStyle = ExplanationStyle.NONE,
    val computedAt: Instant = ctx.now,
)

/**
 * Produces internally consistent [AnalysisSnapshot]s without the engine. Invariants (verified by tests):
 * debt trend rows sum to the debt, curve segments tile each day, ESTIMATED values carry bands,
 * the nap advice exists only with debt >= MODERATE and a dip, the trade-off exists only when bedtime precedes DLMO.
 */
object SnapshotBuilder {
    val ENGINE_VERSION = EngineVersion(1, 0, 0)
    private val HC = setOf(SourceId.HEALTH_CONNECT)
    private const val SLEEP_EFFICIENCY = 0.88
    /** Plan 4.3: surplus repays at eta = 0.6; the leak lambda gives a 7-day half-life. */
    private const val ETA = 0.6
    private val LAMBDA = Math.pow(0.5, 1.0 / 7.0)
    private const val WINDOW_NIGHTS = 14

    fun build(spec: SnapshotSpec): AnalysisSnapshot {
        val ctx = spec.ctx
        val zone = ctx.zone
        val today = ctx.today
        val tier = spec.tier
        val need = hours(spec.needHours)
        val nights = spec.nightHours
        val n = nights.size
        val firstDate = today.minusDays((n - 1).toLong())
        fun dateOf(i: Int) = firstDate.plusDays(i.toLong())
        fun wakeFor(d: LocalDate): LocalTime =
            if (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) spec.wakeTime.plus(spec.freeDayWakeShift) else spec.wakeTime
        fun wakeInstant(d: LocalDate) = d.atTime(wakeFor(d)).atZone(zone).toInstant()

        // ---- sleep need
        val needBand = when (tier) {
            SufficiencyTier.PRIORS_ONLY, SufficiencyTier.EARLY -> Triple(1.0, ConfidenceLevel.LOW, 0.9)
            SufficiencyTier.PERSONALIZING -> Triple(0.8, ConfidenceLevel.MEDIUM, 0.9)
            SufficiencyTier.FULL -> Triple(0.35, ConfidenceLevel.HIGH, 0.9)
        }
        val sources = if (tier == SufficiencyTier.PRIORS_ONLY) emptySet() else HC
        val sleepNeed = Quantity(
            need, Provenance.ESTIMATED,
            Confidence(hours(spec.needHours - needBand.first), hours(spec.needHours + needBand.first), needBand.third, needBand.second),
            sources,
        )

        // ---- debt trend (last 14 nights), contributions are effective deltas so rows sum to the debt
        val windowStart = max(0, n - WINDOW_NIGHTS)
        val trend = mutableListOf<DailyDebt>()
        var debt = Duration.ZERO
        for (i in windowStart until n) {
            val h = nights[i]
            // D_t = lambda*D_(t-1) + (N-S) on a shortfall, lambda*D_(t-1) - eta*(S-N) on a surplus, floored at 0.
            val raw = if (h == null) Duration.ZERO else need.minus(hours(h))
            val leaked = Duration.ofSeconds(Math.round(debt.seconds * LAMBDA))
            val step = if (raw.isNegative) Duration.ofSeconds(Math.round(raw.seconds * ETA)) else raw
            val after = (leaked + step).coerceIn(Duration.ZERO, Duration.ofDays(1))
            trend += DailyDebt(
                date = dateOf(i),
                sleep = h?.let { Quantity(hours(it), Provenance.MEASURED, sourceIds = HC) },
                need = need,
                contribution = after - debt,
                debtAfter = after,
                imputed = h == null,
            )
            debt = after
        }
        val longestGap = trend.fold(0 to 0) { (cur, best), r -> val c = if (r.imputed) cur + 1 else 0; c to max(best, c) }.second
        val debtAvailable = trend.isNotEmpty() && longestGap < 3
        val debtBandWidth = when (tier) {
            SufficiencyTier.PRIORS_ONLY, SufficiencyTier.EARLY -> 2.0 to ConfidenceLevel.LOW
            SufficiencyTier.PERSONALIZING -> 1.2 to ConfidenceLevel.MEDIUM
            SufficiencyTier.FULL -> 0.6 to ConfidenceLevel.HIGH
        }
        val sleepDebt = if (!debtAvailable) null else Quantity(
            debt, Provenance.ESTIMATED,
            Confidence(
                maxOf(Duration.ZERO, debt - hours(debtBandWidth.first)), debt + hours(debtBandWidth.first),
                0.9, debtBandWidth.second,
            ),
            HC,
        )
        val band = sleepDebt?.let { bandFor(it.value) }

        // ---- recommendations anchored on the next wake
        val nextWakeDate = today.plusDays(1)
        val wakeNext = wakeInstant(nextWakeDate)
        val extra = minOf(hours(0.25 * (sleepDebt?.value?.toHoursDouble() ?: 0.0)), Duration.ofHours(1))
        val tibMinutes = ((need + extra).toMinutes() / SLEEP_EFFICIENCY).toLong()
        val tib = Duration.ofMinutes(tibMinutes)
        val bedtime = wakeNext.minus(tib)
        val dlmoMinutes = spec.dlmoMinutes ?: run {
            val l = bedtime.minus(Duration.ofHours(2)).atZone(zone).toLocalTime()
            ((l.minutesOfDay() + spec.dlmoOffsetMinutes) % 1440 + 1440) % 1440
        }

        // ---- sleep timeline for the curve: 14 nights history (missing -> assumed need), tonight and tomorrow night
        val curveStart = today.minusDays(WINDOW_NIGHTS.toLong())
        val sleeps = mutableListOf<Interval>()
        var d = curveStart
        while (!d.isAfter(today)) {
            val idx = n - 1 - java.time.temporal.ChronoUnit.DAYS.between(d, today).toInt()
            val h = nights.getOrNull(idx) ?: spec.needHours
            val w = wakeInstant(d)
            sleeps += Interval(w.minus(hours(h)), w)
            d = d.plusDays(1)
        }
        sleeps += Interval(bedtime, wakeNext)
        val wakeAfter = wakeInstant(nextWakeDate.plusDays(1))
        sleeps += Interval(wakeAfter.minus(tib), wakeAfter)

        val model = EnergyModel(zone, sleeps, dlmoMinutes, spec.circadianScale)
        val curveConfidence = when (tier) {
            SufficiencyTier.PRIORS_ONLY, SufficiencyTier.EARLY -> ConfidenceLevel.LOW
            SufficiencyTier.PERSONALIZING -> ConfidenceLevel.MEDIUM
            SufficiencyTier.FULL -> if (spec.calibrationCheckIns >= 10) ConfidenceLevel.HIGH else ConfidenceLevel.MEDIUM
        }
        val days = listOf(today.minusDays(1), today, today.plusDays(1)).map {
            EnergyCurveFactory.dayCurve(model, it, curveConfidence)
        }
        val todayCurve = days[1]
        val energy = EnergyCurve(days, spec.calibrationCheckIns)

        // ---- circadian
        val dlmoBand = when (tier) {
            SufficiencyTier.PRIORS_ONLY -> 120.0 to ConfidenceLevel.LOW
            SufficiencyTier.EARLY -> 100.0 to ConfidenceLevel.LOW
            SufficiencyTier.PERSONALIZING -> 60.0 to ConfidenceLevel.MEDIUM
            SufficiencyTier.FULL -> 35.0 to ConfidenceLevel.HIGH
        }
        val dlmoQ = Quantity(
            dlmoMinutes, Provenance.ESTIMATED,
            Confidence(dlmoMinutes - dlmoBand.first, dlmoBand.first + dlmoMinutes, 0.9, dlmoBand.second), sources,
        )
        val upcoming = (days.filter { it.date >= today }.flatMap { it.segments })
            .filter { it.phase == EnergyPhase.MELATONIN_WINDOW && it.interval.end.isAfter(ctx.now) }
        val melatonin = upcoming.firstOrNull()?.interval
        val windDownStart = days.filter { it.date >= today }.flatMap { it.segments }
            .firstOrNull { it.phase == EnergyPhase.WIND_DOWN && it.interval.end.isAfter(ctx.now) }?.interval?.start
        val circadian = CircadianEstimate(dlmoQ, melatonin, windDownStart, spec.shiftWork, spec.reentrainment)

        // ---- recommendations
        val tonightDlmo = EnergyCurveFactory.dlmoAtOrAfter(model, ctx.now)
        val tradeOff = if (bedtime.isBefore(tonightDlmo)) {
            val follow = tonightDlmo.plus(Duration.ofMinutes(15))
            TradeOff(
                "Your plan puts you in bed at ${clock(bedtime, spec)}, before your melatonin window opens at ${clock(tonightDlmo, spec)}. " +
                    "Bed at ${clock(follow, spec)} follows the window but leaves ${fmt(Duration.between(follow, wakeNext))} in bed.",
                follow,
            )
        } else null
        val timeBand = when (tier) {
            SufficiencyTier.PRIORS_ONLY, SufficiencyTier.EARLY -> 60L to ConfidenceLevel.LOW
            SufficiencyTier.PERSONALIZING -> 35L to ConfidenceLevel.MEDIUM
            SufficiencyTier.FULL -> 15L to ConfidenceLevel.HIGH
        }
        fun instantQ(t: Instant) = Quantity(
            t, Provenance.ESTIMATED,
            Confidence(t.minus(Duration.ofMinutes(timeBand.first)), t.plus(Duration.ofMinutes(timeBand.first)), 0.9, timeBand.second),
            sources,
        )
        val opportunity = Quantity(
            tib, Provenance.ESTIMATED,
            Confidence(tib - Duration.ofMinutes(timeBand.first), tib + Duration.ofMinutes(timeBand.first), 0.9, timeBand.second),
            sources,
        )
        val habits = listOf(
            HabitCard(HabitKind.MORNING_LIGHT, Interval(wakeNext, wakeNext.plus(Duration.ofHours(1))), "Wright2013"),
            HabitCard(HabitKind.EXERCISE_WINDOW, Interval(bedtime.minus(Duration.ofHours(9)), windDownStart?.minus(Duration.ofHours(0)) ?: bedtime.minus(Duration.ofHours(3))), "Youngstedt2019"),
            HabitCard(HabitKind.CAFFEINE_CUTOFF, Interval(bedtime.minus(Duration.ofHours(8)), bedtime), "Drake2013"),
            HabitCard(HabitKind.LAST_MEAL, Interval(bedtime.minus(Duration.ofHours(3)), bedtime), "Sutton2018"),
            HabitCard(HabitKind.ALCOHOL_CUTOFF, Interval(bedtime.minus(Duration.ofHours(3)), bedtime), "Ebrahim2013"),
            HabitCard(HabitKind.DIM_LIGHTS, Interval(windDownStart ?: bedtime.minus(Duration.ofHours(1)), bedtime), "Gooley2011"),
        )
        val dip = todayCurve.segments.firstOrNull { it.phase == EnergyPhase.AFTERNOON_DIP }
        val nap = if (band != null && band != DebtBand.LOW && dip != null) {
            val latestEnd = bedtime.minus(Duration.ofHours(7))
            val napStart = dip.interval.start
            val napEnd = minOf(dip.interval.end, latestEnd)
            if (Duration.between(napStart, napEnd) >= Duration.ofMinutes(20)) {
                NapAdvice(Interval(napStart, napEnd), Duration.ofMinutes(25), "Afternoon dip with a ${fmt(debt)} sleep debt; ends at least 7 h before bed.")
            } else null
        } else null
        val recommendations = Recommendations(opportunity, instantQ(bedtime), instantQ(wakeNext), tradeOff, habits, nap)

        // ---- regularity and chronotype
        val intervals = nights.mapIndexedNotNull { i, h ->
            h?.let { Interval(wakeInstant(dateOf(i)).minus(hours(it)), wakeInstant(dateOf(i))) to dateOf(i) }
        }
        val regularity = regularity(intervals, zone, tier, spec.needHours, sources)

        val informative = nights.count { it != null }
        val sufficiency = Sufficiency(
            tier = tier,
            daysOfHistory = if (tier == SufficiencyTier.PRIORS_ONLY) 0 else n,
            informativeNights = informative,
            nightsUntilPersonalNeed = if (tier == SufficiencyTier.PRIORS_ONLY || tier == SufficiencyTier.EARLY) max(0, 5 - informative) else 0,
            debtAvailable = debtAvailable,
            regularityAvailable = regularity.sri7 != null,
        )

        val snapshotWithoutExplanations = AnalysisSnapshot(
            engineVersion = ENGINE_VERSION,
            inputHash = "fixture-${tier.name.lowercase()}-$n-${spec.needHours}-${spec.ctx.now.epochSecond}",
            computedAt = spec.computedAt,
            sleepNeed = sleepNeed,
            sleepDebt = sleepDebt,
            debtBand = band,
            debtTrend = trend,
            regularity = regularity,
            circadian = circadian,
            energyCurve = energy,
            recommendations = recommendations,
            explanations = emptyList(),
            state = EngineState(ENGINE_VERSION, spec.ctx.now, mapOf("need" to spec.needHours, "debt" to debt.toHoursDouble())),
            dataSufficiency = sufficiency,
        )
        return snapshotWithoutExplanations.copy(explanations = Explanations.build(spec.explanations, snapshotWithoutExplanations, spec))
    }

    fun bandFor(debt: Duration): DebtBand = when {
        debt < Duration.ofHours(5) -> DebtBand.LOW
        debt <= Duration.ofHours(10) -> DebtBand.MODERATE
        else -> DebtBand.HIGH
    }

    private fun regularity(
        nights: List<Pair<Interval, LocalDate>>,
        zone: java.time.ZoneId,
        tier: SufficiencyTier,
        needHours: Double,
        sources: Set<SourceId>,
    ): RegularityReport {
        if (nights.size < 5) return RegularityReport(null, null, null, null, null, null, null)
        fun onset(iv: Interval) = iv.start.atZone(zone).toLocalTime().minutesOfDay().let { if (it < 720) it + 1440 else it }
        fun wake(iv: Interval) = iv.end.atZone(zone).toLocalTime().minutesOfDay()
        fun sri(list: List<Pair<Interval, LocalDate>>): Double {
            val diffs = list.zipWithNext().map { (a, b) -> abs(onset(a.first) - onset(b.first)) + abs(wake(a.first) - wake(b.first)) }
            return (100 - 200 * diffs.average() / 1440).coerceIn(0.0, 100.0)
        }
        fun std(xs: List<Double>): Double {
            val m = xs.average(); return sqrt(xs.sumOf { (it - m) * (it - m) } / xs.size)
        }
        fun derived(v: Double) = Quantity(v, Provenance.DERIVED, sourceIds = sources)
        fun derivedDur(minutes: Double) = Quantity(Duration.ofMinutes(minutes.toLong()), Provenance.DERIVED, sourceIds = sources)
        val last7 = nights.takeLast(7)
        val last14 = nights.takeLast(14)
        val trend = (6 until nights.size).map { i -> nights[i].second to sri(nights.subList(i - 6, i + 1)) }.takeLast(14)
        val mid = { iv: Interval -> (onset(iv) + wake(iv) + 1440) / 2 }
        val free = nights.filter { it.second.dayOfWeek == DayOfWeek.SATURDAY || it.second.dayOfWeek == DayOfWeek.SUNDAY }
        val work = nights - free.toSet()
        val social = if (free.size >= 2 && work.size >= 2)
            derivedDur(abs(free.map { mid(it.first) }.average() - work.map { mid(it.first) }.average())) else null
        val chronotype = if (tier == SufficiencyTier.FULL && free.size >= 4) {
            val freeSleep = free.map { it.first.duration.toMinutes().toDouble() }.average()
            val avgSleep = nights.map { it.first.duration.toMinutes().toDouble() }.average()
            val msf = free.map { mid(it.first) % 1440 }.average()
            val msfsc = msf - 0.5 * max(0.0, freeSleep - avgSleep)
            Chronotype(
                Quantity(msfsc, Provenance.ESTIMATED, Confidence(msfsc - 25, msfsc + 25, 0.9, ConfidenceLevel.MEDIUM), sources),
                when { msfsc < 210 -> ChronotypeLabel.EARLY; msfsc > 300 -> ChronotypeLabel.LATE; else -> ChronotypeLabel.INTERMEDIATE },
                free.size,
                max(0.0, 1.0 - free.size / 8.0),
            )
        } else null
        return RegularityReport(
            sri7 = derived(sri(last7)),
            sri14 = if (nights.size >= 14) derived(sri(last14)) else null,
            onsetSpread = derivedDur(std(last14.map { onset(it.first) })),
            wakeSpread = derivedDur(std(last14.map { wake(it.first) })),
            midpointSpread = derivedDur(std(last14.map { mid(it.first) })),
            socialJetlag = social,
            chronotype = chronotype,
            trend = trend,
        )
    }

    internal fun fmt(d: Duration): String {
        val m = d.toMinutes()
        return when {
            m >= 60 && m % 60 != 0L -> "${m / 60} h ${m % 60} min"
            m >= 60 -> "${m / 60} h"
            else -> "$m min"
        }
    }

    internal fun clock(t: Instant, spec: SnapshotSpec): String =
        DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(t.atZone(spec.ctx.zone))
}

private fun Duration.coerceIn(lo: Duration, hi: Duration): Duration = if (this < lo) lo else if (this > hi) hi else this
