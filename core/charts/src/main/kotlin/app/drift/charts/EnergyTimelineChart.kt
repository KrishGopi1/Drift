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
import androidx.compose.ui.graphics.drawscope.clipPath
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

    /**
     * The waking part of one day's curve: from the end of the first Sleep segment to the start of the next one.
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

    /** One phase drawn as a block: consecutive segments of the same phase are merged; sleep blocks cut them. */
    data class Panel(val phase: EnergyPhase, val start: Instant, val end: Instant)

    fun phasePanels(days: List<DayCurve>, range: ClosedRange<Instant>, blocks: List<Pair<Instant, Instant>>): List<Panel> {
        val merged = ArrayList<Panel>()
        days.flatMap { it.segments }
            .filter { it.phase != EnergyPhase.SLEEP }
            .sortedBy { it.interval.start }
            .forEach { seg ->
                val a = maxOf(seg.interval.start, range.start)
                val b = minOf(seg.interval.end, range.endInclusive)
                if (b <= a) return@forEach
                val last = merged.lastOrNull()
                if (last != null && last.phase == seg.phase && !a.isAfter(last.end.plusSeconds(60))) {
                    merged[merged.lastIndex] = last.copy(end = maxOf(last.end, b))
                } else {
                    merged += Panel(seg.phase, a, b)
                }
            }
        // Cut out sleep blocks.
        var out: List<Panel> = merged
        for ((bs, be) in blocks) {
            out = out.flatMap { p ->
                if (be <= p.start || bs >= p.end) listOf(p)
                else listOfNotNull(
                    p.copy(end = bs).takeIf { bs > p.start },
                    p.copy(start = be).takeIf { be < p.end },
                )
            }
        }
        return out.filter { Duration.between(it.start, it.end) >= Duration.ofMinutes(10) }
    }

    /** Light moving average (window [half] each side) so the 5-minute model steps draw as one smooth line. */
    fun smooth(levels: List<Double>, half: Int = 3): List<Double> = levels.indices.map { i ->
        val a = maxOf(0, i - half)
        val b = minOf(levels.lastIndex, i + half)
        levels.subList(a, b + 1).average()
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
    phaseLabel: (EnergyPhase) -> String = { it.displayName() },
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
                val panelLeft = plotLeft - 4.dp.toPx()
                val panelRight = size.width * 0.80f

                // Phase blocks, each named at its top; the one holding "now" is lighter.
                val blockSpans = shown.map { it.start to it.end }
                TimelineGeometry.phasePanels(days, range, blockSpans).forEach { panel ->
                    drawPhasePanel(panel, ::y, panelLeft, panelRight, now, phaseLabel(panel.phase), zone, use24h, measurer, type, colors)
                }
                // Sleep blocks.
                shown.forEach { b -> drawBlock(b, ::y, panelLeft, measurer, colors, type.caption, type.title, zone, use24h, zz, sun) }

                // One smooth energy line, coloured by level.
                val runs = TimelineGeometry.awakeRuns(days, range, blockSpans)
                val visibleBottom = top + (size.height - 2 * top) * progress
                val brush = Brush.horizontalGradient(
                    listOf(colors.curveLow, colors.curveMid, colors.curveHigh),
                    startX = x(0.0), endX = x(100.0),
                )
                runs.forEach { run ->
                    val levels = TimelineGeometry.smooth(run.map { it.level })
                    val pts = run.indices.map { i -> Offset(x(levels[i]), y(run[i].at)) }.filter { it.y <= visibleBottom }
                    if (pts.size < 2) return@forEach
                    val line = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) {
                            val mid = Offset((pts[i - 1].x + pts[i].x) / 2, (pts[i - 1].y + pts[i].y) / 2)
                            quadraticTo(pts[i - 1].x, pts[i - 1].y, mid.x, mid.y)
                        }
                        lineTo(pts.last().x, pts.last().y)
                    }
                    drawPath(line, brush, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                if (progress >= 0.999f) {
                    drawMarkers(markers, range, shown, ::y, measurer, type.caption, colors, iconPainters)
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

private fun DrawScope.drawPhasePanel(
    panel: TimelineGeometry.Panel,
    y: (Instant) -> Float,
    left: Float,
    right: Float,
    now: Instant,
    name: String,
    zone: ZoneId,
    use24h: Boolean,
    measurer: androidx.compose.ui.text.TextMeasurer,
    type: app.drift.designsystem.DriftTypography,
    colors: DriftColors,
) {
    val top = y(panel.start) + 1.5.dp.toPx()
    val bottom = y(panel.end) - 1.5.dp.toPx()
    if (bottom - top < 6.dp.toPx()) return
    val current = now >= panel.start && now < panel.end
    drawRoundRect(
        if (current) colors.surfaceRaised else colors.surface,
        Offset(left, top), Size(right - left, bottom - top), CornerRadius(14.dp.toPx()),
    )
    val pad = 12.dp.toPx()
    val titleStyle = type.titleSmall
    val m = measurer.measure(name, titleStyle)
    if (bottom - top > m.size.height + pad) {
        label(measurer, name, titleStyle, colors.onSurface, Offset(left + pad, top + pad * 0.7f))
        val times = "${DriftFormat.shortClock(panel.start, zone, use24h)} – ${DriftFormat.shortClock(panel.end, zone, use24h)}"
        val tm = measurer.measure(times, type.caption)
        if (bottom - top > m.size.height + tm.size.height + pad * 1.6f) {
            label(measurer, times, type.caption, colors.onSurfaceMuted, Offset(left + pad, top + pad * 0.7f + m.size.height))
        }
    }
}

private fun DrawScope.drawBlock(
    b: TimelineBlock,
    y: (Instant) -> Float,
    left: Float,
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
    val right = size.width - 6.dp.toPx()
    val r = CornerRadius(14.dp.toPx())
    val shape = Path().apply { addRoundRect(RoundRect(left, top, right, bottom, r)) }
    drawPath(shape, if (b.planned) colors.surface else colors.accentSurface)
    // Soft wave texture marks sleep.
    clipPath(shape) {
        val step = 16.dp.toPx()
        val amp = 3.dp.toPx()
        val wave = 28.dp.toPx()
        var wy = top + step / 2
        while (wy < bottom) {
            val p = Path().apply {
                moveTo(left, wy)
                var wx = left
                while (wx < right) {
                    quadraticTo(wx + wave / 4, wy - amp, wx + wave / 2, wy)
                    quadraticTo(wx + wave * 3 / 4, wy + amp, wx + wave, wy)
                    wx += wave
                }
            }
            drawPath(p, colors.primary.copy(alpha = if (b.planned) 0.10f else 0.16f), style = Stroke(1.dp.toPx()))
            wy += step
        }
    }
    drawPath(
        shape, colors.primary.copy(alpha = if (b.planned) 0.6f else 0.9f),
        style = Stroke(1.dp.toPx(), pathEffect = if (b.planned) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())) else null),
    )
    val bold = caption.copy(fontWeight = FontWeight.Bold)
    val pad = 10.dp.toPx()
    val icon = 14.dp.toPx()
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
            label(measurer, t, s, if (i == 0) colors.onSurface else colors.onSurfaceMuted, Offset(left + 4.dp.toPx(), cy))
            cy += m.size.height
        }
    }
}

