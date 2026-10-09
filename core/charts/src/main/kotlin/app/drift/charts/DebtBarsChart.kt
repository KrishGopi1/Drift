package app.drift.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.drift.designsystem.DriftTheme
import app.drift.model.engine.DailyDebt
import kotlin.math.max

/**
 * Contribution bars per night: up = added to debt, down = repaid. Estimated (imputed) nights are outlined with a
 * dashed edge and a light fill; nights with no data are gaps marked by a small ring on the zero line.
 * Bars grow from the zero line over 480 ms with a 20 ms stagger (instant under reduced motion).
 */
@Composable
fun DebtBarsChart(
    days: List<DailyDebt>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    showLegend: Boolean = true,
    contentDescription: String = remember(days) { debtBarsDescription(days) },
) {
    val colors = DriftTheme.colors
    val type = DriftTheme.typography
    val measurer = rememberTextMeasurer()
    val progress by rememberDrawIn(days.size, DriftTheme.motion.long)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm)) {
        Canvas(Modifier.fillMaxWidth().height(height).semantics { this.contentDescription = contentDescription }) {
            if (days.isEmpty()) return@Canvas
            val axisH = measurer.measure("M", type.caption).size.height + 6.dp.toPx()
            val plotH = size.height - axisH
            val maxUp = max(days.maxOf { it.contribution.seconds.coerceAtLeast(0) }.toFloat(), 1800f)
            val maxDown = max(days.maxOf { (-it.contribution.seconds).coerceAtLeast(0) }.toFloat(), 1800f)
            val zeroY = plotH * maxUp / (maxUp + maxDown)
            val scale = plotH / (maxUp + maxDown)
            val slot = size.width / days.size
            val barW = (slot * 0.62f).coerceAtLeast(3.dp.toPx())
            drawLine(colors.outline, Offset(0f, zeroY), Offset(size.width, zeroY), 1.dp.toPx())
            days.forEachIndexed { i, d ->
                val cx = slot * i + slot / 2
                val own = ((progress * (days.size + 8) - i * 0.6f) / 8f).coerceIn(0f, 1f).let { if (progress >= 1f) 1f else it }
                val missing = d.sleep == null && !d.imputed
                if (missing) {
                    drawCircle(colors.outline, 3.dp.toPx(), Offset(cx, zeroY), style = Stroke(1.5.dp.toPx()))
                } else {
                    val v = d.contribution.seconds.toFloat()
                    val h = (kotlin.math.abs(v) * scale * own).coerceAtLeast(if (v == 0f) 0f else 2.dp.toPx())
                    val c = if (v > 0) colors.debt else colors.repaid
                    val top = if (v > 0) zeroY - h else zeroY
                    val tl = Offset(cx - barW / 2, top)
                    val sz = Size(barW, h)
                    val r = CornerRadius(3.dp.toPx())
                    if (d.imputed) {
                        drawRoundRect(c.copy(alpha = 0.22f), tl, sz, r)
                        drawRoundRect(c, tl, sz, r, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                    } else drawRoundRect(c, tl, sz, r)
                }
                val letter = weekdayInitial(d.date)
                val m = measurer.measure(letter, type.caption)
                label(measurer, letter, type.caption, colors.onSurfaceMuted, Offset(cx - m.size.width / 2f, plotH + 4.dp.toPx()))
            }
        }
        if (showLegend) {
            FlowRow(
                Modifier.clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
            ) {
                LegendItem("Added to debt", colors.debt, LegendShape.Solid)
                LegendItem("Repaid", colors.repaid, LegendShape.Solid)
                if (days.any { it.imputed }) LegendItem("Estimated night", colors.onSurfaceMuted, LegendShape.Dashed)
                if (days.any { it.sleep == null && !it.imputed }) LegendItem("No data", colors.outline, LegendShape.Ring)
            }
        }
    }
}

internal enum class LegendShape { Solid, Dashed, Ring, Line }

@Composable
internal fun LegendItem(text: String, color: Color, shape: LegendShape) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs + 2.dp)) {
        Canvas(Modifier.size(12.dp)) {
            when (shape) {
                LegendShape.Solid -> drawRoundRect(color, cornerRadius = CornerRadius(3.dp.toPx()))
                LegendShape.Dashed -> drawRoundRect(color, cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))))
                LegendShape.Ring -> drawCircle(color, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                LegendShape.Line -> drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.5.dp.toPx())
            }
        }
        Text(text, style = DriftTheme.typography.caption, color = DriftTheme.colors.onSurfaceMuted)
    }
}
