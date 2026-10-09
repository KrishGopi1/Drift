package app.drift.testing.fixtures

import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.Reentrainment
import app.drift.model.engine.SufficiencyTier
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import kotlin.math.sin

/** Deterministic nightly sleep durations (hours, oldest first) for fixtures. No randomness. */
object NightPatterns {
    /** [n] nights ending on the context's today; free-day (Sat/Sun wake) nights use [free]. */
    fun hours(ctx: FixtureContext, n: Int, workday: Double, free: Double, wiggle: Double = 0.3): List<Double> =
        (0 until n).map { i ->
            val date = ctx.today.minusDays((n - 1 - i).toLong())
            val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            Math.round(((if (weekend) free else workday) + wiggle * sin(i * 1.7)) * 12) / 12.0
        }
}

/**
 * Ready-made [AnalysisSnapshot]s for every UI situation in plan 4.9 / 6.2.
 *
 * ```
 * val snap = SnapshotFixtures.highDebt()            // IST, 2026-10-14
 * val us = SnapshotFixtures.full(FixtureContext.US_EASTERN)
 * val everything = SnapshotFixtures.all()
 * ```
 */
object SnapshotFixtures {
    private fun eveningNow(ctx: FixtureContext): FixtureContext =
        ctx.copy(now = ctx.today.atTime(18, 30).atZone(ctx.zone).toInstant())

    /** Day 0: curve from questionnaire + population priors only. Carries first-run explanations. */
    fun priorsOnlyDay0(ctx: FixtureContext = FixtureContext.IST) = SnapshotBuilder.build(
        SnapshotSpec(ctx, SufficiencyTier.PRIORS_ONLY, emptyList(), needHours = 8.0, explanations = ExplanationStyle.FIRST_RUN),
    )

    /** Two nights: debt with a wide band, need still the prior. */
    fun early(ctx: FixtureContext = FixtureContext.IST) = SnapshotBuilder.build(
        SnapshotSpec(ctx, SufficiencyTier.EARLY, listOf(6.5, 7.0), needHours = 8.0),
    )

    /** Eight nights: personal need begins, regularity available, chronotype not yet. */
    fun personalizing(ctx: FixtureContext = FixtureContext.IST) = SnapshotBuilder.build(
        SnapshotSpec(ctx, SufficiencyTier.PERSONALIZING, NightPatterns.hours(ctx, 8, 7.3, 8.2), needHours = 7.6),
    )

    /** Thirty nights, steady sleeper with mild weekday shortfall. */
    fun full(ctx: FixtureContext = FixtureContext.IST) = SnapshotBuilder.build(
        SnapshotSpec(ctx, SufficiencyTier.FULL, NightPatterns.hours(ctx, 30, 7.6, 8.4)),
    )

    /** Chronic short sleeping: debt at the HIGH band. */
    fun highDebt(ctx: FixtureContext = FixtureContext.IST) = SnapshotBuilder.build(
        SnapshotSpec(ctx, SufficiencyTier.FULL, NightPatterns.hours(ctx, 30, 6.5, 7.4)),
    )

    /** Three consecutive missing nights: debt and band are null, debt not available. */
    fun debtUnavailable(ctx: FixtureContext = FixtureContext.IST): AnalysisSnapshot {
        val hours = NightPatterns.hours(ctx, 20, 7.4, 8.3).toMutableList<Double?>()
        for (i in 14..16) hours[i] = null
        return SnapshotBuilder.build(SnapshotSpec(ctx, SufficiencyTier.FULL, hours))
    }

    /** Night-shift worker: wakes 16:00, shift-work mode on. "Now" is 18:30 local so the day is under way. */
    fun shiftWork(ctx: FixtureContext = FixtureContext.IST): AnalysisSnapshot {
        val c = eveningNow(ctx)
        return SnapshotBuilder.build(
            SnapshotSpec(
                c, SufficiencyTier.FULL, NightPatterns.hours(c, 21, 7.3, 7.3),
                needHours = 7.5, wakeTime = LocalTime.of(16, 0), freeDayWakeShift = Duration.ZERO, shiftWork = true,
            ),
        )
    }

