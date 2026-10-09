package app.drift.feature.sleep.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.drift.charts.DebtPoint
import app.drift.charts.DebtTrendChart
import app.drift.charts.QualityColumn
import app.drift.charts.SleepColumn
import app.drift.charts.SleepQualityChart
import app.drift.charts.SleepTimesChart
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftDivider
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftIcons
import app.drift.designsystem.DriftScreenHeader
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.HeaderSubtitle
import app.drift.designsystem.SettingsButton
import app.drift.designsystem.ScreenStateHost
import app.drift.designsystem.SegmentedControl
import app.drift.feature.sleep.R
import app.drift.feature.sleep.common.rememberUse24h
import app.drift.model.EpisodeId
import app.drift.model.engine.MetricKey
import app.drift.model.repository.SourceFix
import app.drift.model.ui.ScreenState
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Callbacks of the Progress tab. */
data class ProgressActions(
    val onOpenNight: (EpisodeId) -> Unit = {},
    val onAddNight: (LocalDate) -> Unit = {},
    val onRate: (LocalDate, Int) -> Unit = { _, _ -> },
    val onExplain: (MetricKey) -> Unit = {},
    val onOpenHistory: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onFix: (SourceFix) -> Unit = {},
)

/**
 * Progress tab: "My progress" over the two-week window, a Sleep times / Sleep debt / Sleep quality switch, the chart
 * for the chosen view, the catch-up card and every night as a row. [initialTab] and [scrollable] exist for tests.
 */
@Composable
fun ProgressScreen(
    state: ScreenState<ProgressUiModel>,
    actions: ProgressActions,
    modifier: Modifier = Modifier,
    ratingFailed: Boolean = false,
    initialTab: ProgressTab = ProgressTab.TIMES,
    scrollable: Boolean = true,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val ready = (state as? ScreenState.Ready)?.model
    Column(
        modifier
            .background(DriftTheme.colors.background)
            .then(if (scrollable) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier),
    ) {
        DriftScreenHeader(
            title = stringResource(R.string.progress_title),
            subtitle = { HeaderSubtitle(ready?.let { rangeLabel(it.windowStart, it.windowEnd) } ?: stringResource(R.string.progress_subtitle)) },
            trailing = { SettingsButton(actions.onOpenSettings) },
        )
        Column(Modifier.padding(horizontal = DriftTheme.spacing.gutter).padding(bottom = DriftTheme.spacing.xl)) {
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.progress_tab_times),
                    stringResource(R.string.progress_tab_debt),
                    stringResource(R.string.progress_tab_quality),
                ),
                selectedIndex = tab.ordinal,
                onSelect = { tab = ProgressTab.entries[it] },
                contentDescription = stringResource(R.string.progress_tabs_label),
            )
            Box(Modifier.padding(top = DriftTheme.spacing.lg)) {
                ScreenStateHost(state, onRetry = actions.onRetry, onFix = actions.onFix) { model ->
                    when (tab) {
                        ProgressTab.TIMES -> TimesView(model, actions)
                        ProgressTab.DEBT -> DebtView(model, actions)
                        ProgressTab.QUALITY -> QualityView(model, actions, ratingFailed)
                    }
                }
            }
        }
    }
}

@Composable
internal fun rangeLabel(a: LocalDate, b: LocalDate): String {
    val locale = Locale.getDefault()
    val month = DateTimeFormatter.ofPattern("MMM d", locale)
    val day = DateTimeFormatter.ofPattern("d", locale)
    return if (a.month == b.month) "${month.format(a)} – ${day.format(b)}" else "${month.format(a)} – ${month.format(b)}"
}

/** Night named by the evening it started (the night ending on Friday morning is THU), as RISE-style columns read. */
private fun LocalDate.nightShort(): String = minusDays(1).dayShort()

private fun LocalDate.dayShort(): String = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault())

@Composable
private fun nightName(model: ProgressUiModel, date: LocalDate): String {
    val weekday = date.minusDays(1).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return if (date == model.nights.lastOrNull { it.main != null }?.date) {
        stringResource(R.string.progress_last_night, weekday)
    } else {
        stringResource(R.string.progress_night_of, weekday)
    }
}

