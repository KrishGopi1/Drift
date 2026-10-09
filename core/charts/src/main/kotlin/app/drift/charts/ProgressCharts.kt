package app.drift.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.drift.designsystem.DriftIcons
import app.drift.designsystem.DriftTheme
import kotlin.math.ceil

/** One night in [SleepTimesChart]: asleep [spans] as minutes after 18:00 the evening before, plus its labels. */
data class SleepColumn(
    val key: String,
    /** "THU". */
    val day: String,
    /** "8h 32m", or "--" when nothing was recorded. */
    val total: String,
    val spans: List<Pair<Int, Int>>,
    /** Target bedtime marker (moon), minutes after 18:00 the evening before. */
    val targetMinute: Int? = null,
    /** Spoken summary for this column. */
    val description: String,
)

/** Pure axis maths for the progress charts. */
object ProgressGeometry {
    /** Axis window in minutes after 18:00: an hour around all spans and targets, on even hours, at least 8 h tall. */
    fun timeWindow(columns: List<SleepColumn>): IntRange {
        val all = columns.flatMap { c -> c.spans.flatMap { listOf(it.first, it.second) } + listOfNotNull(c.targetMinute) }
        if (all.isEmpty()) return 240..960 // 22:00 to 10:00
        var lo = ((all.min() - 60) / 120) * 120
        var hi = ((all.max() + 60 + 119) / 120) * 120
        if (hi - lo < 480) hi = lo + 480
        lo = lo.coerceAtLeast(0)
        hi = hi.coerceAtMost(1440 + 360)
        return lo..hi
    }

    /** Debt axis top in hours: the next even hour above the largest value, at least 12 h. */
    fun debtAxisTop(hours: List<Double>): Int = maxOf(12, (ceil((hours.maxOrNull() ?: 0.0) / 2.0) * 2).toInt())
}

private fun axisClock(minuteAfter18: Int, use24h: Boolean): String {
    val m = ((minuteAfter18 + 18 * 60) % 1440 + 1440) % 1440
    val h = m / 60
    if (use24h) return "%02d".format(h)
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12" + if (h < 12) "a" else "p"
}

/**
 * Sleep times for recent nights, like a column chart turned into a timetable: one column per night, oldest left,
 * time running down with labels on the right, each sleep span as a block with a sleep mark at the top and a sun at
 * the bottom, and a moon on the target bedtime. Scrolls sideways and opens on the newest night. Tapping a column
 * calls [onColumnClick].
 */
