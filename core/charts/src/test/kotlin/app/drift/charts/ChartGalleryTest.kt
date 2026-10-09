package app.drift.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.EnergySegment
import java.time.Duration

private val z = Fx.zone

@Composable private fun Stack(content: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }

class EnergyCurveGallery : GalleryCase("chart_energy_curve") {
    @Composable override fun Content() = EnergyCurveChart(Fx.energyCurve(), now = Fx.at(Fx.today, 14, 20), zone = z)
}

class EnergyCurveFlatGallery : GalleryCase("chart_energy_curve_flat") {
    @Composable override fun Content() = EnergyCurveChart(Fx.energyCurve(flat = true), now = null, zone = z, use24h = false)
}

class MiniEnergyGallery : GalleryCase("chart_mini_energy") {
    @Composable override fun Content() = MiniEnergyCurve(Fx.energyCurve(), now = Fx.at(Fx.today, 11, 0), zone = z)
}

class DebtBarsGallery : GalleryCase("chart_debt_bars") {
    @Composable override fun Content() = DebtBarsChart(Fx.debtDays())
}

class SleepTimingWeekGallery : GalleryCase("chart_timing_week") {
    @Composable override fun Content() = SleepTimingChart(timingBars(Fx.episodes(days = 7, gapEvery = 4), Fx.today, TimingRange.WEEK), TimingRange.WEEK, selectedDate = Fx.today.minusDays(2))
}

class SleepTimingMonthGallery : GalleryCase("chart_timing_month") {
    @Composable override fun Content() = SleepTimingChart(timingBars(Fx.episodes(days = 30, gapEvery = 9), Fx.today, TimingRange.MONTH), TimingRange.MONTH)
}

class SleepTimingQuarterGallery : GalleryCase("chart_timing_quarter") {
    @Composable override fun Content() = SleepTimingChart(timingBars(Fx.episodes(days = 90, gapEvery = 11), Fx.today, TimingRange.QUARTER), TimingRange.QUARTER, use24h = false)
}

class SleepDurationGallery : GalleryCase("chart_duration") {
    @Composable override fun Content() = SleepDurationChart(timingBars(Fx.episodes(days = 14, gapEvery = 6), Fx.today, TimingRange.MONTH).takeLast(14), TimingRange.WEEK, need = Duration.ofMinutes(465))
}

class StageGallery : GalleryCase("chart_stages") {
    @Composable override fun Content() = StageChart(Fx.stages(), zone = z)
}

class RegularityGallery : GalleryCase("chart_regularity") {
    @Composable override fun Content() = Stack {
        RegularityTrendChart(Fx.regularity(30, gapAt = setOf(12, 13, 14)))
        RegularityTrendChart(Fx.regularity(1))
    }
}

class CircadianGallery : GalleryCase("chart_circadian") {
    @Composable override fun Content() = CircadianRhythmChart(
        Fx.circadian(), zone = z, now = Fx.at(Fx.today, 15, 40),
        sleepWindow = app.drift.model.Interval(Fx.at(Fx.today, 23, 15), Fx.at(Fx.today.plusDays(1), 6, 45)),
    )
}

class CircadianEmptyGallery : GalleryCase("chart_circadian_empty") {
    @Composable override fun Content() = CircadianRhythmChart(Fx.circadianEmpty(), zone = z)
}

class EmptyChartsGallery : GalleryCase("chart_empty_inputs") {
    @Composable override fun Content() = Stack {
        DebtBarsChart(emptyList())
        SleepTimingChart(emptyList(), TimingRange.WEEK)
        StageChart(emptyList(), zone = z)
        RegularityTrendChart(emptyList())
        EnergyCurveChart(Fx.energyCurve().copy(points = emptyList(), segments = listOf(EnergySegment(EnergyPhase.SLEEP, app.drift.model.Interval(Fx.at(Fx.today, 1), Fx.at(Fx.today, 2)), null))), now = null, zone = z)
    }
}

class EnergyCurveFullDayGallery : GalleryCase("chart_energy_full_day") {
    @Composable override fun Content() = Stack {
        val c = Fx.energyCurve()
        val start = Fx.at(Fx.today, 0)
        val full = c.copy(points = (0..288).map { i -> app.drift.model.engine.EnergyPoint(start.plusSeconds(i * 300L), 40.0 + 30 * kotlin.math.sin(i / 46.0)) })
        EnergyCurveChart(full, now = Fx.at(Fx.today, 9), zone = z)
        EnergyCurveChart(full, now = null, zone = z, use24h = false, showLegend = false)
    }
}

class StageChartAxisGallery : GalleryCase("chart_stages_axis") {
    @Composable override fun Content() = Stack {
        StageChart(Fx.stages(), zone = z, use24h = false)
        StageChart(Fx.stages(), zone = z, height = 90.dp)
    }
}
