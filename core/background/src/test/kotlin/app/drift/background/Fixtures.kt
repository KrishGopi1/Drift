package app.drift.background

import app.drift.model.ConfidenceLevel
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.Provenance
import app.drift.model.QualityTier
import app.drift.model.Quantity
import app.drift.model.SleepEpisode
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.CircadianEstimate
import app.drift.model.engine.DayCurve
import app.drift.model.engine.EnergyCurve
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.EnergyPoint
import app.drift.model.engine.EnergySegment
import app.drift.model.engine.EngineState
import app.drift.model.engine.EngineVersion
import app.drift.model.engine.HabitCard
import app.drift.model.engine.HabitKind
import app.drift.model.engine.Recommendations
import app.drift.model.engine.RegularityReport
import app.drift.model.engine.Sufficiency
import app.drift.model.engine.SufficiencyTier
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

object Fixtures {
    val zone: ZoneId = ZoneOffset.UTC
    /** 2026-03-04 12:00 UTC, midday on the fixture day. */
    val noon: Instant = Instant.parse("2026-03-04T12:00:00Z")
    val today: LocalDate = LocalDate.of(2026, 3, 4)

    fun at(h: Int, m: Int = 0, dayOffset: Long = 0): Instant = today.plusDays(dayOffset).atTime(h, m).toInstant(ZoneOffset.UTC)

    private fun <T : Comparable<T>> measured(v: T) = Quantity(v, Provenance.DERIVED)

    /** A plausible day: groggy, morning peak, afternoon dip, evening peak, wind-down, melatonin, sleep. */
    fun dayCurve(date: LocalDate): DayCurve {
        val start = date.atStartOfDay().toInstant(ZoneOffset.UTC)
        val points = (0..287).map { i ->
            val hour = i / 12.0
            val level = 50 + 35 * Math.sin((hour - 6) / 24 * 2 * Math.PI * 1.9) - if (hour < 6 || hour > 23) 40 else 0
            EnergyPoint(start.plusSeconds(i * 300L), level.coerceIn(0.0, 100.0))
        }
        fun seg(phase: EnergyPhase, from: Int, to: Int, ext: Int? = null) =
            EnergySegment(phase, Interval(start.plusSeconds(from * 3600L), start.plusSeconds(to * 3600L)), ext?.let { start.plusSeconds(it * 3600L) })
        val segments = listOf(
            seg(EnergyPhase.SLEEP, 0, 7),
            seg(EnergyPhase.GROGGINESS, 7, 9),
            seg(EnergyPhase.MORNING_PEAK, 9, 13, 11),
            seg(EnergyPhase.AFTERNOON_DIP, 13, 16, 14),
            seg(EnergyPhase.EVENING_PEAK, 16, 20, 18),
            seg(EnergyPhase.WIND_DOWN, 20, 22),
            seg(EnergyPhase.MELATONIN_WINDOW, 22, 24),
        )
        return DayCurve(date, points, segments, false, ConfidenceLevel.MEDIUM, Provenance.DERIVED)
    }

    fun snapshot(
        computedAt: Instant = noon,
        windDown: Instant? = at(21, 30),
        melatoninStart: Instant? = at(22, 15),
        wake: Instant = at(7, 0, 1),
        days: List<DayCurve> = listOf(today.minusDays(1), today, today.plusDays(1)).map(::dayCurve),
        habits: List<HabitCard> = listOf(
            HabitCard(HabitKind.CAFFEINE_CUTOFF, Interval(at(14, 0), at(22, 0)), "caffeine"),
            HabitCard(HabitKind.MORNING_LIGHT, Interval(at(7, 30, 1), at(9, 30, 1)), "light"),
            HabitCard(HabitKind.LAST_MEAL, Interval(at(19, 0), at(21, 0)), "meal"),
        ),
    ): AnalysisSnapshot = AnalysisSnapshot(
        engineVersion = EngineVersion(1, 0, 0),
        inputHash = "fixture",
        computedAt = computedAt,
        sleepNeed = measured(Duration.ofHours(8)),
        sleepDebt = null,
        debtBand = null,
        debtTrend = emptyList(),
        regularity = RegularityReport(null, null, null, null, null, null, null),
        circadian = CircadianEstimate(null, melatoninStart?.let { Interval(it, it.plusSeconds(7200)) }, windDown, false, null),
        energyCurve = EnergyCurve(days, 0),
        recommendations = Recommendations(
            sleepOpportunity = measured(Duration.ofHours(8)),
            bedtime = measured(at(23, 0)),
            wakeTime = measured(wake),
            tradeOff = null,
            habits = habits,
            nap = null,
        ),
        explanations = emptyList(),
        state = EngineState(EngineVersion(1, 0, 0), computedAt, emptyMap()),
        dataSufficiency = Sufficiency(SufficiencyTier.FULL, 30, 20, 0, true, true),
    )

    fun episode(id: String = "ep-1"): SleepEpisode = SleepEpisode(
        id = EpisodeId(id),
        start = at(23, 40, -1),
        end = at(7, 5),
        zone = zone,
        kind = EpisodeKind.MAIN,
        origin = SourceRef(SourceId.PHONE, "phone-$id", QualityTier.PHONE_INFERRED),
    )
}
