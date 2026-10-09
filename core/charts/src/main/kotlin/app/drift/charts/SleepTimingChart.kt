package app.drift.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftTheme
import app.drift.model.EpisodeKind
import app.drift.model.SleepEpisode
import java.time.Duration
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class TimingRange(val days: Int) { WEEK(7), MONTH(30), QUARTER(90) }

/**
 * One night on the timing chart. Minutes are relative to local midnight at the start of [date] (the wake date), so
 * a 23:30 bedtime is -30 and a 07:10 wake time is 430. Both null = no data for that night.
 */
data class TimingBar(val date: LocalDate, val onsetMinutes: Int?, val wakeMinutes: Int?) {
    val durationMinutes: Int? get() = if (onsetMinutes != null && wakeMinutes != null) wakeMinutes - onsetMinutes else null
}

/**
 * Builds one [TimingBar] per day for the [range] ending on [endDate] (oldest first). Uses the longest MAIN episode
 * that ended on that date, converted with the episode's own zone.
 */
fun timingBars(episodes: List<SleepEpisode>, endDate: LocalDate, range: TimingRange): List<TimingBar> {
    val byWakeDate = episodes.filter { it.kind == EpisodeKind.MAIN }.groupBy { it.end.atZone(it.zone).toLocalDate() }
    return (range.days - 1 downTo 0).map { back ->
        val date = endDate.minusDays(back.toLong())
        val ep = byWakeDate[date]?.maxByOrNull { Duration.between(it.start, it.end) }
        if (ep == null) TimingBar(date, null, null) else {
            val midnight = date.atStartOfDay(ep.zone).toInstant()
            TimingBar(
                date,
                Duration.between(midnight, ep.start).toMinutes().toInt(),
                Duration.between(midnight, ep.end).toMinutes().toInt(),
            )
        }
    }
}

/** Hour-aligned vertical span (minutes relative to midnight) that fits every bar, at least 6 h tall. */
fun timingAxis(bars: List<TimingBar>): Pair<Int, Int> {
    val onsets = bars.mapNotNull { it.onsetMinutes }
    val wakes = bars.mapNotNull { it.wakeMinutes }
    if (onsets.isEmpty() || wakes.isEmpty()) return -120 to 480
    var lo = (floor((onsets.min() - 30) / 60.0) * 60).toInt()
    var hi = (ceil((wakes.max() + 30) / 60.0) * 60).toInt()
    if (hi - lo < 360) { val extra = (360 - (hi - lo)) / 2; lo -= extra; hi += extra }
    return lo to hi
}

internal fun showDayLabel(index: Int, count: Int, bar: TimingBar, range: TimingRange): String? = when (range) {
    TimingRange.WEEK -> weekdayInitial(bar.date)
    TimingRange.MONTH -> if ((count - 1 - index) % 5 == 0) bar.date.dayOfMonth.toString() else null
    TimingRange.QUARTER -> if (bar.date.dayOfMonth == 1) bar.date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) else null
}

/**
 * Range bars from bedtime to wake time per night (later times lower on the chart). Nights without data are gaps
 * with a small ring. [onNightClick] makes bars tappable; [selectedDate] highlights one night. Provide a list of the
 * same nights next to this chart for screen-reader and keyboard access.
 */
