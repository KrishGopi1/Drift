package app.drift.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.drift.designsystem.DriftColors
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftIcons
import app.drift.designsystem.DriftTheme
import app.drift.model.engine.DayCurve
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.EnergyPoint
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** A labelled point on the timeline, such as a habit window: an icon badge on the line at [at], [time] over [title]. */
data class TimelineMarker(val at: Instant, val time: String, val title: String, val icon: ImageVector? = null)

/**
 * A sleep period drawn as a block across the timeline. [editable] blocks get drag handles on both edges; [planned]
 * blocks (tonight's recommendation) are drawn dashed. [heading], [value] and [detail] are drawn above the block.
 */
data class TimelineBlock(
    val key: String,
    val start: Instant,
    val end: Instant,
    val editable: Boolean,
    val planned: Boolean = false,
    val heading: String? = null,
    val value: String? = null,
    val detail: String? = null,
)

/** Pure layout of the timeline: time runs down, energy runs right. */
object TimelineGeometry {
    /** Level 0 and 100 as fractions of the plot width. */
    const val LOW_X = 0.36f
    const val HIGH_X = 0.80f
    val SNAP: Duration = Duration.ofMinutes(5)
    val MIN_SLEEP: Duration = Duration.ofMinutes(30)
    val MAX_SLEEP: Duration = Duration.ofHours(16)

    /** From yesterday 18:00 to tomorrow 12:00, local time: last night, today and tonight. */
    fun span(today: LocalDate, zone: ZoneId): ClosedRange<Instant> =
        today.minusDays(1).atTime(18, 0).atZone(zone).toInstant()..today.plusDays(1).atTime(12, 0).atZone(zone).toInstant()

    fun x(level: Double, left: Float, width: Float): Float =
        left + (LOW_X + (HIGH_X - LOW_X) * (level / 100.0).coerceIn(0.0, 1.0).toFloat()) * width

    fun y(t: Instant, range: ClosedRange<Instant>, pxPerHour: Float, top: Float): Float =
        top + Duration.between(range.start, t).toMillis() / 3_600_000f * pxPerHour

    /** Scroll offset that puts [now] a third of the way down a viewport of [viewportPx]. */
    fun initialScroll(now: Instant?, range: ClosedRange<Instant>, pxPerHour: Float, top: Float, viewportPx: Float, contentPx: Float): Int {
        if (now == null || now < range.start || now > range.endInclusive) return 0
        val target = y(now, range, pxPerHour, top) - viewportPx / 3f
        return target.coerceIn(0f, maxOf(0f, contentPx - viewportPx)).toInt()
    }

    /** Rounds to the nearest [SNAP]. */
    fun snap(t: Instant): Instant {
        val step = SNAP.seconds
        return Instant.ofEpochSecond(Math.floorDiv(t.epochSecond + step / 2, step) * step)
    }

    /**
     * Moves one edge of a sleep block to [to] (snapped). The block stays between [MIN_SLEEP] and [MAX_SLEEP] long and
     * never ends after [latest] (sleep cannot be recorded in the future).
     */
    fun moveEdge(start: Instant, end: Instant, topEdge: Boolean, to: Instant, latest: Instant): Pair<Instant, Instant> {
        val t = snap(to)
        return if (topEdge) {
            val s = t.coerceIn(end.minus(MAX_SLEEP), end.minus(MIN_SLEEP))
            s to end
        } else {
            val e = t.coerceIn(start.plus(MIN_SLEEP), minOf(start.plus(MAX_SLEEP), latest))
            start to e
        }
    }

    /** Awake points of [days] inside [range], split into runs wherever sleep (a segment or a block) interrupts them. */
    fun awakeRuns(days: List<DayCurve>, range: ClosedRange<Instant>, blocks: List<Pair<Instant, Instant>>): List<List<EnergyPoint>> {
        val sleep = days.flatMap { d -> d.segments.filter { it.phase == EnergyPhase.SLEEP }.map { it.interval.start to it.interval.end } } + blocks
        val runs = ArrayList<List<EnergyPoint>>()
        var cur = ArrayList<EnergyPoint>()
        var last: Instant? = null
        for (p in days.flatMap { it.points }.sortedBy { it.at }) {
            val inside = p.at < range.start || p.at > range.endInclusive || sleep.any { (a, b) -> p.at >= a && p.at < b }
            val gap = last != null && Duration.between(last, p.at) > Duration.ofMinutes(10)
            if (inside || gap) {
                if (cur.size >= 2) runs += cur
                cur = ArrayList()
            }
            if (!inside) { cur += p; last = p.at } else last = null
        }
        if (cur.size >= 2) runs += cur
        return runs
    }
}

