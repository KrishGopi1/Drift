package app.drift.feature.sleep.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.drift.charts.RegularityTrendChart
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.MetricHero
import app.drift.feature.sleep.R
import app.drift.feature.sleep.sleep.RegularitySection

/** SRI numbers, trend and timing spreads shared by the Sleep and Body clock screens. Needs [RegularitySection.sri7]. */
@Composable
fun RegularityBlock(section: RegularitySection, trendHeight: Dp, modifier: Modifier = Modifier) {
    val sri7 = section.sri7 ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md)) {
        MetricHero(
            value = sri7.value,
            unit = stringResource(R.string.sleep_sri_unit),
            label = stringResource(R.string.sleep_sri7_label),
            provenance = sri7.provenance,
        )
        section.sri14?.let {
            StatRow(
                stringResource(R.string.sleep_sri14_label),
                stringResource(R.string.sleep_sri_value, DriftFormat.decimal(it.value, 0)),
                it.provenance,
            )
        }
        if (section.showTrend) RegularityTrendChart(section.trend, height = scaledChartHeight(trendHeight))
        section.onsetSpread?.let { StatRow(stringResource(R.string.sleep_spread_onset), DriftFormat.duration(it.value), it.provenance) }
        section.wakeSpread?.let { StatRow(stringResource(R.string.sleep_spread_wake), DriftFormat.duration(it.value), it.provenance) }
        section.midpointSpread?.let { StatRow(stringResource(R.string.sleep_spread_midpoint), DriftFormat.duration(it.value), it.provenance) }
    }
}