@Composable
private fun TimesView(model: ProgressUiModel, actions: ProgressActions) {
    val use24h = rememberUse24h(model.use24hSetting)
    val target = model.targetBedtime?.let { t ->
        val z = t.atZone(model.zone)
        // Minutes after 18:00, wrapping the small hours to the next day.
        val m = z.hour * 60 + z.minute - 18 * 60
        if (m < 0) m + 1440 else m
    }
    val noData = stringResource(R.string.progress_no_data_short)
    val columns = model.nights.map { n ->
        val asleep = n.asleep
        SleepColumn(
            key = n.date.toString(),
            day = n.date.nightShort(),
            total = asleep?.let { DriftFormat.hm(it) } ?: noData,
            spans = n.spans(model.zone),
            targetMinute = target,
            description = if (asleep != null) {
                stringResource(R.string.progress_column_a11y, n.date.nightShort(), DriftFormat.durationSpoken(asleep))
            } else {
                stringResource(R.string.progress_column_empty_a11y, n.date.nightShort())
            },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.lg)) {
        SleepTimesChart(
            columns = columns,
            use24h = use24h,
            onColumnClick = { key ->
                val d = LocalDate.parse(key)
                model.mainId(d)?.let(actions.onOpenNight) ?: actions.onAddNight(d)
            },
        )
        if (target != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm)) {
                Icon(DriftIcons.MoonSmall, null, tint = DriftTheme.colors.onSurfaceMuted, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.progress_target_legend), style = DriftTheme.typography.caption, color = DriftTheme.colors.onSurfaceMuted)
            }
        }
        CatchUpCard(model, actions)
        ListTitle(stringResource(R.string.progress_all_times))
        Column {
            model.nights.asReversed().filter { it.main != null }.forEach { n ->
                val main = n.main!!
                ListRow(
                    title = nightName(model, n.date),
                    subtitle = "${DriftFormat.shortClock(main.start, model.zone, use24h)} – ${DriftFormat.shortClock(main.end, model.zone, use24h)}",
                    value = DriftFormat.hm(n.asleep ?: Duration.ZERO),
                    onClick = { actions.onOpenNight(main.id) },
                    tag = "progress_night_${n.date}",
                )
            }
        }
        DriftButton(stringResource(R.string.progress_longer_history), actions.onOpenHistory, style = ButtonStyle.Text)
    }
}

/** "Debt-free schedule · GREAT IN 3 DAYS >" */
@Composable
private fun CatchUpCard(model: ProgressUiModel, actions: ProgressActions) {
    val catchUp = model.catchUp ?: return
    val nights = catchUp.nightsToGreat
    val pill = when {
        nights == 0 -> stringResource(R.string.progress_catchup_there)
        nights != null -> pluralStringResource(R.plurals.progress_catchup_in, nights, nights)
        else -> stringResource(R.string.progress_catchup_long)
    }
    DriftCard(
        modifier = Modifier.testTag("progress_catchup"),
        contentDescription = "${stringResource(R.string.progress_catchup_title)}. $pill",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.progress_catchup_title), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface, modifier = Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(DriftTheme.radii.pill))
                    .background(DriftTheme.colors.background)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (nights == 0) DriftTheme.colors.success else DriftTheme.colors.primary))
                Text(pill.uppercase(Locale.getDefault()), style = DriftTheme.typography.overline, color = DriftTheme.colors.onSurface)
            }
        }
    }
}

