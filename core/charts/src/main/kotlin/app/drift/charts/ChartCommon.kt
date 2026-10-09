package app.drift.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import app.drift.designsystem.DriftTheme

/** Smallest step from [candidates] (minutes) at which labels of [labelWidthPx] fit with ~30% spacing. */
internal fun axisStepMinutes(
    spanMinutes: Float,
    labelWidthPx: Float,
    plotWidthPx: Float,
    candidates: IntArray = intArrayOf(60, 120, 180, 240, 360, 720, 1440),
): Int {
    if (spanMinutes <= 0f || plotWidthPx <= 0f) return candidates.last()
    return candidates.firstOrNull { step -> plotWidthPx * step / spanMinutes >= labelWidthPx * 1.3f } ?: candidates.last()
}

/** Draw-in progress 0..1: animates on first show and whenever [key] changes; snaps to 1 under reduced motion. */
@Composable
internal fun rememberDrawIn(key: Any?, durationMs: Int = DriftTheme.motion.drawIn): State<Float> {
    val reduced = DriftTheme.reducedMotion
    val motion = DriftTheme.motion
    val progress = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key, reduced) {
        if (reduced) progress.snapTo(1f) else progress.animateTo(1f, motion.spec(false, durationMs, motion.emphasized))
    }
    return progress.asState()
}

internal fun DrawScope.label(measurer: TextMeasurer, text: String, style: TextStyle, color: Color, at: Offset) {
    drawText(measurer, text, at, style.copy(color = color))
}
