package app.drift.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Splits a daily series into runs with no missing day between points (a gap of 2+ days breaks the line). */
fun regularitySegments(trend: List<Pair<LocalDate, Double>>): List<List<Pair<LocalDate, Double>>> {
    val out = mutableListOf<MutableList<Pair<LocalDate, Double>>>()
    trend.sortedBy { it.first }.forEach { p ->
        val last = out.lastOrNull()?.lastOrNull()
        if (last == null || ChronoUnit.DAYS.between(last.first, p.first) > 1) out += mutableListOf(p) else out.last() += p
    }
    return out
}

/**
 * Sleep regularity index (0..100) over time as a line with gaps where days are missing, 0/50/100 gridlines,
 * date labels at start, middle and end, and the latest value called out. Draws in over 1.1 s.
 */
@Composable
fun RegularityTrendChart(
    trend: List<Pair<LocalDate, Double>>,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    contentDescription: String = remember(trend) { regularityTrendDescription(trend) },
) {
    val colors = DriftTheme.colors
    val type = DriftTheme.typography
    val measurer = rememberTextMeasurer()
    val chartLine = DriftTheme.strokes.chartLine
    val progress by rememberDrawIn(trend.size)
    val sorted = remember(trend) { trend.sortedBy { it.first } }
    Canvas(modifier.fillMaxWidth().height(height).semantics { this.contentDescription = contentDescription }) {
        if (sorted.isEmpty()) return@Canvas
        val gutter = measurer.measure("100", type.caption).size.width + 8.dp.toPx()
        val axisH = measurer.measure("0", type.caption).size.height + 8.dp.toPx()
        val top = 8.dp.toPx()
        val plotW = size.width - gutter - 8.dp.toPx()
        val plotH = size.height - axisH - top
        val first = sorted.first().first
        val spanDays = maxOf(ChronoUnit.DAYS.between(first, sorted.last().first).toFloat(), 1f)
        fun x(d: LocalDate) = gutter + ChronoUnit.DAYS.between(first, d) / spanDays * plotW
        fun y(v: Double) = top + (1f - (v / 100.0).coerceIn(0.0, 1.0).toFloat()) * plotH
        listOf(0, 50, 100).forEach { v ->
            drawLine(colors.outlineSubtle, Offset(gutter, y(v.toDouble())), Offset(gutter + plotW, y(v.toDouble())), 1.dp.toPx())
            val tm = measurer.measure(v.toString(), type.caption)
            label(measurer, v.toString(), type.caption, colors.onSurfaceMuted, Offset(0f, (y(v.toDouble()) - tm.size.height / 2f).coerceIn(0f, size.height - axisH - tm.size.height)))
        }
        clipRect(right = gutter + plotW * progress + 8.dp.toPx()) {
            regularitySegments(sorted).forEach { seg ->
                if (seg.size == 1) {
                    drawCircle(colors.primary, 3.dp.toPx(), Offset(x(seg[0].first), y(seg[0].second)))
                } else {
                    val path = Path().apply {
                        moveTo(x(seg[0].first), y(seg[0].second))
                        for (i in 1 until seg.size) lineTo(x(seg[i].first), y(seg[i].second))
                    }
                    drawPath(path, colors.primary, style = Stroke(chartLine.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        // Latest value callout.
        val lastPoint = sorted.last()
        val lc = Offset(x(lastPoint.first), y(lastPoint.second))
        drawCircle(colors.onSurface, 6.dp.toPx(), lc)
        drawCircle(colors.primary, 4.dp.toPx(), lc)
        val text = lastPoint.second.roundToInt().toString()
        val tm = measurer.measure(text, type.caption)
        val ty = if (lc.y - tm.size.height - 10.dp.toPx() < 0f) lc.y + 10.dp.toPx() else lc.y - tm.size.height - 8.dp.toPx()
        label(measurer, text, type.caption, colors.onSurface, Offset((lc.x - tm.size.width / 2f).coerceIn(gutter, size.width - tm.size.width), ty))
        // Date labels.
        val labelDates = listOf(sorted.first().first, sorted[sorted.size / 2].first, sorted.last().first).distinct()
        labelDates.forEachIndexed { i, d ->
            val s = dayMonth(d)
            val m = measurer.measure(s, type.caption)
            val lx = when (i) { 0 -> gutter; labelDates.lastIndex -> size.width - m.size.width.toFloat(); else -> x(d) - m.size.width / 2f }
            label(measurer, s, type.caption, colors.onSurfaceMuted, Offset(lx, top + plotH + 6.dp.toPx()))
        }
    }
}
