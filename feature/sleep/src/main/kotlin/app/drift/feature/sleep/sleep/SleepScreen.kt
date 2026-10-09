package app.drift.feature.sleep.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.drift.charts.DebtBarsChart
import app.drift.charts.RegularityTrendChart
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.CardTone
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftDivider
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftIconButton
import app.drift.designsystem.DriftIcons
import app.drift.designsystem.DriftListRow
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.DriftTopBar
import app.drift.designsystem.DurationConfidenceBand
import app.drift.designsystem.DurationHero
import app.drift.designsystem.InsufficientDataPanel
import app.drift.designsystem.MetricHero
import app.drift.designsystem.ProvenanceChip
import app.drift.designsystem.ScreenStateHost
import app.drift.designsystem.labelRes
import app.drift.feature.sleep.R
import app.drift.feature.sleep.common.BodyText
import app.drift.feature.sleep.common.Caption
import app.drift.feature.sleep.common.CardTitle
import app.drift.feature.sleep.common.RegularityBlock
import app.drift.feature.sleep.common.ScreenBody
import app.drift.feature.sleep.common.StatRow
import app.drift.feature.sleep.common.clockIn
import app.drift.feature.sleep.common.entrance
import app.drift.feature.sleep.common.rememberUse24h
import app.drift.feature.sleep.common.shortLabel
import app.drift.model.EpisodeId
import app.drift.model.engine.DebtBand
import app.drift.model.engine.MetricKey
import app.drift.model.repository.SourceFix
import app.drift.model.ui.InsufficientReason
import app.drift.model.ui.ScreenState
import java.time.Duration
import java.time.LocalDate

/** Callbacks of the Sleep screen. Every one is wired to a visible control. */
data class SleepActions(
    val onExplain: (MetricKey) -> Unit = {},
    val onOpenHistory: () -> Unit = {},
    val onOpenBodyClock: () -> Unit = {},
    val onOpenNight: (EpisodeId) -> Unit = {},
    val onAddNight: (LocalDate) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onFix: (SourceFix) -> Unit = {},
)

@Composable
fun SleepScreen(
    state: ScreenState<SleepUiModel>,
    actions: SleepActions,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    ScreenBody(
        modifier = modifier,
        scrollable = scrollable,
        topBar = {
            DriftTopBar(
                title = stringResource(R.string.sleep_title),
                subtitle = stringResource(R.string.sleep_subtitle),
                actions = { DriftButton(stringResource(R.string.sleep_open_history), actions.onOpenHistory, style = ButtonStyle.Text) },
            )
        },
    ) {
        ScreenStateHost(state, onRetry = actions.onRetry, onFix = actions.onFix) { model ->
            SleepContent(model, actions)
        }
    }
}

@Composable
private fun SleepContent(model: SleepUiModel, actions: SleepActions) {
    val use24h = rememberUse24h(model.use24hSetting)
    Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sectionGap)) {
        DebtCard(model.debt, actions, Modifier.entrance(0))
        if (model.debt !is DebtSection.TooFewNights) {
            ContributionCard(model, actions, Modifier.entrance(1))
        }
        NeedCard(model.need, actions, Modifier.entrance(2))
        RegularityCard(model.regularity, actions, Modifier.entrance(3))
        TonightCard(model, use24h, actions, Modifier.entrance(4))
    }
}

@Composable
private fun bandWord(band: DebtBand): String = stringResource(
    when (band) {
        DebtBand.LOW -> R.string.sleep_band_low
        DebtBand.MODERATE -> R.string.sleep_band_moderate
        DebtBand.HIGH -> R.string.sleep_band_high
    },
)

@Composable
private fun bandColor(band: DebtBand): Color = when (band) {
    DebtBand.LOW -> DriftTheme.colors.success
    DebtBand.MODERATE -> DriftTheme.colors.warning
    DebtBand.HIGH -> DriftTheme.colors.error
}

