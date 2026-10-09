package app.drift.feature.energy.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.charts.EnergyTimeline
import app.drift.charts.TimelineBlock
import app.drift.charts.TimelineMarker
import app.drift.charts.energyCurveDescription
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.CardTone
import app.drift.designsystem.DriftBottomSheet
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftIcons
import app.drift.designsystem.DriftScreenHeader
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.HeaderSubtitle
import app.drift.designsystem.ProfileButton
import app.drift.designsystem.ScreenStateHost
import app.drift.designsystem.SectionHeader
import app.drift.feature.energy.EnergyActionError
import app.drift.feature.energy.EnergyUiModel
import app.drift.feature.energy.EnergyViewModel
import app.drift.feature.energy.R
import app.drift.feature.energy.currentSegment
import app.drift.feature.energy.nextSegment
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.SleepEpisode
import app.drift.model.SourceId
import app.drift.model.engine.EnergyLabel
import app.drift.model.engine.EnergyPotential
import app.drift.model.engine.MetricKey
import app.drift.model.repository.SourceFix
import app.drift.model.ui.ScreenState
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** Everything the screen can ask its host to do. Defaults are no-ops so previews and tests set only what they check. */
internal class EnergyActions(
    val onExplain: (MetricKey) -> Unit = {},
    val onOpenNight: (EpisodeId) -> Unit = {},
    val onFix: (SourceFix) -> Unit = {},
    val onStartSetup: () -> Unit = {},
    /** Leads to adding a data source. When null the "Improve it" button opens the energy explanation instead. */
    val onOpenSources: (() -> Unit)? = null,
    val onOpenSettings: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onConfirm: (EpisodeId) -> Unit = {},
    val onSaveAdjusted: (SleepEpisode, Instant, Instant) -> Unit = { _, _, _ -> },
    /** A sleep block is being dragged (preview only). */
    val onDragSleep: (SleepEpisode, Instant, Instant) -> Unit = { _, _, _ -> },
    /** A drag ended: save the new times. */
    val onCommitSleep: (SleepEpisode, Instant, Instant) -> Unit = { _, _, _ -> },
    val onCheckIn: (Int) -> Unit = {},
    val onDismissError: () -> Unit = {},
)

/** Hilt-wired entry used by the navigation graph. */
@Composable
internal fun EnergyRoute(
    actions: EnergyActions,
    modifier: Modifier = Modifier,
    viewModel: EnergyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wired = EnergyActions(
        onExplain = actions.onExplain,
        onOpenNight = actions.onOpenNight,
        onFix = actions.onFix,
        onStartSetup = actions.onStartSetup,
        onOpenSources = actions.onOpenSources,
        onOpenSettings = actions.onOpenSettings,
        onRetry = viewModel::retry,
        onConfirm = viewModel::confirm,
        onSaveAdjusted = viewModel::saveAdjusted,
        onDragSleep = viewModel::dragSleep,
        onCommitSleep = viewModel::commitSleep,
        onCheckIn = viewModel::checkIn,
        onDismissError = viewModel::dismissActionError,
    )
    EnergyScreen(state, wired, modifier)
}

/**
 * The Energy tab: header with today's energy potential, then the whole day as one vertical timeline (last night's
 * sleep, today, tonight) that fills the screen, with a pull-up panel for what's next, tonight's times and habits.
 * [sheetExpanded] starts the panel open (screenshot tests).
 */
@Composable
internal fun EnergyScreen(
    state: ScreenState<EnergyUiModel>,
    actions: EnergyActions,
    modifier: Modifier = Modifier,
    sheetExpanded: Boolean = false,
) {
    val ready = (state as? ScreenState.Ready)?.model
    val blankErrorText = stringResource(R.string.energy_error_default)
    val shown = if (state is ScreenState.Error && state.message.isBlank()) state.copy(message = blankErrorText) else state
    Column(modifier.fillMaxSize().background(DriftTheme.colors.background)) {
        DriftScreenHeader(
            title = stringResource(R.string.energy_header_title),
            subtitle = { if (ready != null) PotentialLine(ready, actions) },
            trailing = { ProfileButton(actions.onOpenSettings) },
        )
        if (ready != null) {
            EnergyReady(ready, actions, sheetExpanded, Modifier.weight(1f))
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = DriftTheme.spacing.gutter),
            ) {
                ScreenStateHost(state = shown, onRetry = actions.onRetry, onFix = actions.onFix, onStartSetup = actions.onStartSetup) {}
            }
        }
    }
}

