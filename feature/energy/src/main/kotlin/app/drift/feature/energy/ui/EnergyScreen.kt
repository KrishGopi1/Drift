package app.drift.feature.energy.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.CardTone
import app.drift.designsystem.DriftBottomSheet
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.DriftTopBar
import app.drift.designsystem.ScreenStateHost
import app.drift.designsystem.SectionHeader
import app.drift.designsystem.SegmentedControl
import app.drift.designsystem.staggeredEntrance
import app.drift.feature.energy.EnergyActionError
import app.drift.feature.energy.EnergyUiModel
import app.drift.feature.energy.EnergyViewModel
import app.drift.feature.energy.R
import app.drift.model.EpisodeId
import app.drift.model.SleepEpisode
import app.drift.model.engine.MetricKey
import app.drift.model.repository.SourceFix
import app.drift.model.ui.ScreenState
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the screen can ask its host to do. Defaults are no-ops so previews and tests set only what they check. */
internal class EnergyActions(
    val onExplain: (MetricKey) -> Unit = {},
    val onOpenNight: (EpisodeId) -> Unit = {},
    val onFix: (SourceFix) -> Unit = {},
    val onStartSetup: () -> Unit = {},
    /** Leads to adding a data source. When null the "Improve it" button opens the energy explanation instead. */
    val onOpenSources: (() -> Unit)? = null,
    val onRetry: () -> Unit = {},
    val onConfirm: (EpisodeId) -> Unit = {},
    val onSaveAdjusted: (SleepEpisode, Instant, Instant) -> Unit = { _, _, _ -> },
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
        onRetry = viewModel::retry,
        onConfirm = viewModel::confirm,
        onSaveAdjusted = viewModel::saveAdjusted,
        onCheckIn = viewModel::checkIn,
        onDismissError = viewModel::dismissActionError,
    )
    EnergyScreen(state, wired, modifier)
}

private const val YESTERDAY = 0
private const val TODAY = 1

/**
 * The Energy home screen. [scrollable] is false only for screenshot tests that render the whole page at once.
 */
@Composable
internal fun EnergyScreen(
    state: ScreenState<EnergyUiModel>,
    actions: EnergyActions,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    var selected by rememberSaveable { mutableIntStateOf(TODAY) }
    val ready = (state as? ScreenState.Ready)?.model
    val blankErrorText = stringResource(R.string.energy_error_default)
    val shown = if (state is ScreenState.Error && state.message.isBlank()) state.copy(message = blankErrorText) else state
    val subtitle = ready?.let { dateLabel(it.today.plusDays((selected - TODAY).toLong())) }
    Column(
        modifier
            .background(DriftTheme.colors.background)
            .then(if (scrollable) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier),
    ) {
        DriftTopBar(title = stringResource(R.string.energy_title), subtitle = subtitle)
        Column(
            Modifier.padding(horizontal = DriftTheme.spacing.gutter).padding(bottom = DriftTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sectionGap),
        ) {
            if (state is ScreenState.Loading || state is ScreenState.Ready) {
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.energy_day_yesterday),
                        stringResource(R.string.energy_day_today),
                        stringResource(R.string.energy_day_tomorrow),
                    ),
                    selectedIndex = selected,
                    onSelect = { selected = it },
                    contentDescription = stringResource(R.string.energy_day_switcher_label),
                )
            }
            ScreenStateHost(
                state = shown,
                onRetry = actions.onRetry,
                onFix = actions.onFix,
                onStartSetup = actions.onStartSetup,
            ) { model -> EnergyReady(model, selected, actions) }
        }
    }
}

@Composable
private fun dateLabel(date: LocalDate): String =
    DateTimeFormatter.ofPattern(stringResource(R.string.energy_date_pattern), Locale.getDefault()).format(date)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EnergyReady(model: EnergyUiModel, selected: Int, actions: EnergyActions) {
    val use24h = model.use24h ?: DateFormat.is24HourFormat(LocalContext.current)
    val fmt = TimeFormat(model.zone, use24h)
    val offset = selected - TODAY
    val day = model.dayAt(offset)
    val isToday = selected == TODAY
    val explainEnergy = { actions.onExplain(MetricKey.ENERGY) }
    val explainRecommendation = { actions.onExplain(MetricKey.RECOMMENDATION) }
    var index = 0
    val stagger: @Composable (Int) -> Modifier = { i -> Modifier.staggeredEntrance(i) }
    var adjusting by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sectionGap)) {
        model.actionError?.let { ActionErrorLine(it, actions.onDismissError) }
        model.pending?.let { pending ->
            ConfirmSleepCard(
                episode = pending,
                fmt = fmt,
                onConfirm = { actions.onConfirm(pending.id) },
                onAdjust = { adjusting = true },
                onNotRight = { actions.onOpenNight(pending.id) },
                modifier = stagger(index++),
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
        model.reentrainment?.let {
            ReentrainmentBanner(it, { actions.onExplain(MetricKey.CIRCADIAN) }, stagger(index++))
        }
        if (day == null) {
            DriftCard(modifier = stagger(index++).testTag("energy_no_curve")) {
                Text(stringResource(R.string.energy_no_curve_for_day), style = DriftTheme.typography.body, color = DriftTheme.colors.onSurfaceMuted)
            }
        } else {
            HeadlineCard(model, day, isToday, fmt, dateLabel(model.today.plusDays(offset.toLong())), explainEnergy, stagger(index++))
            if (model.priorsOnly) {
                PriorsBanner(onImprove = actions.onOpenSources ?: explainEnergy, modifier = stagger(index++))
            }
            CurveCard(model, day, isToday, fmt, explainEnergy, stagger(index++))
            NextUpCard(model, day, isToday, fmt, explainEnergy, stagger(index++))
        }
        BedtimeCard(model, fmt, explainRecommendation, stagger(index++))
        if (model.recommendations.habits.isNotEmpty()) {
            SectionHeader(stringResource(R.string.energy_habits_title))
            HabitCards(model, fmt, explainRecommendation, cardModifier = { i -> stagger(i) }, firstIndex = index)
            index += model.recommendations.habits.size.coerceAtMost(4)
        }
        model.recommendations.nap?.let { NapCard(it, fmt, explainRecommendation, stagger(index++)) }
        model.recommendations.tradeOff?.let { TradeOffCard(it, fmt, stagger(index++)) }
        CheckInCard(model.calibrationCheckIns, model.checkInDue, actions.onCheckIn, stagger(index++))
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
