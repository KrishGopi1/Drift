package app.drift.feature.sleep.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.drift.charts.SleepDurationChart
import app.drift.charts.SleepTimingChart
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftListRow
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.DriftTopBar
import app.drift.designsystem.ProvenanceChip
import app.drift.designsystem.ScreenStateHost
import app.drift.designsystem.SegmentedControl
import app.drift.designsystem.SourceProblemPanel
import app.drift.designsystem.labelRes
import app.drift.feature.sleep.R
import app.drift.feature.sleep.common.Caption
import app.drift.feature.sleep.common.CardTitle
import app.drift.feature.sleep.common.ScreenBody
import app.drift.feature.sleep.common.entrance
import app.drift.feature.sleep.common.rememberUse24h
import app.drift.feature.sleep.common.shortLabel
import app.drift.model.EpisodeId
import app.drift.model.Provenance
import app.drift.model.repository.SourceFix
import app.drift.model.ui.ScreenState
import java.time.LocalDate

data class HistoryActions(
    val onBack: () -> Unit = {},
    val onSelectRange: (HistoryRange) -> Unit = {},
    val onOpenNight: (EpisodeId) -> Unit = {},
    val onAddNight: (LocalDate) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onFix: (SourceFix) -> Unit = {},
)

@Composable
fun HistoryScreen(
    state: ScreenState<HistoryUiModel>,
    today: LocalDate,
    actions: HistoryActions,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    ScreenBody(
        modifier = modifier,
        scrollable = scrollable,
        topBar = {
            DriftTopBar(
                title = stringResource(R.string.history_title),
                onBack = actions.onBack,
                actions = { DriftButton(stringResource(R.string.history_add_night), { actions.onAddNight(today) }, style = ButtonStyle.Text) },
            )
        },
    ) {
        ScreenStateHost(state, onRetry = actions.onRetry, onFix = actions.onFix) { model ->
            HistoryContent(model, actions)
        }
    }
}

@Composable
private fun HistoryContent(model: HistoryUiModel, actions: HistoryActions) {
    val use24h = rememberUse24h(model.use24hSetting)
    var selected: LocalDate? by rememberSaveable { mutableStateOf(null) }
    val onChartTap: (LocalDate) -> Unit = { date ->
        selected = date
        val row = model.nights.firstOrNull { it.date == date }
        if (row?.episodeId != null) actions.onOpenNight(row.episodeId) else actions.onAddNight(date)
    }
    Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sectionGap)) {
        model.problem?.let { SourceProblemPanel(it.message, it.fix, actions.onFix) }
        val labels = listOf(R.string.history_week, R.string.history_month, R.string.history_quarter).map { stringResource(it) }
        SegmentedControl(
            options = labels,
            selectedIndex = model.range.ordinal,
            onSelect = { actions.onSelectRange(HistoryRange.entries[it]) },
            contentDescription = stringResource(R.string.history_range_description),
        )
        SummaryCard(model, use24h, Modifier.entrance(0))
        DriftCard(Modifier.entrance(1)) {
            CardTitle(stringResource(R.string.history_duration_title))
            SleepDurationChart(model.bars, model.range.timing, need = model.need, selectedDate = selected, onNightClick = onChartTap)
        }
        DriftCard(Modifier.entrance(2)) {
            CardTitle(stringResource(R.string.history_timing_title))
            SleepTimingChart(model.bars, model.range.timing, use24h = use24h, selectedDate = selected, onNightClick = onChartTap)
        }
        DriftCard(Modifier.entrance(3)) {
            CardTitle(stringResource(R.string.history_nights_title))
            model.nights.forEach { row -> NightRowView(row, use24h, actions) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(model: HistoryUiModel, use24h: Boolean, modifier: Modifier) {
    val s = model.summary
    val none = stringResource(R.string.history_no_value)
    DriftCard(modifier) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md),
        ) {
            SummaryStat(stringResource(R.string.history_avg_sleep), s.averageSleep?.let { DriftFormat.duration(it) } ?: none)
            SummaryStat(stringResource(R.string.history_avg_bedtime), s.averageOnsetMinutes?.let { DriftFormat.clockMinutes(it, use24h) } ?: none)
            SummaryStat(stringResource(R.string.history_avg_wake), s.averageWakeMinutes?.let { DriftFormat.clockMinutes(it, use24h) } ?: none)
        }
        Caption(stringResource(R.string.history_recorded, s.recorded, s.total))
        if (s.recorded < s.total) Caption(stringResource(R.string.history_gaps_excluded))
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    val spoken = stringResource(R.string.history_stat_description, label, value, stringResource(Provenance.DERIVED.labelRes()))
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }, verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs)) {
        Caption(label)
        Text(value, style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface)
        ProvenanceChip(Provenance.DERIVED)
    }
}

@Composable
private fun NightRowView(row: NightRow, use24h: Boolean, actions: HistoryActions) {
    if (row.hasData && row.start != null && row.end != null && row.zone != null && row.duration != null) {
        DriftListRow(
            title = row.date.shortLabel(),
            subtitle = stringResource(
                R.string.history_row_subtitle,
                DriftFormat.clock(row.start, row.zone, use24h),
                DriftFormat.clock(row.end, row.zone, use24h),
                DriftFormat.duration(row.duration),
            ),
            provenance = row.provenance,
            onClick = { actions.onOpenNight(row.episodeId!!) },
            onClickLabel = stringResource(R.string.history_open_night),
        )
    } else {
        DriftListRow(
            title = row.date.shortLabel(),
            subtitle = stringResource(R.string.history_row_no_data),
            onClick = { actions.onAddNight(row.date) },
            onClickLabel = stringResource(R.string.history_add_night),
        )
    }
}
