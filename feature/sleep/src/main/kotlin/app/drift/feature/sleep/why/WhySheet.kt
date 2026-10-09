package app.drift.feature.sleep.why

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.DriftBottomSheet
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftIcons
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.DurationConfidenceBand
import app.drift.designsystem.ErrorPanel
import app.drift.designsystem.LoadingSkeleton
import app.drift.designsystem.ProvenanceChip
import app.drift.designsystem.SheetBody
import app.drift.feature.sleep.R
import app.drift.feature.sleep.common.Caption
import app.drift.feature.sleep.common.shortLabel
import app.drift.model.engine.Cause
import app.drift.model.engine.CauseKind
import app.drift.model.engine.MetricKey
import app.drift.model.ui.ScreenState
import java.time.Duration
import java.time.LocalDate

data class WhyActions(
    val onDismiss: () -> Unit = {},
    val onOpenDate: (LocalDate) -> Unit = {},
    val onRetry: () -> Unit = {},
    /** Opens Settings > Methodology for the metric. Null hides the button because the host did not provide it. */
    val onOpenMethodology: ((MetricKey) -> Unit)? = null,
)

@Composable
fun metricTitle(metric: MetricKey): String = stringResource(
    when (metric) {
        MetricKey.SLEEP_NEED -> R.string.why_metric_need
        MetricKey.SLEEP_DEBT -> R.string.why_metric_debt
        MetricKey.REGULARITY -> R.string.why_metric_regularity
        MetricKey.CHRONOTYPE -> R.string.why_metric_chronotype
        MetricKey.CIRCADIAN -> R.string.why_metric_circadian
        MetricKey.ENERGY -> R.string.why_metric_energy
        MetricKey.RECOMMENDATION -> R.string.why_metric_recommendation
    },
)

/** The modal bottom sheet. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WhySheet(metric: MetricKey, state: ScreenState<WhyUiModel>, actions: WhyActions) {
    DriftBottomSheet(onDismiss = actions.onDismiss, title = metricTitle(metric)) {
        WhyContent(state, actions)
    }
}

/** Sheet with an explicit body, for screenshots and tests that cannot open a window. */
@Composable
fun WhySheetBody(metric: MetricKey, state: ScreenState<WhyUiModel>, actions: WhyActions, modifier: Modifier = Modifier) {
    SheetBody(title = metricTitle(metric), onClose = actions.onDismiss, modifier = modifier) {
        WhyContent(state, actions)
    }
}

@Composable
fun WhyContent(state: ScreenState<WhyUiModel>, actions: WhyActions) {
    when (state) {
        ScreenState.Loading -> LoadingSkeleton()
        is ScreenState.Error -> ErrorPanel(state.message, onRetry = if (state.retryable) actions.onRetry else null)
        is ScreenState.Ready -> WhyReady(state.model, actions)
        // The sheet only ever loads, succeeds or fails; the other variants cannot occur.
        else -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhyReady(model: WhyUiModel, actions: WhyActions) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md)) {
        when (model.variant) {
            WhyVariant.FIRST_DAY -> {
                Headline(stringResource(R.string.why_first_day_headline))
                CauseRow(Cause(CauseKind.FIRST_RUN, null, null, stringResource(R.string.why_first_day_cause)), actions)
            }
            WhyVariant.NO_CHANGE -> {
                Headline(stringResource(R.string.why_none_headline))
                Text(stringResource(R.string.why_none_body), style = DriftTheme.typography.body, color = DriftTheme.colors.onSurfaceMuted)
            }
            WhyVariant.CHANGES -> model.explanations.forEach { explanation ->
                Headline(explanation.headline)
                explanation.causes.forEach { CauseRow(it, actions) }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            model.provenance?.let { ProvenanceChip(it) }
        }
        model.duration?.let { DurationConfidenceBand(it) }
        actions.onOpenMethodology?.let { open ->
            DriftButton(stringResource(R.string.why_methodology), { open(model.metric) }, style = ButtonStyle.Text)
        }
    }
}

@Composable
private fun Headline(text: String) {
    Text(text, style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface)
}

private fun CauseKind.icon() = when (this) {
    CauseKind.NIGHT_ENTERED_WINDOW, CauseKind.NIGHT_LEFT_WINDOW -> DriftIcons.Moon
    CauseKind.NEED_REESTIMATED -> DriftIcons.Refresh
    CauseKind.SOURCE_ADDED -> DriftIcons.Settings
    CauseKind.USER_EDIT -> DriftIcons.Check
    CauseKind.CIRCADIAN_SHIFT -> DriftIcons.BodyClock
    CauseKind.CHECK_IN_CALIBRATION -> DriftIcons.Energy
    CauseKind.FIRST_RUN -> DriftIcons.Info
}

@Composable
private fun CauseRow(cause: Cause, actions: WhyActions) {
    val date = cause.date
    val open = date?.let { { actions.onOpenDate(it) } }
    val base = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    Row(
        (if (open != null) base.clickable(onClickLabel = stringResource(R.string.why_open_night), role = Role.Button, onClick = open) else base)
            .padding(vertical = DriftTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(cause.kind.icon(), contentDescription = null, tint = DriftTheme.colors.primary, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xxs)) {
            Text(cause.text, style = DriftTheme.typography.body, color = DriftTheme.colors.onSurface)
            val delta = cause.delta?.takeIf { cause.kind.deltaIsMinutes() }
                ?.let { DriftFormat.signedDuration(Duration.ofMinutes(Math.round(it))) }
            if (date != null || delta != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm)) {
                    if (date != null) Caption(date.shortLabel())
                    if (delta != null) Text(delta, style = DriftTheme.typography.label, color = DriftTheme.colors.primary)
                }
            }
        }
    }
}