@Composable
fun SleepTimesChart(
    columns: List<SleepColumn>,
    use24h: Boolean,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 240.dp,
    columnWidth: Dp = 60.dp,
    onColumnClick: (String) -> Unit = {},
) {
    val colors = DriftTheme.colors
    val type = DriftTheme.typography
    val measurer = rememberTextMeasurer()
    val window = ProgressGeometry.timeWindow(columns)
    val scroll = rememberScrollState()
    LaunchedEffect(columns.size) { scroll.scrollTo(scroll.maxValue) }
    val zz = rememberVectorPainter(DriftIcons.Zz)
    val sun = rememberVectorPainter(DriftIcons.Sun)
    val moon = rememberVectorPainter(DriftIcons.MoonSmall)
    Row(modifier.fillMaxWidth()) {
        Row(Modifier.weight(1f).horizontalScroll(scroll)) {
            columns.forEach { c ->
                Column(
                    Modifier
                        .width(columnWidth)
                        .clickable(role = Role.Button, onClickLabel = c.description) { onColumnClick(c.key) }
                        .semantics(mergeDescendants = true) { contentDescription = c.description }
                        .testTag("sleep_column_${c.key}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(Modifier.fillMaxWidth().height(plotHeight)) {
                        val span = (window.last - window.first).toFloat()
                        fun y(min: Int) = (min - window.first) / span * size.height
                        // Gridlines every two hours.
                        var g = window.first
                        while (g <= window.last) {
                            drawLine(colors.outlineSubtle, Offset(0f, y(g)), Offset(size.width, y(g)), 1.dp.toPx())
                            g += 120
                        }
                        val left = 6.dp.toPx()
                        val right = size.width - 6.dp.toPx()
                        c.spans.forEach { (a, b) ->
                            val top = y(a)
                            val bottom = y(b)
                            drawRoundRect(colors.primaryContainer.copy(alpha = 0.55f), Offset(left, top), Size(right - left, bottom - top), CornerRadius(5.dp.toPx()))
                            drawRoundRect(colors.primary.copy(alpha = 0.9f), Offset(left, top), Size(right - left, bottom - top), CornerRadius(5.dp.toPx()), style = Stroke(1.dp.toPx()))
                            val ic = 12.dp.toPx()
                            val cx = (left + right) / 2 - ic / 2
                            if (bottom - top > ic * 3) {
                                translate(cx, top + 5.dp.toPx()) { with(zz) { draw(Size(ic, ic), colorFilter = ColorFilter.tint(colors.primary)) } }
                                translate(cx, bottom - ic - 5.dp.toPx()) { with(sun) { draw(Size(ic, ic), colorFilter = ColorFilter.tint(colors.primary)) } }
                            }
                        }
                        c.targetMinute?.let { t ->
                            val ty = y(t)
                            val ic = 14.dp.toPx()
                            drawLine(colors.onSurface, Offset(left, ty - ic / 2 - 2.dp.toPx()), Offset(right, ty - ic / 2 - 2.dp.toPx()), 1.5.dp.toPx())
                            drawLine(colors.onSurface, Offset(left, ty + ic / 2 + 2.dp.toPx()), Offset(right, ty + ic / 2 + 2.dp.toPx()), 1.5.dp.toPx())
                            translate((left + right) / 2 - ic / 2, ty - ic / 2) { with(moon) { draw(Size(ic, ic), colorFilter = ColorFilter.tint(colors.onSurface)) } }
                        }
                    }
                    Text(c.total, style = type.caption, color = colors.onSurfaceMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    Text(c.day, style = type.label.copy(fontWeight = FontWeight.Bold), color = colors.onSurface)
                }
            }
        }
        // Fixed time axis on the right.
        Canvas(Modifier.width(36.dp).height(plotHeight).clearAndSetSemantics {}) {
            val span = (window.last - window.first).toFloat()
            var g = window.first
            while (g <= window.last) {
                val text = axisClock(g, use24h)
                val m = measurer.measure(text, type.caption)
                label(measurer, text, type.caption, colors.onSurfaceMuted, Offset(6.dp.toPx(), ((g - window.first) / span * size.height - m.size.height / 2f).clampSafe(0f, size.height - m.size.height)))
                g += 120
            }
        }
    }
}

/** One point of [DebtTrendChart]. */
data class DebtPoint(val key: String, val day: String, val hours: Double, val value: String, val description: String)

/**
 * Sleep debt over recent nights: a line with dots, the hours axis on the right, the value and weekday under each
 * point. Scrolls sideways and opens on the newest night.
 */
@Composable
fun DebtTrendChart(
    points: List<DebtPoint>,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 200.dp,
    columnWidth: Dp = 56.dp,
) {
    val colors = DriftTheme.colors
    val type = DriftTheme.typography
    val measurer = rememberTextMeasurer()
    val topH = ProgressGeometry.debtAxisTop(points.map { it.hours })
    val scroll = rememberScrollState()
    LaunchedEffect(points.size) { scroll.scrollTo(scroll.maxValue) }
    Row(modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).horizontalScroll(scroll)) {
            Column {
                Canvas(Modifier.width(columnWidth * points.size).height(plotHeight).testTag("debt_trend")) {
                    fun y(h: Double) = (1f - (h / topH).toFloat().coerceIn(0f, 1f)) * (size.height - 8.dp.toPx()) + 4.dp.toPx()
                    var g = 0
                    while (g <= topH) {
                        drawLine(colors.outlineSubtle, Offset(0f, y(g.toDouble())), Offset(size.width, y(g.toDouble())), 1.dp.toPx())
                        g += 2
                    }
                    val cw = columnWidth.toPx()
                    val pts = points.mapIndexed { i, p -> Offset(cw * i + cw / 2, y(p.hours)) }
                    if (pts.size >= 2) {
                        val line = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                        drawPath(line, colors.primaryContainer, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    pts.forEach {
                        drawCircle(colors.primaryContainer, 5.dp.toPx(), it)
                        drawCircle(colors.onSurface, 2.dp.toPx(), it)
                    }
                }
                Row {
                    points.forEach { p ->
                        Column(
                            Modifier.width(columnWidth).semantics(mergeDescendants = true) { contentDescription = p.description },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(p.value, style = type.caption, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = 6.dp))
                            Text(p.day, style = type.label.copy(fontWeight = FontWeight.Bold), color = colors.onSurface)
                        }
                    }
                }
            }
        }
        Canvas(Modifier.width(36.dp).height(plotHeight).clearAndSetSemantics {}) {
            fun y(h: Int) = (1f - h.toFloat() / topH) * (size.height - 8.dp.toPx()) + 4.dp.toPx()
            var g = 0
            while (g <= topH) {
                val text = "${g}h"
                val m = measurer.measure(text, type.caption)
                label(measurer, text, type.caption, colors.onSurfaceMuted, Offset(6.dp.toPx(), (y(g) - m.size.height / 2f).clampSafe(0f, size.height - m.size.height)))
                g += 2
            }
        }
    }
}

/** One night in [SleepQualityChart]: [rating] 1..5 or null when not rated. */
data class QualityColumn(val key: String, val day: String, val rating: Int?, val description: String)

/** Self-rated sleep quality: five stacked dots per night, filled up to the rating, newest on the right. */
@Composable
fun SleepQualityChart(
    columns: List<QualityColumn>,
    modifier: Modifier = Modifier,
    columnWidth: Dp = 48.dp,
) {
    val colors = DriftTheme.colors
    val type = DriftTheme.typography
    val scroll = rememberScrollState()
    LaunchedEffect(columns.size) { scroll.scrollTo(scroll.maxValue) }
    Row(modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.Start) {
        columns.forEach { c ->
            Column(
                Modifier.width(columnWidth).semantics(mergeDescendants = true) { contentDescription = c.description },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.fillMaxWidth().height(132.dp)) {
                    val r = 7.dp.toPx()
                    val gap = (size.height - 10 * r) / 4
                    for (k in 1..5) {
                        val cy = size.height - r - (k - 1) * (2 * r + gap)
                        val on = c.rating != null && k <= c.rating
                        if (on) drawCircle(colors.primaryContainer, r, Offset(size.width / 2, cy))
                        else drawCircle(colors.outlineSubtle, r, Offset(size.width / 2, cy), style = Stroke(1.5.dp.toPx()))
                    }
                }
                Text(c.day, style = type.label.copy(fontWeight = FontWeight.Bold), color = colors.onSurface, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
