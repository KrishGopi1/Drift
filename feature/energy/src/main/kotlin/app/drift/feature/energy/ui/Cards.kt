package app.drift.feature.energy.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.CardTone
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftDivider
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftIcons
import app.drift.designsystem.DriftListRow
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.labelRes
import app.drift.feature.energy.EnergyUiModel
import app.drift.feature.energy.Upcoming
import app.drift.feature.energy.DIP_PHASES
import app.drift.feature.energy.PEAK_PHASES
import app.drift.feature.energy.R
import app.drift.feature.energy.currentSegment
import app.drift.feature.energy.dayHighlights
import app.drift.feature.energy.isHabitPast
import app.drift.feature.energy.nextSegment
import app.drift.feature.energy.sortHabits
import app.drift.feature.energy.upcomingExtremum
import app.drift.model.Provenance
import app.drift.model.engine.DayCurve
import app.drift.model.engine.HabitCard
import app.drift.model.engine.HabitKind
import app.drift.model.engine.NapAdvice
import app.drift.model.engine.Reentrainment
import app.drift.model.engine.TradeOff
import java.time.Duration

private const val MAX_HABITS_COLLAPSED = 4

/** "Based on typical sleep for now" with an action that leads to adding a data source. */
@Composable
internal fun PriorsBanner(onImprove: () -> Unit, modifier: Modifier = Modifier) {
    DriftCard(modifier = modifier.testTag("energy_priors_banner"), tone = CardTone.Raised) {
        Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.energy_priors_banner),
                style = DriftTheme.typography.bodySmall,
                color = DriftTheme.colors.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        DriftButton(stringResource(R.string.energy_priors_action), onImprove, style = ButtonStyle.Text)
    }
}

@Composable
internal fun ReentrainmentBanner(reentrainment: Reentrainment, onExplain: () -> Unit, modifier: Modifier = Modifier) {
    val days = pluralStringResource(R.plurals.energy_travel_days_left, reentrainment.estimatedDaysLeft, reentrainment.estimatedDaysLeft)
    val shift = DriftFormat.duration(reentrainment.remainingShift)
    val text = stringResource(
        if (reentrainment.eastward) R.string.energy_travel_east else R.string.energy_travel_west,
        days, shift,
    )
    DriftCard(modifier = modifier.testTag("energy_travel_banner"), tone = CardTone.Raised, onClick = onExplain, contentDescription = text) {
        Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(DriftIcons.Info, null, tint = DriftTheme.colors.windDown, modifier = Modifier.size(24.dp))
            Text(text, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurface, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun NextUpCard(
    model: EnergyUiModel,
    day: DayCurve,
    isToday: Boolean,
    fmt: TimeFormat,
    modifier: Modifier = Modifier,
) {
    val highlights = dayHighlights(day)
    val peak = if (isToday) upcomingExtremum(model.days, model.now, model.zone, PEAK_PHASES) else highlights.peak?.let { Upcoming(it, app.drift.model.engine.EnergyPhase.MORNING_PEAK, false) }
    val dip = if (isToday) upcomingExtremum(model.days, model.now, model.zone, DIP_PHASES) else highlights.dip?.let { Upcoming(it, app.drift.model.engine.EnergyPhase.AFTERNOON_DIP, false) }
    DriftCard(modifier = modifier.testTag("energy_next_up")) {
        DriftListRow(
            title = stringResource(if (isToday) R.string.energy_next_peak else R.string.energy_peak),
            subtitle = peak?.let { upcomingTime(it, fmt) } ?: stringResource(R.string.energy_no_more_peaks),
            modifier = Modifier.testTag("energy_next_peak"),
        )
        DriftDivider()
        if (day.tooFlatForDip) {
            DriftListRow(
                title = stringResource(if (isToday) R.string.energy_flat_today else R.string.energy_flat_day),
                subtitle = stringResource(R.string.energy_flat_sub),
                modifier = Modifier.testTag("energy_flat_dip"),
            )
        } else {
            DriftListRow(
                title = stringResource(if (isToday) R.string.energy_next_dip else R.string.energy_dip),
                subtitle = dip?.let { upcomingTime(it, fmt) } ?: stringResource(R.string.energy_no_dip),
                modifier = Modifier.testTag("energy_next_dip"),
            )
        }
    }
}

@Composable
private fun upcomingTime(upcoming: Upcoming, fmt: TimeFormat): String =
    if (upcoming.laterDay) stringResource(R.string.energy_time_tomorrow, fmt.clock(upcoming.at)) else fmt.clock(upcoming.at)

@Composable
internal fun BedtimeCard(model: EnergyUiModel, fmt: TimeFormat, onExplain: () -> Unit, modifier: Modifier = Modifier) {
    val rec = model.recommendations
    val bed = fmt.clock(rec.bedtime.value)
    val wake = fmt.clock(rec.wakeTime.value)
    val opportunity = DriftFormat.duration(rec.sleepOpportunity.value)
    val spoken = stringResource(
        R.string.energy_bedtime_a11y, bed, wake, DriftFormat.durationSpoken(rec.sleepOpportunity.value),
    )
    DriftCard(
        modifier = modifier.testTag("energy_bedtime_card"),
        contentDescription = spoken,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.energy_tonight_title), style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            app.drift.designsystem.DriftIconButton(DriftIcons.Help, stringResource(R.string.energy_why_times), onExplain)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xl)) {
            TimeColumn(stringResource(R.string.energy_bedtime), bed)
            TimeColumn(stringResource(R.string.energy_wake_time), wake)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.energy_sleep_opportunity, opportunity),
                style = DriftTheme.typography.bodySmall,
                color = DriftTheme.colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun TimeColumn(label: String, time: String) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xxs)) {
        Text(label, style = DriftTheme.typography.label, color = DriftTheme.colors.onSurfaceMuted)
        Text(time, style = DriftTheme.typography.headline, color = DriftTheme.colors.onSurface)
    }
}