    /** Eastbound travel, body clock 3 h behind local time with three days to go. Bed precedes the melatonin window. */
    fun travelEastward(ctx: FixtureContext = FixtureContext.IST) = SnapshotBuilder.build(
        SnapshotSpec(
            ctx, SufficiencyTier.FULL, NightPatterns.hours(ctx, 21, 7.5, 8.2),
            dlmoOffsetMinutes = 180.0,
            reentrainment = Reentrainment(Duration.ofHours(3), eastward = true, estimatedDaysLeft = 3),
        ),
    )

    /** Westbound travel, body clock 2 h ahead of local time. */
    fun travelWestward(ctx: FixtureContext = FixtureContext.IST) = SnapshotBuilder.build(
        SnapshotSpec(
            ctx, SufficiencyTier.FULL, NightPatterns.hours(ctx, 21, 7.5, 8.2),
            dlmoOffsetMinutes = -120.0,
            reentrainment = Reentrainment(Duration.ofHours(2), eastward = false, estimatedDaysLeft = 2),
        ),
    )

    /** Circadian amplitude so low that no dip clears the prominence threshold. */
    fun flatCurve(ctx: FixtureContext = FixtureContext.IST) = SnapshotBuilder.build(
        SnapshotSpec(ctx, SufficiencyTier.FULL, NightPatterns.hours(ctx, 30, 7.6, 8.4), circadianScale = 0.15),
    )

    /** Late body clock: computed bedtime lands before the melatonin window, so a trade-off is shown. */
    fun withTradeOff(ctx: FixtureContext = FixtureContext.IST) = SnapshotBuilder.build(
        SnapshotSpec(ctx, SufficiencyTier.FULL, NightPatterns.hours(ctx, 30, 7.6, 8.4), dlmoOffsetMinutes = 150.0),
    )

    /** Same as [full]: bedtime sits after the melatonin window opens, so there is no trade-off. */
    fun withoutTradeOff(ctx: FixtureContext = FixtureContext.IST) = full(ctx)

    /** Moderate debt plus an afternoon dip: a nap is advised. */
    fun withNap(ctx: FixtureContext = FixtureContext.IST) = SnapshotBuilder.build(
        SnapshotSpec(ctx, SufficiencyTier.FULL, NightPatterns.hours(ctx, 30, 7.3, 8.4)),
    )

    /** Day-0 snapshot whose explanations are the first-run lines. */
    fun firstRunExplanation(ctx: FixtureContext = FixtureContext.IST) = priorsOnlyDay0(ctx)

    /** Thirty nights, 12 calibration check-ins and a multi-cause "why this changed" for debt and energy. */
    fun richExplanation(ctx: FixtureContext = FixtureContext.IST) = SnapshotBuilder.build(
        SnapshotSpec(
            ctx, SufficiencyTier.FULL, NightPatterns.hours(ctx, 30, 7.5, 8.6),
            calibrationCheckIns = 12, explanations = ExplanationStyle.RICH,
        ),
    )

    /** Every named fixture for [ctx], in a stable order. Keys are stable screenshot names. */
    fun all(ctx: FixtureContext = FixtureContext.IST): Map<String, AnalysisSnapshot> = linkedMapOf(
        "priors_only_day0" to priorsOnlyDay0(ctx),
        "early_2_nights" to early(ctx),
        "personalizing_8_nights" to personalizing(ctx),
        "full_30_nights" to full(ctx),
        "high_debt" to highDebt(ctx),
        "debt_unavailable" to debtUnavailable(ctx),
        "shift_work" to shiftWork(ctx),
        "travel_eastward" to travelEastward(ctx),
        "travel_westward" to travelWestward(ctx),
        "flat_curve" to flatCurve(ctx),
        "with_trade_off" to withTradeOff(ctx),
        "without_trade_off" to withoutTradeOff(ctx),
        "with_nap" to withNap(ctx),
        "first_run_explanation" to firstRunExplanation(ctx),
        "rich_explanation" to richExplanation(ctx),
    )
}