@Composable
private fun labelText(label: EnergyLabel): String = stringResource(
    when (label) {
        EnergyLabel.SUPER -> R.string.energy_label_super
        EnergyLabel.GREAT -> R.string.energy_label_great
        EnergyLabel.GOOD -> R.string.energy_label_good
        EnergyLabel.FAIR -> R.string.energy_label_fair
        EnergyLabel.LOW -> R.string.energy_label_low
    },
)

@Composable
internal fun labelColor(label: EnergyLabel): Color = when (label) {
    EnergyLabel.SUPER, EnergyLabel.GREAT -> DriftTheme.colors.success
    EnergyLabel.GOOD -> DriftTheme.colors.energy
    EnergyLabel.FAIR -> DriftTheme.colors.debt
    EnergyLabel.LOW -> DriftTheme.colors.error
}

/** "Great ⚡ 85% ?" under the title, or the current phase while debt is not available yet. */
@Composable
private fun PotentialLine(model: EnergyUiModel, actions: EnergyActions) {
    val potential: EnergyPotential? = model.potential
    if (potential == null) {
        val phase = currentSegment(model.dayAt(0), model.now)?.phase
        HeaderSubtitle(phase?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.energy_right_now))
        return
    }
    val label = labelText(potential.label)
    val pct = potential.score.value.roundToInt()
    val color = labelColor(potential.label)
    val spoken = stringResource(
        R.string.energy_potential_a11y, label, pct, stringResource(potential.score.confidence!!.level.labelRes()),
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(DriftTheme.radii.sm))
            .clickable(onClickLabel = stringResource(R.string.energy_why_label), role = Role.Button) { actions.onExplain(MetricKey.ENERGY) }
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .testTag("energy_potential"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HeaderSubtitle(label)
        Icon(DriftIcons.Energy, null, tint = color, modifier = Modifier.size(20.dp))
        Text("$pct%", style = DriftTheme.typography.screenSubtitle, color = color)
        Icon(DriftIcons.Help, null, tint = color, modifier = Modifier.padding(start = 2.dp).size(18.dp))
        if (model.previewing) {
            Text(
                stringResource(R.string.energy_previewing),
                style = DriftTheme.typography.caption,
                color = DriftTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(start = DriftTheme.spacing.sm).testTag("energy_previewing"),
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EnergyReady(model: EnergyUiModel, actions: EnergyActions, sheetExpanded: Boolean, modifier: Modifier) {
    val use24h = model.use24h ?: DateFormat.is24HourFormat(LocalContext.current)
    val fmt = TimeFormat(model.zone, use24h)
    var adjusting by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = DriftTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
        ) {
            model.actionError?.let { ActionErrorLine(it, actions.onDismissError) }
            model.pending?.let { pending ->
                ConfirmSleepCard(
                    episode = pending,
                    fmt = fmt,
                    onConfirm = { actions.onConfirm(pending.id) },
                    onAdjust = { adjusting = true },
                    onNotRight = { actions.onOpenNight(pending.id) },
                )
                if (adjusting) {
                    DriftBottomSheet(onDismiss = { adjusting = false }, title = stringResource(R.string.energy_adjust_title)) {
                        AdjustSleepContent(
                            episode = pending,
                            fmt = fmt,
                            onSave = { start, end ->
                                adjusting = false
                                actions.onSaveAdjusted(pending, start, end)
                            },
                            onCancel = { adjusting = false },
                        )
                    }
                }
            }
            model.reentrainment?.let { ReentrainmentBanner(it, { actions.onExplain(MetricKey.CIRCADIAN) }) }
            if (model.priorsOnly) PriorsBanner(onImprove = actions.onOpenSources ?: { actions.onExplain(MetricKey.ENERGY) })
            if (model.shiftWorkMode) ShiftWorkLine()
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Timeline(model, fmt, actions, Modifier.fillMaxSize())
            UpNextSheet(model, fmt, actions, sheetExpanded, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ShiftWorkLine() {
    Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Icon(DriftIcons.Moon, null, tint = DriftTheme.colors.melatonin, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.energy_shift_work_banner), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurface)
    }
}

@Composable
private fun sourceText(id: SourceId): String = stringResource(
    when (id) {
        SourceId.HEALTH_CONNECT -> R.string.energy_source_health_connect
        SourceId.SLEEP_AS_ANDROID -> R.string.energy_source_saa
        SourceId.PHONE -> R.string.energy_source_phone
        SourceId.USER -> R.string.energy_source_user
        else -> R.string.energy_source_other
    },
)

@Composable
private fun Timeline(model: EnergyUiModel, fmt: TimeFormat, actions: EnergyActions, modifier: Modifier) {
    val totalSleep = stringResource(R.string.energy_total_sleep)
    val nap = stringResource(R.string.energy_nap)
    val tonight = stringResource(R.string.energy_tonight_title)
    val recommended = stringResource(R.string.energy_recommended)
    val past = model.episodes.filter { !it.end.isAfter(model.now) }
    val blocks = past.map { ep ->
        TimelineBlock(
            key = ep.id.value,
            start = ep.start,
            end = ep.end,
            editable = true,
            heading = if (ep.kind == EpisodeKind.NAP) nap else totalSleep,
            value = DriftFormat.hm(Duration.between(ep.start, ep.end)),
            detail = sourceText(ep.origin.sourceId),
        )
    } + listOfNotNull(
        model.recommendations.let { rec ->
            val bed = rec.bedtime.value
            val wake = rec.wakeTime.value
            if (bed.isAfter(model.now) && wake.isAfter(bed) && past.none { it.interval.overlaps(app.drift.model.Interval(bed, wake)) }) {
                TimelineBlock(
                    key = "planned", start = bed, end = wake, editable = false, planned = true,
                    heading = tonight, value = "${fmt.clock(bed)} – ${fmt.clock(wake)}", detail = recommended,
                )
            } else null
        },
    )
    val markers = buildMarkers(model, fmt)
    val description = energyCurveDescription(model.dayAt(0) ?: model.days.first(), model.zone, fmt.use24h, model.now)
    val asleep = stringResource(R.string.energy_handle_start)
    val woke = stringResource(R.string.energy_handle_end)
    EnergyTimeline(
        days = model.days,
        now = model.now,
        zone = model.zone,
        use24h = fmt.use24h,
        contentDescription = description,
        modifier = modifier,
        blocks = blocks,
        markers = markers,
        peakLabel = stringResource(R.string.energy_peak),
        dipLabel = stringResource(R.string.energy_dip),
        handleLabel = { b, top -> String.format(if (top) asleep else woke, fmt.clock(if (top) b.start else b.end)) },
        earlierLabel = stringResource(R.string.energy_earlier),
        laterLabel = stringResource(R.string.energy_later),
        onBlockDrag = { key, s, e -> past.firstOrNull { it.id.value == key }?.let { actions.onDragSleep(it, s, e) } },
        onBlockDragEnd = { key, s, e -> past.firstOrNull { it.id.value == key }?.let { actions.onCommitSleep(it, s, e) } },
        onBlockClick = { key ->
            if (key == "planned") actions.onExplain(MetricKey.RECOMMENDATION) else actions.onOpenNight(EpisodeId(key))
        },
    )
}

@Composable
private fun buildMarkers(model: EnergyUiModel, fmt: TimeFormat): List<TimelineMarker> {
    val habits = model.recommendations.habits.map { habit ->
        val start = fmt.short(habit.window.start)
        val end = fmt.short(habit.window.end)
        TimelineMarker(
            at = habit.window.start,
            time = if (start == end) start else "$start – $end",
            title = stringResource(habit.kind.titleRes()),
            icon = habit.kind.icon(),
        )
    }
    val melatonin = model.melatoninWindow?.let {
        TimelineMarker(it.start, "${fmt.short(it.start)} – ${fmt.short(it.end)}", stringResource(R.string.energy_phase_melatonin), DriftIcons.MoonSmall)
    }
    val nap = model.recommendations.nap?.let {
        TimelineMarker(it.window.start, "${fmt.short(it.window.start)} – ${fmt.short(it.window.end)}", stringResource(R.string.energy_nap_window), DriftIcons.Moon)
    }
    return habits + listOfNotNull(melatonin, nap)
}

/**
 * Pull-up panel over the bottom of the timeline. Collapsed it shows the current phase and what comes next; expanded
 * it holds next peak and dip, tonight's times, habits, nap and trade-off advice and the check-in.
 */
@Composable
private fun UpNextSheet(model: EnergyUiModel, fmt: TimeFormat, actions: EnergyActions, startExpanded: Boolean, modifier: Modifier) {
    var expanded by rememberSaveable { mutableStateOf(startExpanded) }
    val today = model.dayAt(0)
    val phase = today?.let { currentSegment(it, model.now)?.phase }
    val next = nextSegment(model.days, model.now, model.zone)
    val summary = buildString {
        phase?.let { append(stringResource(it.labelRes())) }
        if (next != null) {
            if (isNotEmpty()) append(" · ")
            append(stringResource(R.string.energy_next_short, stringResource(next.phase.labelRes()), fmt.short(next.displayAt)))
        }
    }
    val explainEnergy = { actions.onExplain(MetricKey.ENERGY) }
    val explainRecommendation = { actions.onExplain(MetricKey.RECOMMENDATION) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = DriftTheme.radii.lg, topEnd = DriftTheme.radii.lg))
            .background(DriftTheme.colors.surface)
            .testTag("energy_sheet"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(if (expanded) R.string.energy_sheet_collapse else R.string.energy_sheet_expand), role = Role.Button) {
                    expanded = !expanded
                }
                .semantics { stateDescription = if (expanded) "expanded" else "collapsed" }
                .heightIn(min = 56.dp)
                .padding(horizontal = DriftTheme.spacing.gutter, vertical = DriftTheme.spacing.sm)
                .testTag("energy_sheet_header"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = DriftTheme.spacing.xs)
                        .size(width = 36.dp, height = 4.dp)
                        .background(DriftTheme.colors.outline, RoundedCornerShape(2.dp)),
                )
                Text(stringResource(R.string.energy_up_next), style = DriftTheme.typography.overline, color = DriftTheme.colors.onSurfaceMuted)
                Text(summary, style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
            }
            Icon(
                if (expanded) DriftIcons.ChevronDown else DriftIcons.ChevronUp, null,
                tint = DriftTheme.colors.onSurfaceMuted, modifier = Modifier.width(28.dp),
            )
        }
        AnimatedVisibility(expanded) {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DriftTheme.spacing.gutter)
                    .padding(bottom = DriftTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md),
            ) {
                if (today != null) NextUpCard(model, today, isToday = true, fmt = fmt, onExplain = explainEnergy)
                BedtimeCard(model, fmt, explainRecommendation)
                if (model.recommendations.habits.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.energy_habits_title))
                    HabitCards(model, fmt, explainRecommendation, cardModifier = { Modifier }, firstIndex = 0)
                }
                model.recommendations.nap?.let { NapCard(it, fmt, explainRecommendation) }
                model.recommendations.tradeOff?.let { TradeOffCard(it, fmt) }
                CheckInCard(model.calibrationCheckIns, model.checkInDue, actions.onCheckIn)
            }
        }
    }
}

@Composable
private fun ActionErrorLine(error: EnergyActionError, onDismiss: () -> Unit) {
    val text = stringResource(
        when (error) {
            EnergyActionError.CONFIRM_FAILED -> R.string.energy_action_confirm_failed
            EnergyActionError.SAVE_FAILED -> R.string.energy_action_save_failed
            EnergyActionError.CHECK_IN_FAILED -> R.string.energy_action_checkin_failed
        },
    )
    DriftCard(modifier = Modifier.testTag("energy_action_error"), tone = CardTone.Raised) {
        Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.error, modifier = Modifier.weight(1f))
            DriftButton(stringResource(R.string.energy_action_dismiss), onDismiss, style = ButtonStyle.Text)
        }
    }
}
