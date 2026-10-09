package app.drift.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftTheme
import app.drift.model.Confidence
import app.drift.model.ConfidenceLevel
import app.drift.model.Interval
import app.drift.model.Provenance
import app.drift.model.Quantity
import app.drift.model.SleepStage
import app.drift.model.StageSpan
import app.drift.model.engine.CircadianEstimate
import app.drift.model.engine.DailyDebt
import app.drift.model.engine.DayCurve
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.EnergyPoint
import app.drift.model.engine.EnergySegment
import app.drift.model.repository.ThemeMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.exp
import kotlin.math.pow

// Preview-only sample values for the Android Studio preview pane. Not used by the app.

private val previewDate = LocalDate.of(2026, 10, 9)
private fun at(h: Int, m: Int = 0): Instant = previewDate.atTime(h, m).toInstant(ZoneOffset.UTC)

private fun sampleCurve(): DayCurve {
    val points = (0..204).map { i ->
        val h = 6.5 + i * 5 / 60.0
        val v = 18 + 68 * exp(-((h - 10.5) / 1.9).pow(2)) + 52 * exp(-((h - 19.0) / 1.8).pow(2))
        EnergyPoint(at(6, 30).plusSeconds(i * 300L), v.coerceIn(0.0, 100.0))
    }
    fun seg(p: EnergyPhase, a: Int, b: Int, ex: Int? = null) =
        EnergySegment(p, Interval(at(a), at(b)), ex?.let { at(it) })
    return DayCurve(
        previewDate, points,
        listOf(
            seg(EnergyPhase.MORNING_PEAK, 8, 12, 10), seg(EnergyPhase.AFTERNOON_DIP, 13, 16, 14),
            seg(EnergyPhase.EVENING_PEAK, 17, 21, 19), seg(EnergyPhase.MELATONIN_WINDOW, 22, 23),
        ),
        false, ConfidenceLevel.MEDIUM, Provenance.ESTIMATED,
    )
}

private fun sampleDebt(): List<DailyDebt> = listOf(40, 75, -20, 0, 30, -45, 60).mapIndexed { i, c ->
    DailyDebt(
        previewDate.minusDays((6 - i).toLong()),
        Quantity(Duration.ofMinutes(465L - c), Provenance.MEASURED), Duration.ofMinutes(465),
        Duration.ofMinutes(c.toLong()), Duration.ofMinutes(60), imputed = i == 3,
    )
}

private fun sampleBars() = (0 until 7).map { i ->
    TimingBar(previewDate.minusDays((6 - i).toLong()), if (i == 3) null else -60 + i * 8, if (i == 3) null else 420 + i * 5)
}

private fun sampleStages(): List<StageSpan> {
    var t = at(23, 10).minus(Duration.ofDays(1))
    return listOf(
        SleepStage.AWAKE to 8, SleepStage.LIGHT to 40, SleepStage.DEEP to 50, SleepStage.REM to 25, SleepStage.LIGHT to 60,
        SleepStage.DEEP to 30, SleepStage.REM to 40, SleepStage.LIGHT to 70, SleepStage.AWAKE to 6,
    ).map { (s, m) -> StageSpan(s, Interval(t, t.plusSeconds(m * 60L))).also { t = t.plusSeconds(m * 60L) } }
}

private fun sampleCircadian() = CircadianEstimate(
    dlmoMinutes = Quantity(1275.0, Provenance.ESTIMATED, Confidence(1245.0, 1305.0, 0.8, ConfidenceLevel.MEDIUM)),
    melatoninWindow = Interval(at(21, 45), at(23, 45)),
    windDownStart = at(20, 45),
    shiftWorkMode = false,
    reentrainment = null,
)

@Composable
private fun ChartFrame(dark: Boolean, content: @Composable () -> Unit) {
    DriftTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, reducedMotion = true) {
        Column(Modifier.background(DriftTheme.colors.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
    }
}

@Composable
private fun AllCharts() {
    DriftCard { EnergyCurveChart(sampleCurve(), now = at(14, 20), zone = ZoneOffset.UTC) }
    DriftCard { MiniEnergyCurve(sampleCurve(), now = at(11), zone = ZoneOffset.UTC) }
    DriftCard { DebtBarsChart(sampleDebt()) }
    DriftCard { SleepTimingChart(sampleBars(), TimingRange.WEEK) }
    DriftCard { StageChart(sampleStages(), zone = ZoneOffset.UTC) }
    DriftCard { RegularityTrendChart((0 until 20).map { previewDate.minusDays((19 - it).toLong()) to 65.0 + it }) }
    DriftCard { CircadianRhythmChart(sampleCircadian(), zone = ZoneOffset.UTC, now = at(15, 40)) }
}

@Preview(name = "Charts dark", widthDp = 360, heightDp = 2400) @Composable private fun ChartsDarkPreview() = ChartFrame(true) { AllCharts() }
@Preview(name = "Charts light", widthDp = 360, heightDp = 2400) @Composable private fun ChartsLightPreview() = ChartFrame(false) { AllCharts() }
@Preview(name = "Charts 200% font", widthDp = 360, heightDp = 3200, fontScale = 2f) @Composable private fun ChartsLargeFontPreview() = ChartFrame(true) { AllCharts() }
