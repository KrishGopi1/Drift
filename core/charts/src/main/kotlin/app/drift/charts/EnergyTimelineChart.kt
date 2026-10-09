package app.drift.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.drift.designsystem.DriftColors
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftTheme
import app.drift.model.engine.DayCurve
import app.drift.model.engine.EnergyPhase
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A labelled point on the timeline, such as a habit window: dot on the line at [at], [time] over [title]. */
data class TimelineMarker(val at: Instant, val time: String, val title: String)

/** Vertical layout of the waking day: time runs down, energy runs right. Pure so it can be tested. */
object TimelineGeometry {
    /** Level 0 and 100 as fractions of the plot width. */
    const val LOW_X = 0.30f
    const val HIGH_X = 0.85f

    /**
     * The waking part of the curve: from the end of the first Sleep segment to the start of the next one.
     * Falls back to the whole curve when there is no sleep segment on either side.
     */
    fun wakingRange(curve: DayCurve): ClosedRange<Instant>? {
        val full = EnergyGeometry.range(curve) ?: return null
        val sleeps = curve.segments.filter { it.phase == EnergyPhase.SLEEP }
        val start = sleeps.firstOrNull { it.interval.start <= full.start }?.interval?.end
            ?.takeIf { it > full.start && it < full.endInclusive } ?: full.start
        val end = sleeps.firstOrNull { it.interval.start > start }?.interval?.start
            ?.takeIf { it <= full.endInclusive } ?: full.endInclusive
        return if (end > start) start..end else full
    }

    /** x of [level] (0..100) in a plot from [left] spanning [width]. */
    fun x(level: Double, left: Float, width: Float): Float =
        left + (LOW_X + (HIGH_X - LOW_X) * (level / 100.0).coerceIn(0.0, 1.0).toFloat()) * width

    /** y of [t] given [pxPerHour] and the range start, offset by [top]. */
    fun y(t: Instant, range: ClosedRange<Instant>, pxPerHour: Float, top: Float): Float =
        top + Duration.between(range.start, t).toMillis() / 3_600_000f * pxPerHour

    /** Scroll offset that puts [now] a third of the way down a viewport of [viewportPx]; 0 when [now] is null or outside. */
    fun initialScroll(now: Instant?, range: ClosedRange<Instant>, pxPerHour: Float, top: Float, viewportPx: Float, contentPx: Float): Int {
        if (now == null || now < range.start || now > range.endInclusive) return 0
        val target = y(now, range, pxPerHour, top) - viewportPx / 3f
        return target.coerceIn(0f, maxOf(0f, contentPx - viewportPx)).toInt()
    }
}

private fun lineColor(phase: EnergyPhase, c: DriftColors): Color = when (phase) {
    EnergyPhase.MORNING_PEAK, EnergyPhase.EVENING_PEAK -> c.energy
    EnergyPhase.AFTERNOON_DIP -> c.primary
    EnergyPhase.WIND_DOWN -> c.windDown
    EnergyPhase.MELATONIN_WINDOW -> c.melatonin
    EnergyPhase.GROGGINESS, EnergyPhase.SLEEP -> c.onSurfaceMuted
}

/**
 * The day as a vertical timeline in a rounded panel (docs/ux/visual-refresh.md): hour labels down the left edge, the
 * energy line coloured by phase between two faint guide lines, "Peak"/"Dip" flags, [markers] for habit times and a
 * full-width "now" line. The panel is a [viewport]-tall window that scrolls inside the page and opens on [now].
 * Reduced motion: no draw-in. TalkBack reads [contentDescription] as one node.
 */
