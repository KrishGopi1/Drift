package app.drift.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.drift.designsystem.DriftTheme
import app.drift.model.engine.DayCurve
import app.drift.model.engine.EnergyPhase
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Today's energy at a glance, in the same language as the Energy tab: the waking day left to right as phase blocks,
 * one smooth line coloured by level (violet low to teal high) and a vertical "now" marker. Phase names show inside
 * blocks wide enough for them. Draws in over 1.1 s (instantly under reduced motion).
 */
@Composable
fun MiniEnergyCurve(
    dayCurve: DayCurve,
    now: Instant?,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    use24h: Boolean = true,
    height: Dp = 56.dp,
    phaseLabel: (EnergyPhase) -> String = { it.displayName() },
    contentDescription: String = remember(dayCurve, zone, use24h, now) { miniEnergyDescription(dayCurve, zone, use24h, now) },
) {
    val colors = DriftTheme.colors
    val type = DriftTheme.typography
    val measurer = rememberTextMeasurer()
    val progress by rememberDrawIn(dayCurve.date)
    val range = remember(dayCurve) { TimelineGeometry.wakingRange(dayCurve) }
    Canvas(modifier.fillMaxWidth().height(height).semantics { this.contentDescription = contentDescription }) {
        range ?: return@Canvas
        val total = Duration.between(range.start, range.endInclusive).toMillis().toFloat()
        if (total <= 0f || size.width <= 0f) return@Canvas
        fun x(t: Instant) = Duration.between(range.start, t).toMillis() / total * size.width
        val curveTop = size.height * 0.30f
        val curveBottom = size.height - 6.dp.toPx()
        fun y(level: Double) = curveBottom - (level / 100.0).coerceIn(0.0, 1.0).toFloat() * (curveBottom - curveTop)

        // Phase blocks with their names when they fit.
        TimelineGeometry.phasePanels(listOf(dayCurve), range, emptyList()).forEach { p ->
            val l = x(p.start) + 1.5.dp.toPx()
            val r = x(p.end) - 1.5.dp.toPx()
            if (r - l < 4.dp.toPx()) return@forEach
            val current = now != null && now >= p.start && now < p.end
            drawRoundRect(if (current) colors.surfaceRaised else colors.background, Offset(l, 0f), Size(r - l, size.height), CornerRadius(10.dp.toPx()))
            val name = phaseLabel(p.phase)
            val m = measurer.measure(name, type.caption)
            if (m.size.width + 12.dp.toPx() < r - l) label(measurer, name, type.caption, if (current) colors.onSurface else colors.onSurfaceMuted, Offset(l + 6.dp.toPx(), 6.dp.toPx()))
        }

        // The line.
        val pts0 = dayCurve.points.filter { it.at >= range.start && it.at <= range.endInclusive }
        if (pts0.size >= 2) {
            val levels = TimelineGeometry.smooth(pts0.map { it.level })
            val pts = pts0.indices.map { Offset(x(pts0[it].at), y(levels[it])) }
            val line = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val mid = Offset((pts[i - 1].x + pts[i].x) / 2, (pts[i - 1].y + pts[i].y) / 2)
                    quadraticTo(pts[i - 1].x, pts[i - 1].y, mid.x, mid.y)
                }
                lineTo(pts.last().x, pts.last().y)
            }
            val brush = Brush.verticalGradient(listOf(colors.curveHigh, colors.curveMid, colors.curveLow), startY = curveTop, endY = curveBottom)
            clipRect(right = size.width * progress) {
                drawPath(line, brush, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        // Now.
        if (now != null && now >= range.start && now <= range.endInclusive) {
            val nx = x(now)
            drawLine(colors.onSurface.copy(alpha = 0.8f), Offset(nx, 0f), Offset(nx, size.height), 1.dp.toPx())
            EnergyGeometry.levelAt(dayCurve, now)?.let { drawCircle(colors.onSurface, 4.dp.toPx(), Offset(nx, y(it))) }
        }
    }
}
