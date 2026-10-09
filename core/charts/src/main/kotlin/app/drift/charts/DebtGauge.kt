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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.drift.designsystem.DriftTheme
import java.time.Duration
import java.util.Locale
import kotlin.math.asin

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
        Canvas(Modifier.fillMaxSize()) {
            val panelRight = size.width * 0.72f
            drawRoundRect(colors.surfaceRaised, Offset.Zero, Size(panelRight, size.height), CornerRadius(24.dp.toPx()))
            val half = size.height / 2f
            val reach = half - 20.dp.toPx()
            // Arcs are wide bulges: each apex sits [sag] to the right of where it meets the top and bottom.
            val sag = 56.dp.toPx()
            val r = (reach * reach + sag * sag) / (2 * sag)
            val theta = Math.toDegrees(asin((reach / r).toDouble())).toFloat()
            val apex0 = size.width * 0.44f
            fun arc(apex: Float) = Offset(apex - 2 * r, half - r)
            val box = Size(2 * r, 2 * r)
            listOf(0f, 40.dp.toPx(), 80.dp.toPx()).forEach { extra ->
                drawArc(colors.outlineSubtle, -theta, 2 * theta, useCenter = false, topLeft = arc(apex0 + extra), size = box, style = Stroke(1.5.dp.toPx()))
            }
            val stroke = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
            if (share == 0f) {
                drawArc(colors.success, theta, -6f, useCenter = false, topLeft = arc(apex0), size = box, style = stroke)
            } else {
                val brush = Brush.verticalGradient(listOf(colors.windDownFill, colors.energyFill), half - reach, half + reach)
                drawArc(brush, theta, -2 * theta * share * progress, useCenter = false, topLeft = arc(apex0), size = box, style = stroke)
            }
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
