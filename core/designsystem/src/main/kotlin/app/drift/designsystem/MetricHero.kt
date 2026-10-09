package app.drift.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import app.drift.model.Provenance
import java.time.Duration
import kotlin.math.roundToInt

/**
 * Big number with unit and label, e.g. sleep need "7.8 h". The number counts up from 0 on first show (900 ms,
 * emphasized easing) unless reduced motion is on. Screen readers get one stable sentence, never the animated digits.
 */
@Composable
fun MetricHero(
    value: Double,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    spokenValue: String? = null,
    provenance: Provenance? = null,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val animated by rememberCountUp(value.toFloat())
    val spoken = spokenValue ?: (DriftFormat.decimal(value, decimals) + " " + unit)
    HeroLayout(
        label = label,
        provenance = provenance,
        supporting = supporting,
        spoken = spoken,
        modifier = modifier,
        onClick = onClick,
        onClickLabel = onClickLabel,
    ) {
        Text(DriftFormat.decimal(animated.toDouble(), decimals), style = DriftTheme.typography.hero, color = DriftTheme.colors.onSurface, modifier = Modifier.alignByBaseline())
        Text(unit, style = DriftTheme.typography.unit, color = DriftTheme.colors.onSurfaceMuted, modifier = Modifier.alignByBaseline())
    }
}

/** Hero for a duration, drawn as "7 h 20 min" with the number parts large. Counts up in minutes. */
@Composable
fun DurationHero(
    duration: Duration,
    label: String,
    modifier: Modifier = Modifier,
    provenance: Provenance? = null,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val totalMinutes = (duration.seconds / 60.0).roundToInt()
    val animated by rememberCountUp(totalMinutes.toFloat())
    val shown = animated.roundToInt().coerceAtLeast(0)
    val h = shown / 60
    val m = shown % 60
    val colors = DriftTheme.colors
    val type = DriftTheme.typography
    HeroLayout(
        label = label,
        provenance = provenance,
        supporting = supporting,
        spoken = DriftFormat.durationSpoken(duration),
        modifier = modifier,
        onClick = onClick,
        onClickLabel = onClickLabel,
    ) {
        if (h > 0 || totalMinutes >= 60) {
            Text(h.toString(), style = type.hero, color = colors.onSurface, modifier = Modifier.alignByBaseline())
            Text("h", style = type.unit, color = colors.onSurfaceMuted, modifier = Modifier.alignByBaseline())
        }
        Text(m.toString(), style = type.hero, color = colors.onSurface, modifier = Modifier.alignByBaseline())
        Text("min", style = type.unit, color = colors.onSurfaceMuted, modifier = Modifier.alignByBaseline())
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HeroLayout(
    label: String,
    provenance: Provenance?,
    supporting: String?,
    spoken: String,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    onClickLabel: String?,
    number: @Composable RowScope.() -> Unit,
) {
    val provenanceText = provenance?.let { stringResource(it.labelRes()) }
    val description = buildString {
        append(label).append(": ").append(spoken)
        if (provenanceText != null) append(", ").append(provenanceText)
        if (supporting != null) append(". ").append(supporting)
    }
    val base = modifier.fillMaxWidth()
    val clickable = if (onClick != null) {
        base.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick).heightIn(min = DriftTheme.spacing.minTouch)
    } else base
    Column(
        modifier = clickable.semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            Text(label, style = DriftTheme.typography.label, color = DriftTheme.colors.onSurfaceMuted, modifier = Modifier.align(Alignment.CenterVertically))
            if (provenance != null) ProvenanceChip(provenance)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs + 2.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.clearAndSetSemantics {},
        ) { number() }
        if (supporting != null) {
            Text(supporting, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}
