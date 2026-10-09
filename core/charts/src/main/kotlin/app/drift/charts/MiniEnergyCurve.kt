package app.drift.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.drift.designsystem.DriftTheme
import app.drift.model.engine.DayCurve
import java.time.Instant
import java.time.ZoneId

/** Compact energy curve: line, soft fill and a now dot. No axes or labels; no animation beyond a 1.1 s draw-in. */
@Composable
fun MiniEnergyCurve(
    dayCurve: DayCurve,
    now: Instant?,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    use24h: Boolean = true,
    height: Dp = 56.dp,
    contentDescription: String = remember(dayCurve, zone, use24h, now) { miniEnergyDescription(dayCurve, zone, use24h, now) },
) {
    val colors = DriftTheme.colors
    val progress by rememberDrawIn(dayCurve.date)
    val range = remember(dayCurve) { EnergyGeometry.range(dayCurve) }
    Canvas(modifier.fillMaxWidth().height(height).semantics { this.contentDescription = contentDescription }) {
        range ?: return@Canvas
        val pad = 6.dp.toPx()
        val w = size.width
        val h = size.height - pad * 2
        val pts = EnergyGeometry.normalizedPoints(dayCurve, w, h).map { Offset(it.first, it.second + pad) }
        if (pts.size < 2) return@Canvas
        val line = Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y) }
        val fill = Path().apply { addPath(line); lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close() }
        clipRect(right = w * progress) {
            drawPath(fill, Brush.verticalGradient(listOf(colors.energyFill.copy(alpha = 0.30f), colors.energyFill.copy(alpha = 0f))))
            drawPath(line, colors.energy, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (now != null && now >= range.start && now <= range.endInclusive) {
            val lv = EnergyGeometry.levelAt(dayCurve, now) ?: return@Canvas
            val c = Offset(EnergyGeometry.fraction(range, now) * w, pad + (1f - (lv / 100.0).toFloat()) * h)
            drawCircle(colors.onSurface, 5.dp.toPx(), c)
            drawCircle(colors.energy, 3.dp.toPx(), c)
        }
    }
}