/** Habit-timing cards: at most four until "Show all"; windows already over are dimmed and listed last. */
@Composable
internal fun HabitCards(
    model: EnergyUiModel,
    fmt: TimeFormat,
    cardModifier: @Composable (Int) -> Modifier,
    firstIndex: Int,
) {
    val sorted = sortHabits(model.recommendations.habits, model.now)
    var showAll by remember { mutableStateOf(false) }
    val visible = if (showAll) sorted else sorted.take(MAX_HABITS_COLLAPSED)
    visible.forEachIndexed { i, habit ->
        HabitCardView(habit, isHabitPast(habit, model.now), fmt, cardModifier(firstIndex + i))
    }
    if (sorted.size > MAX_HABITS_COLLAPSED) {
        DriftButton(
            label = stringResource(if (showAll) R.string.energy_habits_show_fewer else R.string.energy_habits_show_all),
            onClick = { showAll = !showAll },
            style = ButtonStyle.Text,
        )
    }
}

@Composable
private fun HabitCardView(habit: HabitCard, past: Boolean, fmt: TimeFormat, modifier: Modifier) {
    val title = stringResource(habit.kind.titleRes())
    val window = habitInstruction(habit, fmt)
    val basis = basisText(habit.basis)
    val muted = DriftTheme.colors.onSurfaceMuted
    DriftCard(
        modifier = modifier.testTag("energy_habit_${habit.kind.name}"),
        contentDescription = buildString {
            append(title).append(": ").append(window).append(". ").append(basis)
            if (past) append(". ").append(stringResource(R.string.energy_habit_passed))
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            HabitIcon(habit.kind, past)
            androidx.compose.foundation.layout.Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xxs),
            ) {
                Text(title, style = DriftTheme.typography.titleSmall, color = if (past) muted else DriftTheme.colors.onSurface)
                Text(window, style = DriftTheme.typography.body, color = if (past) muted else DriftTheme.colors.onSurface)
                Text(basis, style = DriftTheme.typography.caption, color = muted)
            }
        }
    }
}

@Composable
private fun HabitIcon(kind: HabitKind, past: Boolean) {
    val bg = if (past) DriftTheme.colors.surfaceRaised else DriftTheme.colors.primaryContainer
    val tint = if (past) DriftTheme.colors.onSurfaceMuted else DriftTheme.colors.onPrimaryContainer
    Box(
        Modifier.size(32.dp).clearAndSetSemantics {}.background(bg, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(kind.icon(), null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun NapCard(nap: NapAdvice, fmt: TimeFormat, modifier: Modifier = Modifier) {
    val window = stringResource(R.string.energy_habit_window, fmt.clock(nap.window.start), fmt.clock(nap.window.end))
    val max = stringResource(R.string.energy_nap_max, DriftFormat.duration(nap.maxDuration))
    DriftCard(modifier = modifier.testTag("energy_nap_card")) {
        Text(stringResource(R.string.energy_nap_title), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
        Text(window, style = DriftTheme.typography.headline, color = DriftTheme.colors.onSurface)
        Text(max, style = DriftTheme.typography.body, color = DriftTheme.colors.onSurface)
        Text(nap.reason, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
    }
}

@Composable
internal fun TradeOffCard(tradeOff: TradeOff, fmt: TimeFormat, modifier: Modifier = Modifier) {
    var showLater by remember { mutableStateOf(false) }
    DriftCard(modifier = modifier.testTag("energy_tradeoff_card"), tone = CardTone.Accent) {
        Text(stringResource(R.string.energy_tradeoff_title), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onPrimaryContainer)
        Text(tradeOff.message, style = DriftTheme.typography.body, color = DriftTheme.colors.onPrimaryContainer)
        if (showLater) {
            Text(
                stringResource(R.string.energy_tradeoff_later, fmt.clock(tradeOff.bedtimeIfFollowingWindow)),
                style = DriftTheme.typography.titleSmall,
                color = DriftTheme.colors.onPrimaryContainer,
            )
        } else {
            DriftButton(stringResource(R.string.energy_tradeoff_action), { showLater = true }, style = ButtonStyle.Secondary)
        }
    }
}
