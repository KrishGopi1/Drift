package app.drift.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.drift.model.ConfidenceLevel
import app.drift.model.Quantity
import java.time.Duration

fun ConfidenceLevel.labelRes(): Int = when (this) {
    ConfidenceLevel.LOW -> R.string.ds_confidence_low
    ConfidenceLevel.MEDIUM -> R.string.ds_confidence_medium
    ConfidenceLevel.HIGH -> R.string.ds_confidence_high
}

/** Pure text for the band, e.g. "+/- 25 min (90% range 7 h 5 min to 7 h 55 min)". Unit-tested. */
fun confidenceBandText(halfWidthText: String, lowerText: String, upperText: String, coverage: Double?): String {
    val pct = coverage?.let { "${(it * 100).toInt()}% range " } ?: "range "
    return "± $halfWidthText ($pct$lowerText to $upperText)"
}

/**
 * Value with its uncertainty: a track showing the plausible range, a tick at the value, and a text line
 * "value +/- band" with the confidence level. Positions are relative to [scaleMin]..[scaleMax].
 */
@Composable
fun ConfidenceBand(
    value: Double,
    lower: Double,
    upper: Double,
    scaleMin: Double,
    scaleMax: Double,
    valueText: String,
    bandText: String,
    level: ConfidenceLevel,
    modifier: Modifier = Modifier,
) {
    val colors = DriftTheme.colors
    val levelText = stringResource(level.labelRes())
    val span = (scaleMax - scaleMin).takeIf { it > 0 } ?: 1.0
    fun frac(v: Double) = ((v - scaleMin) / span).coerceIn(0.0, 1.0).toFloat()
    Column(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "$valueText $bandText, $levelText"
        },
        verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
    ) {
        Canvas(Modifier.fillMaxWidth().height(16.dp).clearAndSetSemantics {}) {
            val trackH = 6.dp.toPx()
            val y = (size.height - trackH) / 2
            drawRoundRect(colors.outlineSubtle, Offset(0f, y), Size(size.width, trackH), CornerRadius(trackH / 2))
            val x0 = frac(lower) * size.width
            val x1 = frac(upper) * size.width
            val bandW = maxOf(x1 - x0, trackH)
            val bandColor = colors.primary.copy(alpha = 0.45f)
            drawRoundRect(bandColor, Offset(x0, y), Size(bandW, trackH), CornerRadius(trackH / 2))
            val cx = frac(value) * size.width
            drawCircle(colors.onSurface, radius = 7.dp.toPx(), center = Offset(cx, size.height / 2))
            drawCircle(colors.primary, radius = 4.5.dp.toPx(), center = Offset(cx, size.height / 2))
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xxs),
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            Text(valueText, style = DriftTheme.typography.titleSmall, color = colors.onSurface)
            Text(bandText, style = DriftTheme.typography.bodySmall, color = colors.onSurfaceMuted)
            Text(levelText, style = DriftTheme.typography.label, color = colors.primary)
        }
    }
}

/**
 * Convenience for an engine `Quantity<Duration>`. Renders nothing for values without a confidence band
 * (only ESTIMATED values carry one). [scale] defaults to the band padded by half its width on each side.
 */
@Composable
fun DurationConfidenceBand(
    quantity: Quantity<Duration>,
    modifier: Modifier = Modifier,
    scale: ClosedRange<Duration>? = null,
) {
    val c = quantity.confidence ?: return
    val half = Duration.ofSeconds((c.upper.seconds - c.lower.seconds) / 2)
    val pad = maxOf(half.seconds, 600L)
    val min = scale?.start ?: c.lower.minusSeconds(pad)
    val max = scale?.endInclusive ?: c.upper.plusSeconds(pad)
    ConfidenceBand(
        value = quantity.value.seconds.toDouble(),
        lower = c.lower.seconds.toDouble(),
        upper = c.upper.seconds.toDouble(),
        scaleMin = min.seconds.toDouble(),
        scaleMax = max.seconds.toDouble(),
        valueText = DriftFormat.duration(quantity.value),
        bandText = confidenceBandText(DriftFormat.duration(half), DriftFormat.duration(c.lower), DriftFormat.duration(c.upper), c.coverage),
        level = c.level,
        modifier = modifier,
    )
}
