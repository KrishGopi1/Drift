package app.drift.charts

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.drift.designsystem.DriftColors
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftTheme
import app.drift.model.engine.DayCurve
import app.drift.model.engine.EnergyPhase
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

internal fun phaseBandColor(phase: EnergyPhase, c: DriftColors): Color = when (phase) {
    EnergyPhase.GROGGINESS -> c.onSurfaceMuted
    EnergyPhase.MORNING_PEAK, EnergyPhase.EVENING_PEAK -> c.energyFill
    EnergyPhase.AFTERNOON_DIP -> c.primary
    EnergyPhase.WIND_DOWN -> c.windDownFill
    EnergyPhase.MELATONIN_WINDOW -> c.melatoninFill
    EnergyPhase.SLEEP -> c.outline
}

/**
 * Today's energy curve: gradient fill under a line, tinted phase bands with labels, peak/dip markers, hourly time
 * axis and a live "now" marker that pulses (1.8 s). The line draws in over 1.1 s when the day changes.
 * Reduced motion: drawn complete, marker static. [contentDescription] summarises peaks and dips with times.
 */
@Composable
fun EnergyCurveChart(
    dayCurve: DayCurve,
    now: Instant?,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    use24h: Boolean = true,
    height: Dp = 220.dp,
    showLegend: Boolean = true,
    contentDescription: String = remember(dayCurve, zone, use24h, now) { energyCurveDescription(dayCurve, zone, use24h, now) },
) {
    val colors = DriftTheme.colors
    val type = DriftTheme.typography
    val measurer = rememberTextMeasurer()
    val chartLine = DriftTheme.strokes.chartLine
    val progress by rememberDrawIn(dayCurve.date)
    val pulse by rememberPulse()
    val range = remember(dayCurve) { EnergyGeometry.range(dayCurve) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm)) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription },
    ) {
        range ?: return@Canvas
        val axisH = measurer.measure("00:00", type.caption).size.height + 8.dp.toPx()
        val topPad = 6.dp.toPx()
        val plotH = size.height - axisH - topPad
        val plotW = size.width
        fun x(t: Instant) = EnergyGeometry.fraction(range, t) * plotW
        fun y(level: Double) = topPad + (1f - (level / 100.0).coerceIn(0.0, 1.0).toFloat()) * plotH

        // Phase bands with names when they fit.
        dayCurve.segments.forEach { seg ->
            val a = maxOf(seg.interval.start, range.start)
            val b = minOf(seg.interval.end, range.endInclusive)
            if (!b.isAfter(a)) return@forEach
            val x0 = x(a)
            val x1 = x(b)
            drawRect(phaseBandColor(seg.phase, colors).copy(alpha = if (colors.isDark) 0.16f else 0.20f), Offset(x0, topPad), androidx.compose.ui.geometry.Size(x1 - x0, plotH))
            val name = seg.phase.displayName()
            val m = measurer.measure(name, type.caption)
            if (m.size.width + 8.dp.toPx() < x1 - x0) {
                label(measurer, name, type.caption, colors.onSurfaceMuted, Offset(x0 + (x1 - x0 - m.size.width) / 2, topPad + 4.dp.toPx()))
            }
        }
        // Gridlines at 0/50/100.
        listOf(0.0, 50.0, 100.0).forEach { lv ->
            drawLine(colors.outlineSubtle, Offset(0f, y(lv)), Offset(plotW, y(lv)), strokeWidth = 1.dp.toPx())
        }
        // Curve.
        val pts = EnergyGeometry.normalizedPoints(dayCurve, plotW, plotH).map { Offset(it.first, it.second + topPad) }
        if (pts.size >= 2) {
            val line = Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y) }
            val fill = Path().apply {
                addPath(line); lineTo(pts.last().x, topPad + plotH); lineTo(pts.first().x, topPad + plotH); close()
            }
            clipRect(left = 0f, top = 0f, right = plotW * progress, bottom = size.height) {
                drawPath(fill, Brush.verticalGradient(listOf(colors.energyFill.copy(alpha = 0.38f), colors.energyFill.copy(alpha = 0f)), topPad, topPad + plotH))
                drawPath(line, colors.energy, style = Stroke(chartLine.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        // Peak and dip markers.
        if (progress >= 0.999f) {
            dayCurve.segments.forEach { seg ->
                val at = seg.extremumAt ?: return@forEach
                if (seg.phase !in setOf(EnergyPhase.MORNING_PEAK, EnergyPhase.EVENING_PEAK, EnergyPhase.AFTERNOON_DIP)) return@forEach
                val lv = EnergyGeometry.levelAt(dayCurve, at) ?: return@forEach
                drawCircle(colors.surface, 5.dp.toPx(), Offset(x(at), y(lv)))
                drawCircle(colors.energy, 5.dp.toPx(), Offset(x(at), y(lv)), style = Stroke(2.dp.toPx()))
            }
        }
        // Time axis.
        val zdt = ZonedDateTime.ofInstant(range.start, zone)
        val sample = measurer.measure(DriftFormat.clockMinutes(23 * 60 + 59, use24h), type.caption).size.width.toFloat()
        val spanMin = java.time.Duration.between(range.start, range.endInclusive).toMinutes().toFloat()
        val step = axisStepMinutes(spanMin, sample, plotW)
        val startMinuteOfDay = zdt.hour * 60 + zdt.minute
        var tick = ((startMinuteOfDay + step - 1) / step) * step - startMinuteOfDay
        while (tick <= spanMin) {
            val t = range.start.plusSeconds(tick * 60L)
            val text = DriftFormat.clock(t, zone, use24h)
            val m = measurer.measure(text, type.caption)
            val tx = (x(t) - m.size.width / 2f).coerceIn(0f, plotW - m.size.width)
            label(measurer, text, type.caption, colors.onSurfaceMuted, Offset(tx, topPad + plotH + 6.dp.toPx()))
            tick += step
        }
        // Now marker.
        if (now != null && now >= range.start && now <= range.endInclusive) {
            val nx = x(now)
            drawLine(colors.onSurface.copy(alpha = 0.55f), Offset(nx, topPad), Offset(nx, topPad + plotH), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            val lv = EnergyGeometry.levelAt(dayCurve, now)
            if (lv != null) {
                val c = Offset(nx, y(lv))
                drawCircle(colors.energy.copy(alpha = 0.45f * (1f - pulse)), (7f + 9f * pulse).dp.toPx(), c)
                drawCircle(colors.onSurface, 7.dp.toPx(), c)
                drawCircle(colors.energy, 4.5.dp.toPx(), c)
            }
        }
    }
    if (showLegend) {
        val phases = dayCurve.segments.map { it.phase }.distinct()
        FlowRow(
            Modifier.clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
        ) {
            phases.forEach { LegendItem(it.displayName(), phaseBandColor(it, colors).copy(alpha = 0.6f), LegendShape.Solid) }
        }
    }
    }
}

/** 0..1 pulse phase; constant 0.35 under reduced motion. */
@Composable
internal fun rememberPulse(): State<Float> {
    if (DriftTheme.reducedMotion) return remember { mutableFloatStateOf(0.35f) }
    val t = rememberInfiniteTransition(label = "pulse")
    return t.animateFloat(0f, 1f, infiniteRepeatable(tween(DriftTheme.motion.pulse, easing = DriftTheme.motion.decelerate), RepeatMode.Restart), label = "pulse")
}
