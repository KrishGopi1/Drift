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
import app.drift.charts.EnergyTimelineChart
import app.drift.charts.TimelineMarker
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.CardTone
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftDivider
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftIcons
import app.drift.designsystem.DriftListRow
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.ProvenanceChip
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeadlineCard(
    model: EnergyUiModel,
    day: DayCurve,
    isToday: Boolean,
    fmt: TimeFormat,
    dateLabel: String,
    onExplain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val segment = if (isToday) currentSegment(day, model.now) else null
    val next = if (isToday) nextSegment(model.days, model.now, model.zone) else null
    val confidence = if (model.priorsOnly) app.drift.model.ConfidenceLevel.LOW else day.confidence
    val phaseText = segment?.phase?.let { stringResource(it.labelRes()) }
    val nextText = next?.let { nextEventText(it, model, fmt) }
    val summary = if (isToday) null else daySummary(day, fmt)
    val label = if (isToday) stringResource(R.string.energy_right_now) else dateLabel
    val provenanceText = stringResource(day.provenance.labelRes())
    val confidenceText = stringResource(confidence.labelRes())
    val spokenNext = if (next != null) {
        stringResource(
            R.string.energy_headline_a11y,
            phaseText.orEmpty(), stringResource(next.phase.labelRes()).lowercase(), fmt.clock(next.displayAt),
            DriftFormat.durationSpoken(Duration.between(model.now, next.displayAt)), provenanceText, confidenceText,
        )
    } else if (isToday) {
        stringResource(R.string.energy_headline_a11y_no_next, phaseText.orEmpty(), provenanceText, confidenceText)
    } else {
        stringResource(R.string.energy_headline_a11y_day, dateLabel, summary.orEmpty(), provenanceText, confidenceText)
    }
    DriftCard(
        modifier = modifier.testTag("energy_headline"),
        tone = CardTone.Default,
        onClick = onExplain,
        onClickLabel = stringResource(R.string.energy_why_label),
        contentDescription = spokenNext,
    ) {
        Text(label, style = DriftTheme.typography.label, color = DriftTheme.colors.onSurfaceMuted)
        if (isToday) {
            Crossfade(
                targetState = phaseText.orEmpty(),
                animationSpec = DriftTheme.motion.spec(DriftTheme.reducedMotion, DriftTheme.motion.short),
                label = "phase",
            ) { text ->
                Text(text, style = DriftTheme.typography.headline, color = segment?.phase?.color() ?: DriftTheme.colors.onSurface)
            }
            if (nextText != null) Text(nextText, style = DriftTheme.typography.body, color = DriftTheme.colors.onSurface)
        } else if (summary != null) {
            Text(summary, style = DriftTheme.typography.headline, color = DriftTheme.colors.onSurface)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ProvenanceChip(day.provenance)
            Text(confidenceText, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
        }
        if (model.priorsOnly) {
            Text(
                stringResource(R.string.energy_based_on_people_like_you),
                style = DriftTheme.typography.bodySmall,
                color = DriftTheme.colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun nextEventText(next: Upcoming, model: EnergyUiModel, fmt: TimeFormat): String {
    val phase = stringResource(next.phase.labelRes())
    val at = fmt.clock(next.displayAt)
    val exact = next.extremumAt != null
    return if (next.laterDay) {
        stringResource(if (exact) R.string.energy_next_extremum_later else R.string.energy_next_event_later, phase, at)
    } else {
        stringResource(
            if (exact) R.string.energy_next_extremum else R.string.energy_next_event,
            phase, at, DriftFormat.duration(Duration.between(model.now, next.displayAt)),
        )
    }
}

@Composable
private fun daySummary(day: DayCurve, fmt: TimeFormat): String {
    val h = dayHighlights(day)
    return when {
        h.peak != null && h.dip != null -> stringResource(R.string.energy_day_summary, fmt.clock(h.peak), fmt.clock(h.dip))
        h.peak != null -> stringResource(R.string.energy_day_summary_peak_only, fmt.clock(h.peak))
        else -> stringResource(R.string.energy_day_summary_none)
    }
}

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
            ProvenanceChip(Provenance.ESTIMATED)
        }
        DriftButton(stringResource(R.string.energy_priors_action), onImprove, style = ButtonStyle.Text)
    }
}

@Composable
internal fun CurveCard(
    model: EnergyUiModel,
    day: DayCurve,
    isToday: Boolean,
    fmt: TimeFormat,
    onExplain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val habits = if (isToday) model.recommendations.habits else emptyList()
    val markers = habits.map { habit ->
        val start = fmt.clock(habit.window.start)
        val end = fmt.clock(habit.window.end)
        TimelineMarker(
            at = habit.window.start,
            time = if (start == end) start else stringResource(R.string.energy_habit_window, start, end),
            title = stringResource(habit.kind.titleRes()),
        )
    }
    androidx.compose.foundation.layout.Column(
        modifier
            .testTag("energy_curve_card")
            .clickable(onClickLabel = stringResource(R.string.energy_why_label), role = Role.Button, onClick = onExplain),
        verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
    ) {
        if (model.shiftWorkMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(DriftIcons.Moon, null, tint = DriftTheme.colors.melatonin, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.energy_shift_work_banner),
                    style = DriftTheme.typography.bodySmall,
                    color = DriftTheme.colors.onSurface,
                )
            }
        }
        EnergyTimelineChart(
            dayCurve = day,
            now = if (isToday) model.now else null,
            zone = fmt.zone,
            use24h = fmt.use24h,
            markers = markers,
            peakLabel = stringResource(R.string.energy_peak),
            dipLabel = stringResource(R.string.energy_dip),
        )
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
    onExplain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlights = dayHighlights(day)
    val peak = if (isToday) upcomingExtremum(model.days, model.now, model.zone, PEAK_PHASES) else highlights.peak?.let { Upcoming(it, app.drift.model.engine.EnergyPhase.MORNING_PEAK, false) }
    val dip = if (isToday) upcomingExtremum(model.days, model.now, model.zone, DIP_PHASES) else highlights.dip?.let { Upcoming(it, app.drift.model.engine.EnergyPhase.AFTERNOON_DIP, false) }
    DriftCard(modifier = modifier.testTag("energy_next_up")) {
        DriftListRow(
            title = stringResource(if (isToday) R.string.energy_next_peak else R.string.energy_peak),
            subtitle = peak?.let { upcomingTime(it, fmt) } ?: stringResource(R.string.energy_no_more_peaks),
            provenance = day.provenance,
            onClick = onExplain,
            onClickLabel = stringResource(R.string.energy_why_label),
            modifier = Modifier.testTag("energy_next_peak"),
        )
        DriftDivider()
        if (day.tooFlatForDip) {
            DriftListRow(
                title = stringResource(if (isToday) R.string.energy_flat_today else R.string.energy_flat_day),
                subtitle = stringResource(R.string.energy_flat_sub),
                onClick = onExplain,
                onClickLabel = stringResource(R.string.energy_why_label),
                modifier = Modifier.testTag("energy_flat_dip"),
            )
        } else {
            DriftListRow(
                title = stringResource(if (isToday) R.string.energy_next_dip else R.string.energy_dip),
                subtitle = dip?.let { upcomingTime(it, fmt) } ?: stringResource(R.string.energy_no_dip),
                provenance = day.provenance,
                onClick = onExplain,
                onClickLabel = stringResource(R.string.energy_why_label),
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
        onClick = onExplain,
        onClickLabel = stringResource(R.string.energy_why_label),
        contentDescription = spoken,
    ) {
        Text(stringResource(R.string.energy_tonight_title), style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xl)) {
            TimeColumn(stringResource(R.string.energy_bedtime), bed)
            TimeColumn(stringResource(R.string.energy_wake_time), wake)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            ProvenanceChip(rec.bedtime.provenance)
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
    onExplain: () -> Unit,
    cardModifier: @Composable (Int) -> Modifier,
    firstIndex: Int,
) {
    val sorted = sortHabits(model.recommendations.habits, model.now)
    var showAll by remember { mutableStateOf(false) }
    val visible = if (showAll) sorted else sorted.take(MAX_HABITS_COLLAPSED)
    visible.forEachIndexed { i, habit ->
        HabitCardView(habit, isHabitPast(habit, model.now), fmt, onExplain, cardModifier(firstIndex + i))
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
private fun HabitCardView(habit: HabitCard, past: Boolean, fmt: TimeFormat, onExplain: () -> Unit, modifier: Modifier) {
    val title = stringResource(habit.kind.titleRes())
    val window = habitInstruction(habit, fmt)
    val basis = basisText(habit.basis)
    val muted = DriftTheme.colors.onSurfaceMuted
    DriftCard(
        modifier = modifier.testTag("energy_habit_${habit.kind.name}"),
        onClick = onExplain,
        onClickLabel = stringResource(R.string.energy_why_label),
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
    val tint = if (past) DriftTheme.colors.onSurfaceMuted else when (kind) {
        HabitKind.DIM_LIGHTS -> DriftTheme.colors.windDown
        HabitKind.MORNING_LIGHT, HabitKind.EXERCISE_WINDOW -> DriftTheme.colors.energy
        else -> DriftTheme.colors.primary
    }
    val icon: ImageVector? = when (kind) {
        HabitKind.MORNING_LIGHT, HabitKind.EXERCISE_WINDOW -> DriftIcons.Energy
        HabitKind.DIM_LIGHTS -> DriftIcons.Moon
        else -> null
    }
    Box(Modifier.size(24.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        else Box(Modifier.size(12.dp).background(tint, CircleShape).alpha(0.9f))
    }
}

@Composable
internal fun NapCard(nap: NapAdvice, fmt: TimeFormat, onExplain: () -> Unit, modifier: Modifier = Modifier) {
    val window = stringResource(R.string.energy_habit_window, fmt.clock(nap.window.start), fmt.clock(nap.window.end))
    val max = stringResource(R.string.energy_nap_max, DriftFormat.duration(nap.maxDuration))
    DriftCard(
        modifier = modifier.testTag("energy_nap_card"),
        onClick = onExplain,
        onClickLabel = stringResource(R.string.energy_why_label),
    ) {
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