@Composable
private fun DebtView(model: ProgressUiModel, actions: ProgressActions) {
    if (model.debt.isEmpty()) {
        Text(stringResource(R.string.progress_debt_pending), style = DriftTheme.typography.body, color = DriftTheme.colors.onSurfaceMuted)
        return
    }
    val points = model.debt.map { d ->
        val h = d.debtAfter.seconds / 3600.0
        DebtPoint(
            key = d.date.toString(),
            day = d.date.dayShort(),
            hours = h,
            value = "${DriftFormat.hoursDecimal(d.debtAfter)}h",
            description = stringResource(R.string.progress_debt_point_a11y, d.date.dayShort(), DriftFormat.durationSpoken(d.debtAfter)),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.lg)) {
        DebtTrendChart(points)
        CatchUpCard(model, actions)
        ListTitle(stringResource(R.string.progress_all_debt))
        Column {
            model.debt.asReversed().forEach { d ->
                val title = if (d.date == model.today) stringResource(R.string.progress_today) else d.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                ListRow(
                    title = title,
                    subtitle = DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault()).format(d.date),
                    value = stringResource(R.string.progress_debt_value, DriftFormat.hoursDecimal(d.debtAfter)),
                    onClick = model.mainId(d.date)?.let { id -> { actions.onOpenNight(id) } },
                    tag = "progress_debt_${d.date}",
                )
            }
        }
    }
}

@Composable
private fun ratingWord(r: Int): String = stringResource(
    when (r) {
        1 -> R.string.progress_rating_1
        2 -> R.string.progress_rating_2
        3 -> R.string.progress_rating_3
        4 -> R.string.progress_rating_4
        else -> R.string.progress_rating_5
    },
)

@Composable
private fun QualityView(model: ProgressUiModel, actions: ProgressActions, ratingFailed: Boolean) {
    val notRated = stringResource(R.string.progress_not_rated)
    Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.lg)) {
        model.unratedLastNight?.let { night ->
            DriftCard(modifier = Modifier.testTag("progress_rate")) {
                Text(stringResource(R.string.progress_rate_title), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..5).forEach { r ->
                        val word = ratingWord(r)
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(DriftTheme.radii.sm))
                                .clickable(role = Role.Button, onClickLabel = word) { actions.onRate(night, r) }
                                .semantics(mergeDescendants = true) { contentDescription = word }
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                .testTag("progress_rate_$r"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(DriftTheme.colors.primaryContainer.copy(alpha = 0.2f + 0.16f * r)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("$r", style = DriftTheme.typography.label, color = DriftTheme.colors.onSurface)
                            }
                            Text(word, style = DriftTheme.typography.caption, color = DriftTheme.colors.onSurfaceMuted)
                        }
                    }
                }
                if (ratingFailed) {
                    Text(stringResource(R.string.progress_rate_failed), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.error)
                }
            }
        }
        SleepQualityChart(
            model.nights.map { n ->
                val r = model.ratings[n.date]
                QualityColumn(
                    key = n.date.toString(),
                    day = n.date.nightShort(),
                    rating = r,
                    description = "${n.date.nightShort()}: ${r?.let { ratingWord(it) } ?: notRated}",
                )
            },
        )
        ListTitle(stringResource(R.string.progress_all_quality))
        Column {
            model.nights.asReversed().filter { it.main != null }.forEach { n ->
                val r = model.ratings[n.date]
                ListRow(
                    title = nightName(model, n.date),
                    subtitle = DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault()).format(n.date),
                    value = r?.let { ratingWord(it) } ?: notRated,
                    onClick = { actions.onOpenNight(n.main!!.id) },
                    tag = "progress_quality_${n.date}",
                )
            }
        }
        Text(stringResource(R.string.progress_quality_note), style = DriftTheme.typography.caption, color = DriftTheme.colors.onSurfaceMuted)
    }
}

@Composable
private fun ListTitle(text: String) {
    Text(text, style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface, modifier = Modifier.padding(top = DriftTheme.spacing.sm))
}

/** RISE-style list row: title over a muted subtitle, value on the right, chevron when tappable. */
@Composable
private fun ListRow(title: String, subtitle: String, value: String, onClick: (() -> Unit)?, tag: String) {
    Column {
        DriftDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .semantics(mergeDescendants = true) {}
                .heightIn(min = 64.dp)
                .padding(vertical = DriftTheme.spacing.sm)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
                Text(subtitle, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
            }
            Text(value, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurface)
            if (onClick != null) Icon(DriftIcons.ChevronRight, null, tint = DriftTheme.colors.onSurface, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
