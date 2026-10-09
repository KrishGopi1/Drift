package app.drift.charts

import app.drift.model.Confidence
import app.drift.model.ConfidenceLevel
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.Provenance
import app.drift.model.QualityTier
import app.drift.model.Quantity
import app.drift.model.SleepEpisode
import app.drift.model.SleepStage
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.StageSpan
import app.drift.model.engine.CircadianEstimate
import app.drift.model.engine.DailyDebt
import app.drift.model.engine.DayCurve
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.EnergyPoint
import app.drift.model.engine.EnergySegment
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.pow

/** Small hand-built inputs for chart tests. Deterministic: no clocks, no randomness. */
object Fx {
    val zone: ZoneId = ZoneId.of("UTC")
    val today: LocalDate = LocalDate.of(2026, 10, 9)

    fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = date.atTime(hour, minute).atZone(zone).toInstant()

    private fun level(h: Double, flat: Boolean): Double {
        if (flat) return 55.0 + 6 * kotlin.math.sin(h / 3)
        val v = 18 + 68 * exp(-((h - 10.5) / 1.9).pow(2)) + 52 * exp(-((h - 19.0) / 1.8).pow(2)) + 8 * exp(-((h - 7.0) / 1.0).pow(2))
        return v.coerceIn(0.0, 100.0)
    }

    fun energyCurve(date: LocalDate = today, flat: Boolean = false): DayCurve {
        val start = at(date, 6, 30)
        val points = (0..204).map { i ->
            val t = start.plusSeconds(i * 300L)
            val h = 6.5 + i * 5 / 60.0
            EnergyPoint(t, level(h, flat))
        }
        fun seg(phase: EnergyPhase, h1: Int, m1: Int, h2: Int, m2: Int, extremum: Pair<Int, Int>? = null) =
            EnergySegment(phase, Interval(at(date, h1, m1), at(date, h2, m2)), extremum?.let { at(date, it.first, it.second) })
        val segments = if (flat) listOf(
            seg(EnergyPhase.MORNING_PEAK, 8, 0, 12, 0, 10 to 30),
            seg(EnergyPhase.EVENING_PEAK, 17, 30, 21, 0, 19 to 0),
            seg(EnergyPhase.WIND_DOWN, 21, 0, 21, 45),
            seg(EnergyPhase.MELATONIN_WINDOW, 21, 45, 23, 30),
        ) else listOf(
            seg(EnergyPhase.GROGGINESS, 6, 30, 8, 0),
            seg(EnergyPhase.MORNING_PEAK, 8, 0, 12, 30, 10 to 30),
            seg(EnergyPhase.AFTERNOON_DIP, 12, 30, 16, 0, 14 to 15),
            seg(EnergyPhase.EVENING_PEAK, 16, 0, 21, 0, 19 to 0),
            seg(EnergyPhase.WIND_DOWN, 21, 0, 21, 45),
            seg(EnergyPhase.MELATONIN_WINDOW, 21, 45, 23, 30),
        )
        return DayCurve(date, points, segments, tooFlatForDip = flat, confidence = ConfidenceLevel.MEDIUM, provenance = Provenance.ESTIMATED)
    }

    /** 14 nights ending [today]: mix of added, repaid, one imputed and one missing night. */
    fun debtDays(): List<DailyDebt> {
        val contributions = listOf(40, 75, -20, 0, 30, -45, -30, 80, 105, 20, -10, 60, 0, -25)
        var debt = 90L
        return contributions.mapIndexed { i, c ->
            debt = (debt + c).coerceAtLeast(0)
            val date = today.minusDays((13 - i).toLong())
            val missing = i == 4
            val imputed = i == 9
            DailyDebt(
                date = date,
                sleep = if (missing) null else Quantity(Duration.ofMinutes(465L - c), Provenance.MEASURED),
                need = Duration.ofMinutes(465),
                contribution = Duration.ofMinutes(if (missing) 0 else c.toLong()),
                debtAfter = Duration.ofMinutes(debt),
                imputed = imputed,
            )
        }
    }

    private fun origin() = SourceRef(SourceId.HEALTH_CONNECT, "r", QualityTier.WEARABLE_WRIST)

    /** One MAIN episode per night for [days] nights ending on [end]; every [gapEvery]th night is skipped. */
    fun episodes(end: LocalDate = today, days: Int = 7, gapEvery: Int = 0): List<SleepEpisode> =
        (0 until days).mapNotNull { back ->
            if (gapEvery > 0 && back % gapEvery == gapEvery - 1) return@mapNotNull null
            val date = end.minusDays(back.toLong())
            val jitter = ((back * 37) % 70) - 20
            val onset = date.minusDays(1).atTime(23, 0).atZone(zone).toInstant().plusSeconds(jitter * 60L)
            val wake = date.atTime(7, 0).atZone(zone).toInstant().plusSeconds(((back * 23) % 40 - 10) * 60L)
            SleepEpisode(EpisodeId("e$back"), onset, wake, zone, EpisodeKind.MAIN, origin = origin())
        }

    fun stages(): List<StageSpan> {
        val start = at(today.minusDays(1), 23, 10)
        val plan = listOf(
            SleepStage.AWAKE to 8, SleepStage.LIGHT to 22, SleepStage.DEEP to 45, SleepStage.LIGHT to 15, SleepStage.REM to 12,
            SleepStage.LIGHT to 30, SleepStage.DEEP to 30, SleepStage.LIGHT to 20, SleepStage.AWAKE to 5, SleepStage.REM to 25,
            SleepStage.LIGHT to 40, SleepStage.REM to 30, SleepStage.UNKNOWN to 15, SleepStage.LIGHT to 50, SleepStage.REM to 35,
            SleepStage.LIGHT to 40, SleepStage.AWAKE to 10,
        )
        var t = start
        return plan.map { (s, m) -> StageSpan(s, Interval(t, t.plusSeconds(m * 60L))).also { t = t.plusSeconds(m * 60L) } }
    }

    fun regularity(days: Int = 30, gapAt: Set<Int> = emptySet()): List<Pair<LocalDate, Double>> =
        (0 until days).filter { it !in gapAt }.map { i -> today.minusDays((days - 1 - i).toLong()) to (62.0 + i * 0.6 + 5 * kotlin.math.sin(i / 2.5)) }

    fun circadian(): CircadianEstimate = CircadianEstimate(
        dlmoMinutes = Quantity(1275.0, Provenance.ESTIMATED, Confidence(1245.0, 1305.0, 0.8, ConfidenceLevel.MEDIUM)),
        melatoninWindow = Interval(at(today, 21, 45), at(today, 23, 45)),
        windDownStart = at(today, 20, 45),
        shiftWorkMode = false,
        reentrainment = null,
    )

    fun circadianEmpty(): CircadianEstimate = CircadianEstimate(null, null, null, false, null)
}