@Composable
fun SleepTimingChart(
    bars: List<TimingBar>,
    range: TimingRange,
    modifier: Modifier = Modifier,
    use24h: Boolean = true,
    height: Dp = 220.dp,
    selectedDate: LocalDate? = null,
    onNightClick: ((LocalDate) -> Unit)? = null,
    contentDescription: String = remember(bars, range, use24h) { sleepTimingDescription(bars, range, use24h) },
) {
    val colors = DriftTheme.colors
    val type = DriftTheme.typography
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val gutter = remember(use24h, type, density) {
        measurer.measure(DriftFormat.clockMinutes(23 * 60 + 59, use24h), type.caption).size.width + with(density) { 8.dp.toPx() }
    }
    val progress by rememberDrawIn(range)
    val (lo, hi) = remember(bars) { timingAxis(bars) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm)) {
        Canvas(
            Modifier.fillMaxWidth().height(height)
                .semantics { this.contentDescription = contentDescription }
                .then(
                    if (onNightClick != null) Modifier.pointerInput(bars, gutter) {
                        detectTapGestures { p ->
                            val slot = (size.width - gutter) / bars.size
                            val i = ((p.x - gutter) / slot).toInt()
                            if (i in bars.indices) onNightClick(bars[i].date)
                        }
                    } else Modifier,
                ),
        ) {
            if (bars.isEmpty()) return@Canvas
            val axisH = measurer.measure("M", type.caption).size.height + 6.dp.toPx()
            val plotH = size.height - axisH
            val plotW = size.width - gutter
            fun y(minutes: Int) = (minutes - lo).toFloat() / (hi - lo) * plotH
            // Grid every 2 h, labelled.
            var m = ceil(lo / 120.0).toInt() * 120
            while (m <= hi) {
                drawLine(colors.outlineSubtle, Offset(gutter, y(m)), Offset(size.width, y(m)), 1.dp.toPx())
                val text = DriftFormat.clockMinutes(m, use24h)
                val tm = measurer.measure(text, type.caption)
                label(measurer, text, type.caption, colors.onSurfaceMuted, Offset(0f, (y(m) - tm.size.height / 2f).coerceIn(0f, plotH - tm.size.height)))
                m += 120
            }
            val slot = plotW / bars.size
            val barW = min(slot * (if (range == TimingRange.QUARTER) 0.8f else 0.62f), 28.dp.toPx()).coerceAtLeast(2.dp.toPx())
            bars.forEachIndexed { i, b ->
                val cx = gutter + slot * i + slot / 2
                if (b.date == selectedDate) drawRoundRect(colors.primary.copy(alpha = 0.14f), Offset(gutter + slot * i, 0f), Size(slot, plotH), CornerRadius(4.dp.toPx()))
                val on = b.onsetMinutes
                val wk = b.wakeMinutes
                if (on == null || wk == null) {
                    drawCircle(colors.outline, min(3.dp.toPx(), slot / 3), Offset(cx, plotH / 2), style = Stroke(1.dp.toPx()))
                } else {
                    val top = y(on)
                    val h = max((y(wk) - top) * progress, 2.dp.toPx())
                    drawRoundRect(
                        if (b.date == selectedDate) colors.energy else colors.primary,
                        Offset(cx - barW / 2, top), Size(barW, h), CornerRadius(min(barW / 2, 6.dp.toPx())),
                    )
                }
                showDayLabel(i, bars.size, b, range)?.let { text ->
                    val tm = measurer.measure(text, type.caption)
                    label(measurer, text, type.caption, colors.onSurfaceMuted, Offset((cx - tm.size.width / 2f).coerceIn(gutter, size.width - tm.size.width), plotH + 4.dp.toPx()))
                }
            }
        }
        if (bars.any { it.onsetMinutes == null }) {
            FlowRow(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.lg)) {
                LegendItem("Asleep", colors.primary, LegendShape.Solid)
                LegendItem("No data", colors.outline, LegendShape.Ring)
            }
        }
    }
}

