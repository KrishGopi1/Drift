package app.drift.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftTheme
import app.drift.model.Interval
import app.drift.model.engine.CircadianEstimate
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Compose arc angle in degrees for a clock minute: noon at the top, midnight at the bottom, clockwise. */
fun dialAngle(minuteOfDay: Float): Float = -90f + (minuteOfDay - 720f) * 0.25f

internal fun minuteOfDay(t: Instant, zone: ZoneId): Float =
    ZonedDateTime.ofInstant(t, zone).let { it.hour * 60 + it.minute + it.second / 60f }

/**
 * 24-hour dial of the body clock: wind-down arc, melatonin window arc, a tick at estimated DLMO, optional planned
 * sleep arc and a now dot. Noon is at the top and midnight at the bottom. The centre shows the melatonin window.
 * Draws in as a 480 ms fade (instant under reduced motion).
 */
@Composable
fun CircadianRhythmChart(
    circadian: CircadianEstimate,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    use24h: Boolean = true,
    now: Instant? = null,
    sleepWindow: Interval? = null,
    contentDescription: String = remember(circadian, zone, use24h, sleepWindow) { circadianDescription(circadian, zone, use24h, sleepWindow) },
) {
    val colors = DriftTheme.colors
    val type = DriftTheme.typography
    val measurer = rememberTextMeasurer()
    val progress by rememberDrawIn(circadian.melatoninWindow?.start, DriftTheme.motion.long)
    val window = circadian.melatoninWindow
    val centerTitle = if (window != null) "Melatonin window" else "No estimate yet"
    val centerValue = window?.let { DriftFormat.clock(it.start, zone, use24h) + " – " + DriftFormat.clock(it.end, zone, use24h) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f).semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                val ringW = 18.dp.toPx()
                val labelPad = 22.dp.toPx()
                val diameter = size.minDimension - ringW - labelPad * 2
                if (diameter <= 0f) return@Canvas
                val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                val arcSize = Size(diameter, diameter)
                val centre = Offset(size.width / 2, size.height / 2)
                drawArc(colors.outlineSubtle, 0f, 360f, false, topLeft, arcSize, style = Stroke(ringW))
                fun arc(from: Instant, to: Instant, color: Color, alpha: Float) {
                    val a = minuteOfDay(from, zone)
                    var sweep = (minuteOfDay(to, zone) - a) * 0.25f
                    if (sweep < 0) sweep += 360f
                    drawArc(color.copy(alpha = alpha * progress), dialAngle(a), sweep.coerceAtMost(360f), false, topLeft, arcSize, style = Stroke(ringW))
                }
                sleepWindow?.let { arc(it.start, it.end, colors.primary, 0.35f) }
                val ws = circadian.windDownStart
                if (ws != null && window != null && ws < window.start) arc(ws, window.start, colors.windDownFill, 0.9f)
                window?.let { arc(it.start, it.end, colors.melatoninFill, 1f) }
                // Hour ticks at 0/6/12/18 with labels outside the ring.
                listOf(0, 6, 12, 18).forEach { h ->
                    val ang = dialAngle(h * 60f) * (PI / 180.0).toFloat()
                    val r = diameter / 2 + ringW / 2 + 11.dp.toPx()
                    val text = if (use24h) h.toString().padStart(2, '0') else DriftFormat.clockMinutes(h * 60, false).replace(":00 ", "")
                    val m = measurer.measure(text, type.caption)
                    label(measurer, text, type.caption, colors.onSurfaceMuted, Offset(centre.x + cos(ang) * r - m.size.width / 2f, centre.y + sin(ang) * r - m.size.height / 2f))
                }
                // DLMO tick.
                circadian.dlmoMinutes?.let { d ->
                    val ang = dialAngle(((d.value.toFloat() % 1440f) + 1440f) % 1440f) * (PI / 180.0).toFloat()
                    val r0 = diameter / 2 - ringW / 2 - 4.dp.toPx()
                    val r1 = diameter / 2 + ringW / 2 + 4.dp.toPx()
                    drawLine(colors.onSurface, Offset(centre.x + cos(ang) * r0, centre.y + sin(ang) * r0), Offset(centre.x + cos(ang) * r1, centre.y + sin(ang) * r1), 3.dp.toPx())
                }
                // Now dot.
                if (now != null) {
                    val ang = dialAngle(minuteOfDay(now, zone)) * (PI / 180.0).toFloat()
                    val r = diameter / 2
                    val c = Offset(centre.x + cos(ang) * r, centre.y + sin(ang) * r)
                    drawCircle(colors.surface, ringW / 2 + 3.dp.toPx(), c)
                    drawCircle(colors.energy, ringW / 2 - 3.dp.toPx(), c)
                }
            }
            Column(Modifier.padding(horizontal = 56.dp).clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerTitle, style = type.caption, color = colors.onSurfaceMuted, textAlign = TextAlign.Center)
                if (centerValue != null) Text(centerValue, style = type.titleSmall, color = colors.melatonin, textAlign = TextAlign.Center)
            }
        }
        FlowRow(
            Modifier.clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
        ) {
            if (circadian.windDownStart != null) LegendItem("Wind-down", colors.windDownFill, LegendShape.Solid)
            if (window != null) LegendItem("Melatonin window", colors.melatoninFill, LegendShape.Solid)
            if (circadian.dlmoMinutes != null) LegendItem("Melatonin onset", colors.onSurface, LegendShape.Line)
            if (sleepWindow != null) LegendItem("Planned sleep", colors.primary.copy(alpha = 0.5f), LegendShape.Solid)
            if (now != null) LegendItem("Now", colors.energy, LegendShape.Solid)
        }
    }
}
