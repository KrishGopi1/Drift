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
import androidx.compose.ui.unit.dp
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

/** Clamp that never throws: if the range is empty (hi < lo) the result is [lo]. */
internal fun Float.clampSafe(lo: Float, hi: Float): Float = coerceIn(lo, maxOf(lo, hi))

/**
 * Places labels left to right without overlap. [place] returns the x to draw at (centred on [centerX], clamped to
 * [minX]..[maxX]) or null when the label would touch the previous one.
 */
internal class LabelPlacer(private val gapPx: Float) {
    private var lastRight = -Float.MAX_VALUE
    fun place(centerX: Float, width: Float, minX: Float, maxX: Float): Float? {
        val x = (centerX - width / 2f).clampSafe(minX, maxX - width)
        if (x < lastRight + gapPx) return null
        lastRight = x + width
        return x
    }
}

/** Hour-aligned time ticks along [left]..[left]+[plotW] at y; skips labels that do not fit. */
internal fun DrawScope.drawTimeTicks(
    measurer: TextMeasurer,
    style: TextStyle,
    color: Color,
    start: java.time.Instant,
    end: java.time.Instant,
    zone: java.time.ZoneId,
    use24h: Boolean,
    left: Float,
    plotW: Float,
    y: Float,
) {
    if (plotW <= 0f) return
    val spanMin = java.time.Duration.between(start, end).toMinutes().toFloat()
    if (spanMin <= 0f) return
    val sample = measurer.measure(app.drift.designsystem.DriftFormat.clockMinutes(23 * 60 + 59, use24h), style).size.width.toFloat()
    val step = axisStepMinutes(spanMin, sample, plotW)
    val z = java.time.ZonedDateTime.ofInstant(start, zone)
    val startMod = z.hour * 60 + z.minute
    var tick = ((startMod + step - 1) / step) * step - startMod
    val placer = LabelPlacer(8.dp.toPx())
    while (tick <= spanMin) {
        val t = start.plusSeconds(tick * 60L)
        val text = app.drift.designsystem.DriftFormat.clock(t, zone, use24h)
        val m = measurer.measure(text, style)
        val cx = left + tick / spanMin * plotW
        placer.place(cx, m.size.width.toFloat(), left, left + plotW)?.let { label(measurer, text, style, color, Offset(it, y)) }
        tick += step
    }
}
