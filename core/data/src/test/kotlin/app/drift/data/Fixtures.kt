package app.drift.data

import app.drift.model.ActivityType
import app.drift.model.AlarmKind
import app.drift.model.Confidence
import app.drift.model.ConfidenceLevel
import app.drift.model.DataQuality
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.Provenance
import app.drift.model.QualityTier
import app.drift.model.Quantity
import app.drift.model.Signal
import app.drift.model.SleepEpisode
import app.drift.model.SleepStage
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.StageSpan
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.Cause
import app.drift.model.engine.CauseKind
import app.drift.model.engine.Chronotype
import app.drift.model.engine.ChronotypeLabel
import app.drift.model.engine.CircadianEstimate
import app.drift.model.engine.DailyDebt
import app.drift.model.engine.DayCurve
import app.drift.model.engine.DebtBand
import app.drift.model.engine.EnergyCurve
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.EnergyPoint
import app.drift.model.engine.EnergySegment
import app.drift.model.engine.EngineState
import app.drift.model.engine.EngineVersion
import app.drift.model.engine.Explanation
import app.drift.model.engine.HabitCard
import app.drift.model.engine.HabitKind
import app.drift.model.engine.MetricKey
import app.drift.model.engine.NapAdvice
import app.drift.model.engine.Recommendations
import app.drift.model.engine.Reentrainment
import app.drift.model.engine.RegularityReport
import app.drift.model.engine.Sufficiency
import app.drift.model.engine.SufficiencyTier
import app.drift.model.engine.TradeOff
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object Fixtures {
    val zone: ZoneId = ZoneId.of("Europe/Berlin")
    val day0: Instant = Instant.parse("2026-09-01T21:00:00Z")

    fun ref(source: SourceId, record: String, tier: QualityTier, origin: String? = null) =
        SourceRef(source, record, tier, origin, null)

    fun episode(
        record: String,
        start: Instant,
        end: Instant,
        source: SourceId = SourceId.HEALTH_CONNECT,
        tier: QualityTier = QualityTier.WEARABLE_WRIST,
        kind: EpisodeKind = EpisodeKind.MAIN,
        stages: Boolean = false,
        origin: String? = null,
    ) = SleepEpisode(
        id = EpisodeId("raw-$source-$record"),
        start = start, end = end, zone = zone, kind = kind,
        stages = if (stages) listOf(
            StageSpan(SleepStage.LIGHT, Interval(start, start.plus(Duration.between(start, end).dividedBy(2)))),
            StageSpan(SleepStage.DEEP, Interval(start.plus(Duration.between(start, end).dividedBy(2)), end)),
        ) else null,
        inBed = Interval(start.minusSeconds(300), end),
        origin = ref(source, record, tier, origin),
        quality = DataQuality(0.95, 1.0),
    )

    fun snapshot(hash: String = "h1", computedAt: Instant = Instant.parse("2026-10-09T08:00:00.123456789Z")): AnalysisSnapshot {
        val v = EngineVersion(1, 2, 3)
        fun <T : Comparable<T>> est(x: T, lo: T, hi: T) =
            Quantity(x, Provenance.ESTIMATED, Confidence(lo, hi, 0.9, ConfidenceLevel.MEDIUM), setOf(SourceId.HEALTH_CONNECT, SourceId.PHONE))
        val t = Instant.parse("2026-10-09T10:00:00Z")
        val interval = Interval(t, t.plusSeconds(3600))
        return AnalysisSnapshot(
            engineVersion = v, inputHash = hash, computedAt = computedAt,
            sleepNeed = est(Duration.ofMinutes(468), Duration.ofMinutes(440), Duration.ofMinutes(500)),
            sleepDebt = Quantity(Duration.ofMinutes(95), Provenance.DERIVED),
            debtBand = DebtBand.LOW,
            debtTrend = listOf(
                DailyDebt(LocalDate.parse("2026-10-08"), Quantity(Duration.ofMinutes(400), Provenance.MEASURED), Duration.ofMinutes(468), Duration.ofMinutes(68), Duration.ofMinutes(95), false),
                DailyDebt(LocalDate.parse("2026-10-07"), null, Duration.ofMinutes(468), Duration.ofMinutes(-12), Duration.ofMinutes(27), true),
            ),
            regularity = RegularityReport(
                Quantity(88.25, Provenance.DERIVED), null, Quantity(Duration.ofMinutes(31), Provenance.DERIVED), null, null,
                Quantity(Duration.ofMinutes(50), Provenance.DERIVED),
                Chronotype(est(250.5, 230.0, 270.0), ChronotypeLabel.INTERMEDIATE, 4, 0.25),
                listOf(LocalDate.parse("2026-10-01") to 80.5, LocalDate.parse("2026-10-02") to 81.0),
            ),
            circadian = CircadianEstimate(est(1290.0, 1260.0, 1320.0), interval, t.minusSeconds(3600), true, Reentrainment(Duration.ofMinutes(90), true, 2)),
            energyCurve = EnergyCurve(
                listOf(
                    DayCurve(
                        LocalDate.parse("2026-10-09"),
                        listOf(EnergyPoint(t, 61.5), EnergyPoint(t.plusSeconds(300), Double.NaN.let { 62.25 })),
                        listOf(EnergySegment(EnergyPhase.MORNING_PEAK, interval, t.plusSeconds(1800)), EnergySegment(EnergyPhase.WIND_DOWN, interval, null)),
                        false, ConfidenceLevel.HIGH, Provenance.ESTIMATED,
                    ),
                ),
                12,
            ),
            recommendations = Recommendations(
                est(Duration.ofMinutes(540), Duration.ofMinutes(500), Duration.ofMinutes(560)),
                est(t, t.minusSeconds(600), t.plusSeconds(600)), est(t.plusSeconds(32000), t.plusSeconds(31000), t.plusSeconds(33000)),
                TradeOff("Later bedtime fits your melatonin window", t.plusSeconds(1)),
                listOf(HabitCard(HabitKind.CAFFEINE_CUTOFF, interval, "caffeine-8h")),
                NapAdvice(interval, Duration.ofMinutes(25), "afternoon dip"),
            ),
            explanations = listOf(Explanation(MetricKey.SLEEP_DEBT, "Debt fell", listOf(Cause(CauseKind.NIGHT_ENTERED_WINDOW, LocalDate.parse("2026-10-08"), -0.5, "x"), Cause(CauseKind.FIRST_RUN, null, null, "y")))),
            state = EngineState(v, computedAt, mapOf("need.mean" to 7.8, "need.var" to 0.0123456789012345)),
            dataSufficiency = Sufficiency(SufficiencyTier.FULL, 90, 20, 0, true, true),
        )
    }
}