internal fun lineColor(phase: EnergyPhase, c: DriftColors): Color = when (phase) {
    EnergyPhase.MORNING_PEAK, EnergyPhase.EVENING_PEAK -> c.energy
    EnergyPhase.AFTERNOON_DIP -> c.primary
    EnergyPhase.WIND_DOWN -> c.windDown
    EnergyPhase.MELATONIN_WINDOW -> c.melatonin
    EnergyPhase.GROGGINESS, EnergyPhase.SLEEP -> c.onSurfaceMuted
}

/**
 * The energy schedule as one vertical timeline from yesterday evening to tomorrow morning (docs/ux/visual-refresh.md):
 * hour labels down the left, the energy line coloured by phase in a soft lane, "Peak"/"Dip" flags, habit [markers],
 * sleep [blocks] and a full-width "now" line. The timeline scrolls inside [modifier]'s height and opens on [now].
 *
 * Editable blocks have a handle on each edge. Dragging calls [onBlockDrag] on every 5-minute step (so a caller can
 * preview the curve) and [onBlockDragEnd] on release. TalkBack users get "earlier"/"later" actions on each handle.
 * Reduced motion: no draw-in.
 */
@Composable
fun EnergyTimeline(
    days: List<DayCurve>,
    now: Instant,
    zone: ZoneId,
    use24h: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    blocks: List<TimelineBlock> = emptyList(),
    markers: List<TimelineMarker> = emptyList(),
    peakLabel: String = "Peak",
    dipLabel: String = "Dip",
    handleLabel: (block: TimelineBlock, topEdge: Boolean) -> String = { _, top -> if (top) "Fell asleep" else "Woke up" },
    earlierLabel: String = "5 minutes earlier",
    laterLabel: String = "5 minutes later",
    hourHeight: Dp = 60.dp,
    onBlockDrag: (key: String, start: Instant, end: Instant) -> Unit = { _, _, _ -> },
    onBlockDragEnd: (key: String, start: Instant, end: Instant) -> Unit = { _, _, _ -> },
    onBlockClick: (key: String) -> Unit = {},
) {
    val colors = DriftTheme.colors
    val type = DriftTheme.typography
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val today = now.atZone(zone).toLocalDate()
    val range = remember(today, zone) { TimelineGeometry.span(today, zone) }
    val padV = 28.dp
    val hours = Duration.between(range.start, range.endInclusive).toMinutes() / 60f
    val content = hourHeight * hours + padV * 2
    val progress by rememberDrawIn(today)

    var drafts by remember { mutableStateOf<Map<String, Pair<Instant, Instant>>>(emptyMap()) }
    LaunchedEffect(blocks) { drafts = emptyMap() }
    val shown = blocks.map { b -> drafts[b.key]?.let { (s, e) -> b.copy(start = s, end = e) } ?: b }

    val zz = rememberVectorPainter(DriftIcons.Zz)
    val sun = rememberVectorPainter(DriftIcons.Sun)
    val iconPainters = markers.mapNotNull { it.icon }.distinct().associateWith { rememberVectorPainter(it) }

    BoxWithConstraints(modifier.fillMaxWidth().background(colors.background)) {
        val viewportPx = with(density) { maxHeight.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val scroll = rememberScrollState(
            with(density) { TimelineGeometry.initialScroll(now, range, hourHeight.toPx(), padV.toPx(), viewportPx, content.toPx()) },
        )
        Box(Modifier.fillMaxWidth().height(maxHeight).verticalScroll(scroll)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(content)
                    .testTag("energy_timeline")
                    .clearAndSetSemantics { this.contentDescription = contentDescription }
                    .pointerInput(shown) {
                        detectTapGestures { pos ->
                            val pxPerHour = hourHeight.toPx()
                            val top = padV.toPx()
                            shown.firstOrNull { b ->
                                pos.y in TimelineGeometry.y(b.start, range, pxPerHour, top)..TimelineGeometry.y(b.end, range, pxPerHour, top)
                            }?.let { onBlockClick(it.key) }
                        }
                    },
            ) {
                val top = padV.toPx()
                val pxPerHour = hourHeight.toPx()
                val plotLeft = 48.dp.toPx()
                val plotW = size.width - plotLeft - 8.dp.toPx()
                if (plotW <= 0f) return@Canvas
                fun y(t: Instant) = TimelineGeometry.y(t, range, pxPerHour, top)
                fun x(level: Double) = TimelineGeometry.x(level, plotLeft, plotW)
                drawHourAxis(range, zone, use24h, measurer, type.caption, colors, ::y, plotLeft)

                // Sleep blocks first, so the line and labels sit on top.
                shown.forEach { b -> drawBlock(b, ::y, plotLeft, measurer, colors, type.caption, type.title, zone, use24h, zz, sun) }

                // Lane, guides and the energy line.
                val runs = TimelineGeometry.awakeRuns(days, range, shown.map { it.start to it.end })
                val visibleBottom = top + (size.height - 2 * top) * progress
                val brush = phaseBrush(days, range, ::y, size.height, colors)
                val laneW = 44.dp.toPx()
                runs.forEach { run ->
                    val pts = run.map { Offset(x(it.level), y(it.at)) }.filter { it.y <= visibleBottom }
                    if (pts.size < 2) return@forEach
                    val line = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                    val lane = Path().apply {
                        moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }
                        pts.asReversed().forEach { lineTo(it.x - laneW, it.y) }
                        close()
                    }
                    drawPath(lane, colors.onSurface.copy(alpha = if (colors.isDark) 0.045f else 0.05f))
                    val guide = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    translate(left = -laneW) { drawPath(line, colors.outlineSubtle, style = guide) }
                    translate(left = 28.dp.toPx()) { drawPath(line, colors.outlineSubtle, style = guide) }
                    drawPath(line, brush, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                if (progress >= 0.999f) {
                    drawFlags(days, range, shown, ::x, ::y, plotLeft, peakLabel, dipLabel, measurer, type.caption, colors)
                    drawMarkers(markers, days, range, shown, ::x, ::y, measurer, type.caption, colors, iconPainters)
                }

                // Now line.
                if (now >= range.start && now <= range.endInclusive) {
                    val ny = y(now)
                    drawLine(colors.onSurface.copy(alpha = 0.8f), Offset(plotLeft - 12.dp.toPx(), ny), Offset(size.width, ny), 1.dp.toPx())
                    drawCircle(colors.onSurface, 4.dp.toPx(), Offset(plotLeft - 12.dp.toPx(), ny))
                }
            }

            // Drag handles for editable blocks, in an overlay as tall as the content so every handle can be hit.
            val pxPerHour = with(density) { hourHeight.toPx() }
            val topPx = with(density) { padV.toPx() }
            Box(Modifier.fillMaxWidth().height(content)) {
                shown.filter { it.editable }.forEach { b ->
                    val original = blocks.first { it.key == b.key }
                    listOf(true, false).forEach { topEdge ->
                        SleepHandle(
                            block = b,
                            topEdge = topEdge,
                            xPx = if (topEdge) with(density) { 56.dp.toPx() } else widthPx - with(density) { 120.dp.toPx() },
                            yPx = TimelineGeometry.y(if (topEdge) b.start else b.end, range, pxPerHour, topPx),
                            label = handleLabel(b, topEdge),
                            earlierLabel = earlierLabel,
                            laterLabel = laterLabel,
                            onMove = { deltaMinutes, commit ->
                                val cur = drafts[b.key] ?: (original.start to original.end)
                                val edge = if (topEdge) cur.first else cur.second
                                val moved = TimelineGeometry.moveEdge(cur.first, cur.second, topEdge, edge.plusSeconds((deltaMinutes * 60).toLong()), now)
                                drafts = drafts + (b.key to moved)
                                if (commit) onBlockDragEnd(b.key, moved.first, moved.second) else onBlockDrag(b.key, moved.first, moved.second)
                            },
                            onRelease = { drafts[b.key]?.let { (s, e) -> onBlockDragEnd(b.key, s, e) } },
                            pxPerHour = pxPerHour,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One drag handle: a pill on the block edge with a 48 dp touch area. [onMove] receives minutes moved since the last
 * call (drags) or a fixed 5-minute step with commit = true (accessibility actions).
 */
@Composable
private fun SleepHandle(
    block: TimelineBlock,
    topEdge: Boolean,
    xPx: Float,
    yPx: Float,
    label: String,
    earlierLabel: String,
    laterLabel: String,
    onMove: (deltaMinutes: Double, commit: Boolean) -> Unit,
    onRelease: () -> Unit,
    pxPerHour: Float,
) {
    val density = LocalDensity.current
    val move by rememberUpdatedState(onMove)
    val release by rememberUpdatedState(onRelease)
    val w = 96.dp
    val h = 48.dp
    Box(
            Modifier
                .offset { IntOffset(xPx.roundToInt(), (yPx - with(density) { h.toPx() } / 2).roundToInt()) }
                .size(w, h)
                .testTag("sleep_handle_${block.key}_${if (topEdge) "start" else "end"}")
                .semantics {
                    contentDescription = label
                    customActions = listOf(
                        CustomAccessibilityAction(earlierLabel) { move(-5.0, true); true },
                        CustomAccessibilityAction(laterLabel) { move(5.0, true); true },
                    )
                }
                .pointerInput(block.key, topEdge) {
                    var acc = 0f
                    detectVerticalDragGestures(
                        onDragStart = { acc = 0f },
                        onDragEnd = { release() },
                        onDragCancel = { release() },
                    ) { change, dy ->
                        change.consume()
                        acc += dy
                        val minutes = acc / pxPerHour * 60f
                        // Report whole 5-minute steps only, carrying the remainder.
                        val steps = (minutes / 5f).toInt()
                        if (steps != 0) {
                            move(steps * 5.0, false)
                            acc -= steps * 5f / 60f * pxPerHour
                        }
                    }
                },
        ) {
            Box(
                Modifier
                    .offset(x = 24.dp, y = (h - 6.dp) / 2)
                    .size(48.dp, 6.dp)
                    .background(DriftTheme.colors.primary, RoundedCornerShape(3.dp)),
            )
        }
}

private fun DrawScope.drawHourAxis(
    range: ClosedRange<Instant>,
    zone: ZoneId,
    use24h: Boolean,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    colors: DriftColors,
    y: (Instant) -> Float,
    plotLeft: Float,
) {
    var tick = range.start.atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant()
    while (tick <= range.endInclusive) {
        if (tick >= range.start) {
            val ty = y(tick)
            val text = DriftFormat.hourLabel(tick, zone, use24h)
            val m = measurer.measure(text, style)
            label(measurer, text, style, colors.onSurfaceMuted, Offset(14.dp.toPx(), ty - m.size.height / 2f))
            drawLine(colors.outlineSubtle.copy(alpha = 0.5f), Offset(plotLeft, ty), Offset(size.width, ty), 0.5.dp.toPx())
        }
        val half = tick.plus(Duration.ofMinutes(30))
        if (half >= range.start && half <= range.endInclusive) {
            val hy = y(half)
            drawLine(colors.outline.copy(alpha = 0.7f), Offset(14.dp.toPx(), hy), Offset(24.dp.toPx(), hy), 1.dp.toPx())
        }
        tick = tick.plus(Duration.ofHours(1))
    }
}

private fun DrawScope.drawBlock(
    b: TimelineBlock,
    y: (Instant) -> Float,
    plotLeft: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    colors: DriftColors,
    caption: TextStyle,
    title: TextStyle,
    zone: ZoneId,
    use24h: Boolean,
    zz: VectorPainter,
    sun: VectorPainter,
) {
    val top = y(b.start)
    val bottom = y(b.end)
    val left = plotLeft - 4.dp.toPx()
    val right = size.width - 4.dp.toPx()
    val r = CornerRadius(10.dp.toPx())
    val fill = if (b.planned) colors.primaryContainer.copy(alpha = 0.16f) else colors.primaryContainer.copy(alpha = if (colors.isDark) 0.42f else 0.22f)
    drawRoundRect(fill, Offset(left, top), Size(right - left, bottom - top), r)
    drawRoundRect(
        colors.primary.copy(alpha = 0.85f), Offset(left, top), Size(right - left, bottom - top), r,
        style = Stroke(1.dp.toPx(), pathEffect = if (b.planned) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())) else null),
    )
    val bold = caption.copy(fontWeight = FontWeight.Bold)
    val pad = 10.dp.toPx()
    val icon = 14.dp.toPx()
    // Start time with the sleep mark at the top left, end time with the sun at the bottom right.
    if (bottom - top > 44.dp.toPx()) {
        val startText = DriftFormat.shortClock(b.start, zone, use24h)
        val sm = measurer.measure(startText, bold)
        translate(left + pad, top + pad) { with(zz) { draw(Size(icon, icon), colorFilter = ColorFilter.tint(colors.primary)) } }
        label(measurer, startText, bold, colors.onSurface, Offset(left + pad + icon + 6.dp.toPx(), top + pad + icon / 2 - sm.size.height / 2f))
        val endText = DriftFormat.shortClock(b.end, zone, use24h)
        val em = measurer.measure(endText, bold)
        val ey = bottom - pad - icon
        translate(right - pad - icon, ey) { with(sun) { draw(Size(icon, icon), colorFilter = ColorFilter.tint(colors.primary)) } }
        label(measurer, endText, bold, colors.onSurface, Offset(right - pad - icon - 6.dp.toPx() - em.size.width, ey + icon / 2 - em.size.height / 2f))
    }
    // Heading above the block.
    val lines = listOfNotNull(b.heading?.let { it to title }, b.value?.let { it to title.copy(fontWeight = FontWeight.Normal) }, b.detail?.let { it to caption })
    if (lines.isNotEmpty()) {
        val measured = lines.map { (t, s) -> Triple(t, s, measurer.measure(t, s)) }
        var cy = top - 10.dp.toPx() - measured.sumOf { it.third.size.height }
        measured.forEachIndexed { i, (t, s, m) ->
            val c = when (i) { 0 -> colors.onSurface; else -> colors.onSurfaceMuted }
            label(measurer, t, s, c, Offset(plotLeft, cy))
            cy += m.size.height
        }
    }
}

/** Vertical gradient whose colour at each phase's middle is that phase's colour. */
private fun phaseBrush(days: List<DayCurve>, range: ClosedRange<Instant>, y: (Instant) -> Float, height: Float, colors: DriftColors): Brush {
    val stops = days.flatMap { it.segments }
        .filter { it.phase != EnergyPhase.SLEEP && it.interval.end > range.start && it.interval.start < range.endInclusive }
        .map { seg ->
            val mid = seg.interval.start.plus(Duration.between(seg.interval.start, seg.interval.end).dividedBy(2))
            (y(mid) / height).coerceIn(0f, 1f) to lineColor(seg.phase, colors)
        }
        .sortedBy { it.first }
        .distinctBy { it.first }
    return when {
        stops.isEmpty() -> Brush.verticalGradient(listOf(colors.energy, colors.energy))
        stops.size == 1 -> Brush.verticalGradient(listOf(stops[0].second, stops[0].second))
        else -> Brush.verticalGradient(*stops.toTypedArray(), startY = 0f, endY = height)
    }
}

private fun DrawScope.drawFlags(
    days: List<DayCurve>,
    range: ClosedRange<Instant>,
    blocks: List<TimelineBlock>,
    x: (Double) -> Float,
    y: (Instant) -> Float,
    plotLeft: Float,
    peakLabel: String,
    dipLabel: String,
    measurer: androidx.compose.ui.text.TextMeasurer,
    caption: TextStyle,
    colors: DriftColors,
) {
    val style = caption.copy(fontWeight = FontWeight.SemiBold)
    days.flatMap { d -> d.segments.map { d to it } }.forEach { (day, seg) ->
        val at = seg.extremumAt ?: return@forEach
        if (at < range.start || at > range.endInclusive || blocks.any { at >= it.start && at < it.end }) return@forEach
        val text = when (seg.phase) {
            EnergyPhase.MORNING_PEAK, EnergyPhase.EVENING_PEAK -> peakLabel
            EnergyPhase.AFTERNOON_DIP -> dipLabel
            else -> return@forEach
        }
        val lv = EnergyGeometry.levelAt(day, at) ?: return@forEach
        val tip = Offset(x(lv) - 6.dp.toPx(), y(at))
        val m = measurer.measure(text, style)
        val h = m.size.height + 10.dp.toPx()
        val w = m.size.width + 20.dp.toPx()
        val point = 9.dp.toPx()
        val left = (tip.x - point - w).clampSafe(plotLeft - 8.dp.toPx(), size.width)
        val flag = Path().apply {
            addRoundRect(RoundRect(left, tip.y - h / 2, left + w, tip.y + h / 2, CornerRadius(6.dp.toPx())))
            moveTo(left + w - 1f, tip.y - h / 2 + 2.dp.toPx())
            lineTo(left + w + point, tip.y)
            lineTo(left + w - 1f, tip.y + h / 2 - 2.dp.toPx())
            close()
        }
        drawPath(flag, colors.primaryContainer)
        label(measurer, text, style, colors.onPrimaryContainer, Offset(left + 10.dp.toPx(), tip.y - m.size.height / 2f))
    }
}

private fun DrawScope.drawMarkers(
    markers: List<TimelineMarker>,
    days: List<DayCurve>,
    range: ClosedRange<Instant>,
    blocks: List<TimelineBlock>,
    x: (Double) -> Float,
    y: (Instant) -> Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    caption: TextStyle,
    colors: DriftColors,
    painters: Map<ImageVector, VectorPainter>,
) {
    val titleStyle = caption.copy(fontWeight = FontWeight.Bold)
    var lastBottom = -Float.MAX_VALUE
    markers.sortedBy { it.at }.forEach { mk ->
        if (mk.at < range.start || mk.at > range.endInclusive || blocks.any { mk.at >= it.start && mk.at < it.end }) return@forEach
        val day = days.firstOrNull { d -> d.points.isNotEmpty() && mk.at >= d.points.first().at && mk.at <= d.points.last().at } ?: return@forEach
        val lv = EnergyGeometry.levelAt(day, mk.at) ?: return@forEach
        val c = Offset(x(lv), y(mk.at))
        val t1 = measurer.measure(mk.time, caption)
        val t2 = measurer.measure(mk.title, titleStyle)
        val blockH = (t1.size.height + t2.size.height).toFloat()
        val blockW = maxOf(t1.size.width, t2.size.width).toFloat()
        val topY = c.y - blockH / 2
        if (topY < lastBottom + 6.dp.toPx()) return@forEach
        val badge = 20.dp.toPx()
        drawRoundRect(colors.primaryContainer, Offset(c.x - badge / 2, c.y - badge / 2), Size(badge, badge), CornerRadius(5.dp.toPx()))
        val painter = mk.icon?.let { painters[it] }
        if (painter != null) {
            val ic = 14.dp.toPx()
            translate(c.x - ic / 2, c.y - ic / 2) { with(painter) { draw(Size(ic, ic), colorFilter = ColorFilter.tint(colors.onPrimaryContainer)) } }
        }
        val right = c.x + badge / 2 + 8.dp.toPx()
        val lx = if (right + blockW <= size.width - 4.dp.toPx()) right else c.x - badge / 2 - 8.dp.toPx() - blockW
        label(measurer, mk.time, caption, colors.onSurface, Offset(lx, topY))
        label(measurer, mk.title, titleStyle, colors.onSurface, Offset(lx, topY + t1.size.height))
        lastBottom = topY + blockH
    }
}