@Composable
fun EnergyTimelineChart(
    dayCurve: DayCurve,
    now: Instant?,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    use24h: Boolean = true,
    markers: List<TimelineMarker> = emptyList(),
    peakLabel: String = "Peak",
    dipLabel: String = "Dip",
    viewport: Dp = 460.dp,
    hourHeight: Dp = 64.dp,
    contentDescription: String = remember(dayCurve, zone, use24h, now) { energyCurveDescription(dayCurve, zone, use24h, now) },
) {
    val colors = DriftTheme.colors
    val type = DriftTheme.typography
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val range = remember(dayCurve) { TimelineGeometry.wakingRange(dayCurve) }
    val progress by rememberDrawIn(dayCurve.date)
    val padV = 24.dp
    val hours = range?.let { Duration.between(it.start, it.endInclusive).toMinutes() / 60f } ?: 0f
    val content = hourHeight * hours + padV * 2
    val window = if (content < viewport) content else viewport
    val scroll = remember(dayCurve.date, range) {
        val start = range?.let {
            with(density) {
                TimelineGeometry.initialScroll(now, it, hourHeight.toPx(), padV.toPx(), window.toPx(), content.toPx())
            }
        } ?: 0
        ScrollState(start)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(window)
            .clip(RoundedCornerShape(DriftTheme.radii.lg))
            .background(colors.surface)
            .clearAndSetSemantics { this.contentDescription = contentDescription }
            .verticalScroll(scroll),
    ) {
        Canvas(Modifier.fillMaxWidth().height(content)) {
            range ?: return@Canvas
            val top = padV.toPx()
            val pxPerHour = hourHeight.toPx()
            val labelW = 52.dp.toPx()
            val plotLeft = labelW
            val plotW = size.width - plotLeft - 12.dp.toPx()
            if (plotW <= 0f) return@Canvas
            fun y(t: Instant) = TimelineGeometry.y(t, range, pxPerHour, top)
            fun x(level: Double) = TimelineGeometry.x(level, plotLeft, plotW)

            // Hour labels and half-hour ticks.
            val muted = colors.onSurfaceMuted
            var tick = range.start.atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant()
            while (tick <= range.endInclusive) {
                if (tick >= range.start) {
                    val ty = y(tick)
                    val text = DriftFormat.clock(tick, zone, use24h)
                    val m = measurer.measure(text, type.caption)
                    label(measurer, text, type.caption, muted, Offset(16.dp.toPx(), ty - m.size.height / 2f))
                }
                val half = tick.plus(Duration.ofMinutes(30))
                if (half >= range.start && half <= range.endInclusive) {
                    val hy = y(half)
                    drawLine(colors.outline.copy(alpha = 0.6f), Offset(16.dp.toPx(), hy), Offset(26.dp.toPx(), hy), 1.dp.toPx())
                }
                tick = tick.plus(Duration.ofHours(1))
            }

            // The curve, its guide lines, and phase colouring.
            val pts = dayCurve.points.filter { it.at >= range.start && it.at <= range.endInclusive }
            if (pts.size >= 2) {
                val line = Path().apply {
                    moveTo(x(pts[0].level), y(pts[0].at))
                    for (i in 1 until pts.size) lineTo(x(pts[i].level), y(pts[i].at))
                }
                val guide = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val gap = 28.dp.toPx()
                translate(left = -gap) { drawPath(line, colors.outlineSubtle, style = guide) }
                translate(left = gap) { drawPath(line, colors.outlineSubtle, style = guide) }
                val visibleBottom = top + (y(range.endInclusive) - top) * progress
                val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                dayCurve.segments.forEach { seg ->
                    val a = maxOf(seg.interval.start, range.start)
                    val b = minOf(seg.interval.end, range.endInclusive)
                    if (b <= a) return@forEach
                    clipRect(left = 0f, top = y(a), right = size.width, bottom = minOf(y(b), visibleBottom)) {
                        drawPath(line, lineColor(seg.phase, colors), style = stroke)
                    }
                }
            }

            if (progress >= 0.999f) {
                // Peak and dip flags, pointing at the line from the left.
                val flagStyle = type.caption.copy(fontWeight = FontWeight.SemiBold)
                dayCurve.segments.forEach { seg ->
                    val at = seg.extremumAt ?: return@forEach
                    if (at < range.start || at > range.endInclusive) return@forEach
                    val text = when (seg.phase) {
                        EnergyPhase.MORNING_PEAK, EnergyPhase.EVENING_PEAK -> peakLabel
                        EnergyPhase.AFTERNOON_DIP -> dipLabel
                        else -> return@forEach
                    }
                    val lv = EnergyGeometry.levelAt(dayCurve, at) ?: return@forEach
                    val tip = Offset(x(lv) - 4.dp.toPx(), y(at))
                    val m = measurer.measure(text, flagStyle)
                    val h = m.size.height + 8.dp.toPx()
                    val w = m.size.width + 20.dp.toPx()
                    val point = 8.dp.toPx()
                    val left = (tip.x - point - w).clampSafe(plotLeft - 8.dp.toPx(), size.width)
                    val flag = Path().apply {
                        addRoundRect(RoundRect(left, tip.y - h / 2, left + w, tip.y + h / 2, CornerRadius(6.dp.toPx())))
                        moveTo(left + w - 1f, tip.y - h / 2 + 3.dp.toPx())
                        lineTo(left + w + point, tip.y)
                        lineTo(left + w - 1f, tip.y + h / 2 - 3.dp.toPx())
                        close()
                    }
                    drawPath(flag, colors.primaryContainer)
                    label(measurer, text, flagStyle, colors.onPrimaryContainer, Offset(left + 10.dp.toPx(), tip.y - m.size.height / 2f))
                }

                // Habit markers with labels to the right of the dot; a label that would overlap the last one is skipped.
                val titleStyle = type.caption.copy(fontWeight = FontWeight.Bold)
                var lastBottom = -Float.MAX_VALUE
                markers.sortedBy { it.at }.forEach { mk ->
                    if (mk.at < range.start || mk.at > range.endInclusive) return@forEach
                    val lv = EnergyGeometry.levelAt(dayCurve, mk.at) ?: return@forEach
                    val c = Offset(x(lv), y(mk.at))
                    drawCircle(colors.surface, 7.dp.toPx(), c)
                    drawCircle(colors.energy, 7.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                    val t1 = measurer.measure(mk.time, type.caption)
                    val t2 = measurer.measure(mk.title, titleStyle)
                    val blockH = (t1.size.height + t2.size.height).toFloat()
                    val blockW = maxOf(t1.size.width, t2.size.width).toFloat()
                    val topY = c.y - blockH / 2
                    if (topY < lastBottom + 4.dp.toPx()) return@forEach
                    val right = c.x + 14.dp.toPx()
                    val lx = if (right + blockW <= size.width - 4.dp.toPx()) right else c.x - 14.dp.toPx() - blockW
                    label(measurer, mk.time, type.caption, colors.onSurface, Offset(lx, topY))
                    label(measurer, mk.title, titleStyle, colors.onSurface, Offset(lx, topY + t1.size.height))
                    lastBottom = topY + blockH
                }
            }

            // Now line.
            if (now != null && now >= range.start && now <= range.endInclusive) {
                val ny = y(now)
                drawLine(colors.onSurface.copy(alpha = 0.7f), Offset(plotLeft, ny), Offset(size.width, ny), 1.dp.toPx())
                drawCircle(colors.onSurface, 4.dp.toPx(), Offset(plotLeft, ny))
                EnergyGeometry.levelAt(dayCurve, now)?.let { lv ->
                    drawCircle(colors.onSurface, 6.dp.toPx(), Offset(x(lv), ny))
                    drawCircle(colors.surface, 3.dp.toPx(), Offset(x(lv), ny))
                }
            }
        }
    }
}
