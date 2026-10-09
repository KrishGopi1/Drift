package app.drift.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.drift.designsystem.DriftTheme
import java.time.Duration
import java.util.Locale

/** Pure helpers for [DebtGauge]. */
object DebtGaugeMath {
    /** Debt that fills the whole arc. */
    val FULL_SCALE: Duration = Duration.ofHours(12)

    /** 0..1 share of the arc for [debt]; never negative, capped at 1. */
    fun fraction(debt: Duration): Float =
        (debt.toMinutes().toFloat() / FULL_SCALE.toMinutes()).coerceIn(0f, 1f)

    /** "6.3" style hours with one decimal in [locale]. */
    fun hoursText(debt: Duration, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f", debt.toMinutes().coerceAtLeast(0) / 60.0)
}

/**
 * Sleep debt as a gauge (docs/ux/visual-refresh.md): a rounded panel with three concentric arcs bulging out of its
 * right edge, the innermost one filled from the bottom in proportion to [debt] (12 h fills it), and a disc that
 * overlaps the panel's edge holding the number in hours, [unit] and [label]. One TalkBack node reads [contentDescription].
 */
@Composable
fun DebtGauge(
    debt: Duration,
    label: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    unit: String = "h",
    height: Dp = 260.dp,
    disc: Dp = 168.dp,
) {
    val colors = DriftTheme.colors
    val type = DriftTheme.typography
    val progress by rememberDrawIn(debt)
    val share = DebtGaugeMath.fraction(debt)
    Box(
        modifier.fillMaxWidth().height(height).clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.CenterEnd,
    ) {
        val measurer = rememberTextMeasurer()
        Canvas(Modifier.fillMaxSize()) {
            val panelRight = size.width * 0.74f
            val corner = CornerRadius(24.dp.toPx())
            drawRoundRect(colors.surface, Offset.Zero, Size(panelRight, size.height), corner)
            val top = 18.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            val baseX = 28.dp.toPx()
            // Where the curved edge's middle sits for a given share of the 12 h scale: 0 h is a straight edge near the
            // left, 12 h bulges most of the way across the panel.
            fun apex(f: Float) = baseX + 16.dp.toPx() + f * (panelRight - baseX - 40.dp.toPx())
            fun edge(f: Float) = Path().apply {
                moveTo(baseX, top)
                quadraticTo(2 * apex(f) - baseX, (top + bottom) / 2, baseX, bottom)
            }
            // Scale: faint edges at 5 h and 10 h, labelled.
            listOf(5, 10).forEach { h ->
                val f = h / 12f
                drawPath(edge(f), colors.outlineSubtle, style = Stroke(1.5.dp.toPx()))
                val text = "${h}h"
                val m = measurer.measure(text, type.caption)
                // Label near the top of the edge (clear of the number disc): the quadratic at t = 0.1.
                val t = 0.1f
                val lx = baseX * ((1 - t) * (1 - t) + t * t) + 2 * (1 - t) * t * (2 * apex(f) - baseX)
                val ly = top + t * (bottom - top)
                label(measurer, text, type.caption, colors.onSurfaceMuted, Offset(lx + 6.dp.toPx(), ly - m.size.height / 2f))
            }
            // The debt itself: a filled shape whose edge bulges with the debt.
            val f = share * progress
            val fill = Path().apply {
                moveTo(0f, top)
                lineTo(baseX, top)
                quadraticTo(2 * apex(f) - baseX, (top + bottom) / 2, baseX, bottom)
                lineTo(0f, bottom)
                close()
            }
            val panel = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, panelRight, size.height, corner))
            }
            clipPath(panel) { drawPath(fill, colors.surfaceRaised) }
            val brush = Brush.verticalGradient(listOf(colors.windDownFill, colors.energyFill), top, bottom)
            drawPath(edge(f), if (share == 0f) Brush.verticalGradient(listOf(colors.success, colors.success)) else brush, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(
            Modifier.size(disc).background(colors.background, CircleShape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(DebtGaugeMath.hoursText(debt), style = type.hero, color = colors.onSurface)
                Text(unit, style = type.unit, color = colors.onSurface, modifier = Modifier.padding(bottom = 10.dp))
            }
            Text(label, style = type.titleSmall, color = colors.onSurface)
        }
    }
}