/** Habit and window markers as pills on the right edge: icon, title and time. Overlapping pills are skipped. */
private fun DrawScope.drawMarkers(
    markers: List<TimelineMarker>,
    range: ClosedRange<Instant>,
    blocks: List<TimelineBlock>,
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
        val t1 = measurer.measure(mk.title, titleStyle)
        val t2 = measurer.measure(mk.time, caption)
        val badge = 22.dp.toPx()
        val pad = 6.dp.toPx()
        val h = maxOf(badge, (t1.size.height + t2.size.height).toFloat()) + pad * 2
        val w = pad + badge + 8.dp.toPx() + maxOf(t1.size.width, t2.size.width) + pad * 2
        val cy = y(mk.at)
        val top = cy - h / 2
        if (top < lastBottom + 4.dp.toPx()) return@forEach
        val right = size.width - 6.dp.toPx()
        val left = right - w
        drawRoundRect(colors.surfaceRaised, Offset(left, top), Size(w, h), CornerRadius(10.dp.toPx()))
        drawRoundRect(colors.outlineSubtle, Offset(left, top), Size(w, h), CornerRadius(10.dp.toPx()), style = Stroke(1.dp.toPx()))
        val bx = left + pad
        val by = cy - badge / 2
        drawRoundRect(colors.primaryContainer, Offset(bx, by), Size(badge, badge), CornerRadius(6.dp.toPx()))
        mk.icon?.let { painters[it] }?.let { painter ->
            val ic = 15.dp.toPx()
            translate(bx + (badge - ic) / 2, by + (badge - ic) / 2) { with(painter) { draw(Size(ic, ic), colorFilter = ColorFilter.tint(colors.onPrimaryContainer)) } }
        }
        val tx = bx + badge + 8.dp.toPx()
        val textTop = cy - (t1.size.height + t2.size.height) / 2f
        label(measurer, mk.title, titleStyle, colors.onSurface, Offset(tx, textTop))
        label(measurer, mk.time, caption, colors.onSurfaceMuted, Offset(tx, textTop + t1.size.height))
        lastBottom = top + h
    }
}