@Composable
private fun DebtCard(section: DebtSection, actions: SleepActions, modifier: Modifier) {
    when (section) {
        is DebtSection.Available -> {
            val debt = section.debt
            val provenance = stringResource(debt.provenance.labelRes())
            val band = section.band
            val description = stringResource(
                R.string.sleep_debt_description,
                DriftFormat.durationSpoken(debt.value),
                provenance,
                band?.let { bandWord(it) }.orEmpty(),
            )
            DriftCard(
                modifier = modifier,
                onClick = { actions.onExplain(MetricKey.SLEEP_DEBT) },
                onClickLabel = stringResource(R.string.sleep_why_debt),
                contentDescription = description,
            ) {
                DurationHero(
                    duration = debt.value,
                    label = stringResource(R.string.sleep_debt_label),
                    provenance = debt.provenance,
                )
                if (band != null) BandPill(band)
                DurationConfidenceBand(debt)
                Caption(
                    stringResource(
                        R.string.sleep_band_basis,
                        DriftFormat.duration(DebtBandBasis.lowBelow),
                        DriftFormat.duration(DebtBandBasis.highAbove),
                        DebtBandBasis.WINDOW_NIGHTS,
                    ),
                )
            }
        }
        is DebtSection.TooFewNights ->
            InsufficientDataPanel(InsufficientReason.TOO_FEW_NIGHTS, section.nightsNeeded, modifier)
        is DebtSection.Unavailable ->
            InsufficientDataPanel(InsufficientReason.TOO_MANY_MISSING_NIGHTS, 0, modifier)
    }
}