/** Sleep duration per night as bars with an optional dashed line at the sleep need. Same data as [SleepTimingChart]. */
@Composable
fun SleepDurationChart(
    bars: List<TimingBar>,
    range: TimingRange,
    modifier: Modifier = Modifier,
    need: Duration? = null,
    height: Dp = 180.dp,
    selectedDate: LocalDate? = null,
    onNightClick: ((LocalDate) -> Unit)? = null,
    contentDescription: String = remember(bars, range, need) { sleepDurationDescription(bars, need) },
) {
    val colors = DriftTheme.colors
    val type = DriftTheme.typography
    val measurer = rememberTextMeasurer()
    val progress by rememberDrawIn(range)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm)) {
        Canvas(
            Modifier.fillMaxWidth().height(height)
                .semantics { this.contentDescription = contentDescription }
                .then(
                    if (onNightClick != null) Modifier.pointerInput(bars) {
                        detectTapGestures { p ->
                            val i = (p.x / (size.width / bars.size)).toInt()
                            if (i in bars.indices) onNightClick(bars[i].date)
                        }
                    } else Modifier,
                ),
        ) {
            if (bars.isEmpty()) return@Canvas
            val axisH = measurer.measure("M", type.caption).size.height + 6.dp.toPx()
            val plotH = size.height - axisH
            val maxMin = max(600, max(bars.maxOf { it.durationMinutes ?: 0 }, need?.toMinutes()?.toInt() ?: 0) + 30)
            val slot = size.width / bars.size
            val barW = min(slot * (if (range == TimingRange.QUARTER) 0.8f else 0.62f), 28.dp.toPx()).coerceAtLeast(2.dp.toPx())
            drawLine(colors.outline, Offset(0f, plotH), Offset(size.width, plotH), 1.dp.toPx())
            bars.forEachIndexed { i, b ->
                val cx = slot * i + slot / 2
                if (b.date == selectedDate) drawRoundRect(colors.primary.copy(alpha = 0.14f), Offset(slot * i, 0f), Size(slot, plotH), CornerRadius(4.dp.toPx()))
                val d = b.durationMinutes
                if (d == null) drawCircle(colors.outline, min(3.dp.toPx(), slot / 3), Offset(cx, plotH - 4.dp.toPx()), style = Stroke(1.dp.toPx()))
                else {
                    val h = max(d.toFloat() / maxMin * plotH * progress, 2.dp.toPx())
                    val met = need == null || d >= need.toMinutes()
                    drawRoundRect(if (met) colors.primary else colors.debt, Offset(cx - barW / 2, plotH - h), Size(barW, h), CornerRadius(min(barW / 2, 6.dp.toPx())))
                }
                showDayLabel(i, bars.size, b, range)?.let { text ->
                    val tm = measurer.measure(text, type.caption)
                    label(measurer, text, type.caption, colors.onSurfaceMuted, Offset((cx - tm.size.width / 2f).coerceIn(0f, size.width - tm.size.width), plotH + 4.dp.toPx()))
                }
            }
            if (need != null) {
                val ny = plotH - need.toMinutes().toFloat() / maxMin * plotH
                drawLine(colors.energy, Offset(0f, ny), Offset(size.width, ny), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            }
        }
        FlowRow(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs)) {
            if (need != null) {
                LegendItem("Met your need", colors.primary, LegendShape.Solid)
                LegendItem("Short of need", colors.debt, LegendShape.Solid)
                LegendItem("Sleep need", colors.energy, LegendShape.Line)
            }
            if (bars.any { it.onsetMinutes == null }) LegendItem("No data", colors.outline, LegendShape.Ring)
        }
    }
}

fun sleepDurationDescription(bars: List<TimingBar>, need: Duration?): String {
    val present = bars.mapNotNull { it.durationMinutes }
    if (present.isEmpty()) return "Sleep duration chart. No recorded nights."
    val avg = present.average().toLong()
    val short = need?.let { n -> present.count { it < n.toMinutes() } }
    return "Sleep duration over ${bars.size} nights, ${present.size} recorded. Average ${DriftFormat.durationSpoken(Duration.ofMinutes(avg))}, " +
        "shortest ${DriftFormat.durationSpoken(Duration.ofMinutes(present.min().toLong()))}, longest ${DriftFormat.durationSpoken(Duration.ofMinutes(present.max().toLong()))}." +
        (short?.let { " $it of ${present.size} nights were under your sleep need." } ?: "") +
        (bars.size - present.size).takeIf { it > 0 }?.let { " $it nights have no data." }.orEmpty()
}
