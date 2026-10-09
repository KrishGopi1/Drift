package app.drift.feature.energy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.CardTone
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.SheetBody
import app.drift.feature.energy.ADJUST_STEP_MINUTES
import app.drift.feature.energy.R
import app.drift.feature.energy.adjustWindow
import app.drift.feature.energy.clampEnd
import app.drift.feature.energy.clampStart
import app.drift.model.Interval
import app.drift.model.SleepEpisode
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** Morning "Looks like you slept ..." card with Confirm, Adjust and Not right. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConfirmSleepCard(
    episode: SleepEpisode,
    fmt: TimeFormat,
    onConfirm: () -> Unit,
    onAdjust: () -> Unit,
    onNotRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DriftCard(modifier = modifier.testTag("energy_confirm_card"), tone = CardTone.Accent) {
        Text(stringResource(R.string.energy_confirm_title), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onPrimaryContainer)
        Text(
            stringResource(R.string.energy_confirm_body, fmt.clock(episode.start), fmt.clock(episode.end)),
            style = DriftTheme.typography.headline,
            color = DriftTheme.colors.onPrimaryContainer,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
        ) {
            DriftButton(stringResource(R.string.energy_confirm_action), onConfirm)
            DriftButton(stringResource(R.string.energy_adjust_action), onAdjust, style = ButtonStyle.Secondary)
            DriftButton(stringResource(R.string.energy_not_right_action), onNotRight, style = ButtonStyle.Text)
        }
    }
}

/** Sheet content: two draggable handles on a time track plus 5-minute step buttons as the precise, accessible path. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdjustSleepContent(
    episode: SleepEpisode,
    fmt: TimeFormat,
    onSave: (start: Instant, end: Instant) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = remember(episode) { adjustWindow(episode) }
    var start by remember(episode.id) { mutableStateOf(episode.start) }
    var end by remember(episode.id) { mutableStateOf(episode.end) }
    val step = Duration.ofMinutes(ADJUST_STEP_MINUTES)
    Column(modifier.testTag("energy_adjust_content"), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xl)) {
            TimeReadout(stringResource(R.string.energy_adjust_start), fmt.clock(start), "energy_adjust_start_time")
            TimeReadout(stringResource(R.string.energy_adjust_end), fmt.clock(end), "energy_adjust_end_time")
        }
        Text(
            stringResource(R.string.energy_adjust_total, DriftFormat.duration(Duration.between(start, end))),
            style = DriftTheme.typography.bodySmall,
            color = DriftTheme.colors.onSurfaceMuted,
        )
        RangeTrack(
            window = window,
            start = start,
            end = end,
            startLabel = stringResource(R.string.energy_adjust_handle, stringResource(R.string.energy_adjust_start), fmt.clock(start)),
            endLabel = stringResource(R.string.energy_adjust_handle, stringResource(R.string.energy_adjust_end), fmt.clock(end)),
            onStart = { start = clampStart(it, end, window) },
            onEnd = { end = clampEnd(it, start, window) },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
        ) {
            DriftButton(stringResource(R.string.energy_adjust_start_earlier), { start = clampStart(start.minus(step), end, window) }, style = ButtonStyle.Secondary)
            DriftButton(stringResource(R.string.energy_adjust_start_later), { start = clampStart(start.plus(step), end, window) }, style = ButtonStyle.Secondary)
            DriftButton(stringResource(R.string.energy_adjust_end_earlier), { end = clampEnd(end.minus(step), start, window) }, style = ButtonStyle.Secondary)
            DriftButton(stringResource(R.string.energy_adjust_end_later), { end = clampEnd(end.plus(step), start, window) }, style = ButtonStyle.Secondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm)) {
            DriftButton(stringResource(R.string.energy_adjust_save), { onSave(start, end) })
            DriftButton(stringResource(R.string.energy_adjust_cancel), onCancel, style = ButtonStyle.Text)
        }
    }
}

/** Full sheet (with header) used by the screenshot tests; the live screen wraps [AdjustSleepContent] in a bottom sheet. */
@Composable
internal fun AdjustSleepSheetBody(
    episode: SleepEpisode,
    fmt: TimeFormat,
    onSave: (Instant, Instant) -> Unit,
    onCancel: () -> Unit,
) {
    SheetBody(title = stringResource(R.string.energy_adjust_title), onClose = onCancel) {
        AdjustSleepContent(episode, fmt, onSave, onCancel)
    }
}

@Composable
private fun TimeReadout(label: String, time: String, tag: String) {
    Column(Modifier, Arrangement.spacedBy(DriftTheme.spacing.xxs)) {
        Text(label, style = DriftTheme.typography.label, color = DriftTheme.colors.onSurfaceMuted)
        Text(time, style = DriftTheme.typography.headline, color = DriftTheme.colors.onSurface, modifier = Modifier.testTag(tag))
    }
}

private val HandleTouch = 48.dp

/** Horizontal track covering [window]; the selected span is filled and each end has a drag handle. */
@Composable
private fun RangeTrack(
    window: Interval,
    start: Instant,
    end: Instant,
    startLabel: String,
    endLabel: String,
    onStart: (Instant) -> Unit,
    onEnd: (Instant) -> Unit,
) {
    val colors = DriftTheme.colors
    val totalSeconds = window.duration.seconds.toFloat().coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth().height(HandleTouch)) {
        val density = LocalDensity.current
        val travelPx = with(density) { (maxWidth - HandleTouch).toPx() }.coerceAtLeast(1f)
        val secondsPerPx = totalSeconds / travelPx
        fun xOf(t: Instant): Int = ((Duration.between(window.start, t).seconds / totalSeconds) * travelPx).roundToInt()
        val centreOffset = HandleTouch / 2
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = centreOffset)
                .fillMaxWidth()
                .height(4.dp)
                .background(colors.outline, RoundedCornerShape(2.dp)),
        )
        val startPx = xOf(start)
        val endPx = xOf(end)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(startPx + with(density) { centreOffset.roundToPx() }, 0) }
                .size(width = with(density) { (endPx - startPx).coerceAtLeast(0).toDp() }, height = 4.dp)
                .background(colors.primary, RoundedCornerShape(2.dp)),
        )
        Handle(startPx, startLabel, "energy_handle_start", start.epochSecond, secondsPerPx) { onStart(it) }
        Handle(endPx, endLabel, "energy_handle_end", end.epochSecond, secondsPerPx) { onEnd(it) }
    }
}

@Composable
private fun Handle(
    offsetPx: Int,
    label: String,
    tag: String,
    valueEpochSeconds: Long,
    secondsPerPx: Float,
    onValue: (Instant) -> Unit,
) {
    val currentValue by rememberUpdatedState(valueEpochSeconds)
    val currentOnValue by rememberUpdatedState(onValue)
    val currentSecondsPerPx by rememberUpdatedState(secondsPerPx)
    var raw by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .offset { IntOffset(offsetPx, 0) }
            .size(HandleTouch)
            .testTag(tag)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { raw = currentValue.toFloat() },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        raw += dx * currentSecondsPerPx
                        currentOnValue(Instant.ofEpochSecond(raw.toLong()))
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(28.dp).background(DriftTheme.colors.primary, CircleShape))
        Box(Modifier.size(10.dp).background(DriftTheme.colors.onPrimary, CircleShape))
    }
}
