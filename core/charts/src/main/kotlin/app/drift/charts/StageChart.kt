package app.drift.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftTheme
import app.drift.model.SleepStage
import app.drift.model.StageSpan
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

private val stageRows = listOf(SleepStage.AWAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)

internal fun stageName(s: SleepStage) = when (s) {
    SleepStage.AWAKE -> "Awake"; SleepStage.REM -> "REM"; SleepStage.LIGHT -> "Light"; SleepStage.DEEP -> "Deep"; SleepStage.UNKNOWN -> "Unknown"
}

/**
 * Hypnogram: one row per stage (Awake, REM, Light, Deep from top to bottom), blocks for each span joined by thin
 * connectors, and an hourly time axis. Unknown spans are drawn as a faint full-height band. Draws in over 1.1 s.
 */
@Composable
fun StageChart(
    spans: List<StageSpan>,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    use24h: Boolean = true,
    height: Dp = 180.dp,
    contentDescription: String = remember(spans, zone, use24h) { stageDescription(spans, zone, use24h) },
) {
    val colors = DriftTheme.colors
    val type = DriftTheme.typography
    val measurer = rememberTextMeasurer()
    val progress by rememberDrawIn(spans.size)
    val sorted = remember(spans) { spans.sortedBy { it.interval.start } }
    fun colorOf(s: SleepStage) = when (s) {
        SleepStage.AWAKE -> colors.stageAwake; SleepStage.REM -> colors.stageRem; SleepStage.LIGHT -> colors.stageLight
        SleepStage.DEEP -> colors.stageDeep; SleepStage.UNKNOWN -> colors.outline
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm)) {
        Canvas(Modifier.fillMaxWidth().height(height).semantics { this.contentDescription = contentDescription }) {
            if (sorted.isEmpty()) return@Canvas
            val start = sorted.first().interval.start
            val end = sorted.maxOf { it.interval.end }
            val totalMs = Duration.between(start, end).toMillis().toFloat().coerceAtLeast(1f)
            val axisH = measurer.measure("0", type.caption).size.height + 6.dp.toPx()
            val gutter = stageRows.maxOf { measurer.measure(stageName(it), type.caption).size.width } + 8.dp.toPx()
            val plotW = size.width - gutter
            val plotH = size.height - axisH
            val rowH = plotH / stageRows.size
            fun x(t: Instant) = gutter + Duration.between(start, t).toMillis() / totalMs * plotW
            fun rowCenter(s: SleepStage) = rowH * (stageRows.indexOf(s).takeIf { it >= 0 } ?: 1) + rowH / 2
            stageRows.forEachIndexed { i, s ->
                drawLine(colors.outlineSubtle, Offset(gutter, rowH * i + rowH / 2), Offset(size.width, rowH * i + rowH / 2), 1.dp.toPx())
                val tm = measurer.measure(stageName(s), type.caption)
                label(measurer, stageName(s), type.caption, colors.onSurfaceMuted, Offset(0f, rowH * i + (rowH - tm.size.height) / 2))
            }
            clipRect(left = 0f, right = gutter + plotW * progress) {
                sorted.forEachIndexed { i, span ->
                    val x0 = x(span.interval.start)
                    val x1 = maxOf(x(span.interval.end), x0 + 2.dp.toPx())
                    if (span.stage == SleepStage.UNKNOWN) {
                        drawRect(colors.outline.copy(alpha = 0.18f), Offset(x0, 0f), Size(x1 - x0, plotH))
                    } else {
                        val bh = rowH * 0.58f
                        drawRoundRect(colorOf(span.stage), Offset(x0, rowCenter(span.stage) - bh / 2), Size(x1 - x0, bh), CornerRadius(3.dp.toPx()))
                    }
                    val next = sorted.getOrNull(i + 1)
                    if (next != null && span.stage != SleepStage.UNKNOWN && next.stage != SleepStage.UNKNOWN && next.stage != span.stage) {
                        drawLine(colors.onSurfaceMuted.copy(alpha = 0.5f), Offset(x1, rowCenter(span.stage)), Offset(x1, rowCenter(next.stage)), 1.dp.toPx())
                    }
                }
            }
            val sample = measurer.measure(DriftFormat.clockMinutes(23 * 60 + 59, use24h), type.caption).size.width.toFloat()
            val spanMin = Duration.between(start, end).toMinutes().toFloat()
            val step = axisStepMinutes(spanMin, sample, plotW)
            val z = ZonedDateTime.ofInstant(start, zone)
            var tick = ((z.hour * 60 + z.minute + step - 1) / step) * step - (z.hour * 60 + z.minute)
            while (tick <= spanMin) {
                val t = start.plusSeconds(tick * 60L)
                val text = DriftFormat.clock(t, zone, use24h)
                val tm = measurer.measure(text, type.caption)
                label(measurer, text, type.caption, colors.onSurfaceMuted, Offset((x(t) - tm.size.width / 2f).coerceIn(gutter, size.width - tm.size.width), plotH + 4.dp.toPx()))
                tick += step
            }
        }
    }
}