@Composable
private fun BandPill(band: DebtBand) {
    val color = bandColor(band)
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
    ) {
        Box(Modifier.padding(start = 12.dp).size(8.dp).clip(CircleShape).background(color))
        Text(
            bandWord(band),
            style = DriftTheme.typography.label,
            color = color,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

@Composable
private fun ContributionCard(model: SleepUiModel, actions: SleepActions, modifier: Modifier) {
    DriftCard(modifier = modifier) {
        CardTitle(stringResource(R.string.sleep_nights_title))
        DebtBarsChart(model.days, height = 180.dp, showLegend = true)
        Caption(stringResource(R.string.sleep_bars_caption))
        DriftDivider()
        model.contributions.forEach { row -> ContributionRowView(row, model, actions) }
    }
}

@Composable
private fun ContributionRowView(row: ContributionRow, model: SleepUiModel, actions: SleepActions) {
    val date = row.date.shortLabel()
    val subtitle = when (row.kind) {
        ContributionKind.NO_DATA -> stringResource(if (row.imputed) R.string.sleep_row_estimated_night else R.string.sleep_row_no_data)
        else -> stringResource(
            R.string.sleep_row_slept,
            DriftFormat.duration(row.sleep ?: Duration.ZERO),
            DriftFormat.duration(row.need),
        )
    }
    val change = when (row.kind) {
        ContributionKind.BUILT -> stringResource(R.string.sleep_row_built, DriftFormat.duration(row.contribution))
        ContributionKind.REPAID -> stringResource(R.string.sleep_row_repaid, DriftFormat.duration(row.contribution.negated()))
        ContributionKind.EVEN -> stringResource(R.string.sleep_row_even)
        ContributionKind.NO_DATA -> stringResource(R.string.sleep_row_add_night)
    }
    val color = when (row.kind) {
        ContributionKind.BUILT -> DriftTheme.colors.debt
        ContributionKind.REPAID -> DriftTheme.colors.repaid
        else -> DriftTheme.colors.onSurfaceMuted
    }
    val onClick: (() -> Unit)? = when {
        row.kind == ContributionKind.NO_DATA -> ({ actions.onAddNight(row.date) })
        row.episodeId != null -> ({ actions.onOpenNight(row.episodeId) })
        else -> null
    }
    DriftListRow(
        title = date,
        subtitle = subtitle,
        provenance = row.sleepProvenance,
        onClick = onClick,
        onClickLabel = stringResource(if (row.kind == ContributionKind.NO_DATA) R.string.sleep_row_add_night else R.string.sleep_row_open_night),
        trailing = {
            Text(
                change,
                style = DriftTheme.typography.label,
                color = color,
                modifier = Modifier.padding(start = 4.dp),
            )
        },
    )
}

@Composable
private fun NeedCard(need: NeedSection, actions: SleepActions, modifier: Modifier) {
    val caption = if (need.personalized) {
        pluralStringResource(R.plurals.sleep_need_personal, need.informativeNights, need.informativeNights)
    } else {
        pluralStringResource(R.plurals.sleep_need_prior, need.nightsUntilPersonal, need.nightsUntilPersonal)
    }
    DriftCard(
        modifier = modifier,
        onClick = { actions.onExplain(MetricKey.SLEEP_NEED) },
        onClickLabel = stringResource(R.string.sleep_why_need),
        contentDescription = stringResource(
            R.string.sleep_need_description,
            DriftFormat.durationSpoken(need.need.value),
            stringResource(need.need.provenance.labelRes()),
            caption,
        ),
    ) {
        DurationHero(
            duration = need.need.value,
            label = stringResource(R.string.sleep_need_label),
            provenance = need.need.provenance,
        )
        DurationConfidenceBand(need.need)
        Caption(caption)
    }
}

@Composable
private fun RegularityCard(section: RegularitySection, actions: SleepActions, modifier: Modifier) {
    DriftCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.sleep_regularity_title), Modifier.weight(1f))
            DriftIconButton(DriftIcons.Info, stringResource(R.string.sleep_why_regularity), { actions.onExplain(MetricKey.REGULARITY) })
        }
        if (!section.available || section.sri7 == null) {
            InsufficientDataPanel(InsufficientReason.TOO_FEW_NIGHTS, section.nightsNeeded)
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = stringResource(R.string.sleep_open_body_clock), role = Role.Button, onClick = actions.onOpenBodyClock),
            ) {
                RegularityBlock(section, trendHeight = 120.dp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TonightCard(model: SleepUiModel, use24h: Boolean, actions: SleepActions, modifier: Modifier) {
    val t = model.tonight
    val bed = t.bedtime.value.clockIn(model.zone, use24h)
    val wake = t.wake.value.clockIn(model.zone, use24h)
    DriftCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.sleep_tonight_title), Modifier.weight(1f))
            DriftIconButton(DriftIcons.Info, stringResource(R.string.sleep_why_recommendation), { actions.onExplain(MetricKey.RECOMMENDATION) })
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md),
        ) {
            TimeColumn(stringResource(R.string.sleep_bedtime), bed, t.bedtime.provenance)
            TimeColumn(stringResource(R.string.sleep_wake), wake, t.wake.provenance)
        }
        StatRow(
            stringResource(R.string.sleep_opportunity),
            DriftFormat.duration(t.opportunity.value),
            t.opportunity.provenance,
        )
        t.tradeOff?.let { trade ->
            DriftCard(tone = CardTone.Accent, contentDescription = stringResource(R.string.sleep_tradeoff_description, trade.message)) {
                Text(
                    stringResource(R.string.sleep_tradeoff_title),
                    style = DriftTheme.typography.titleSmall,
                    color = DriftTheme.colors.onPrimaryContainer,
                )
                Text(trade.message, style = DriftTheme.typography.body, color = DriftTheme.colors.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun TimeColumn(label: String, time: String, provenance: app.drift.model.Provenance) {
    val spoken = stringResource(R.string.sleep_time_description, label, time, stringResource(provenance.labelRes()))
    Column(
        Modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
    ) {
        Caption(label)
        Text(time, style = DriftTheme.typography.display, color = DriftTheme.colors.onSurface)
        ProvenanceChip(provenance)
    }
}
