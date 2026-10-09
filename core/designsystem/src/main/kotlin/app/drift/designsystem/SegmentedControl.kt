package app.drift.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Single-choice control (e.g. Week / Month / 3 months). Each segment is a RadioButton in a selectable group,
 * at least 48 dp tall, text wraps to two lines at 200% font scale. Selection fades over 160 ms.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = DriftTheme.colors
    // Above 1.3x font scale the segments no longer fit side by side without breaking words, so they stack.
    val stacked = LocalDensity.current.fontScale > 1.3f && options.size > 1
    val outer = RoundedCornerShape(if (stacked) DriftTheme.radii.lg else DriftTheme.radii.pill)
    val container = modifier
        .fillMaxWidth()
        .clip(outer)
        .background(colors.surfaceRaised)
        .padding(DriftTheme.spacing.xs)
        .selectableGroup()
        .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
    val segment: @Composable (Int, String, Modifier) -> Unit = { index, text, mod ->
        Segment(text, index == selectedIndex, { onSelect(index) }, mod)
    }
    if (stacked) {
        Column(container, verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs)) {
            options.forEachIndexed { index, text -> segment(index, text, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(container.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs)) {
            options.forEachIndexed { index, text -> segment(index, text, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = DriftTheme.colors
    val motion = DriftTheme.motion
    val reduced = DriftTheme.reducedMotion
    val bg by animateColorAsState(
        if (selected) colors.primaryContainer else colors.surfaceRaised,
        motion.spec(reduced, motion.short), label = "segment",
    )
    val fg by animateColorAsState(
        if (selected) colors.onPrimaryContainer else colors.onSurfaceMuted,
        motion.spec(reduced, motion.short), label = "segmentText",
    )
    Box(
        modifier = modifier
            .heightIn(min = DriftTheme.spacing.minTouch - 8.dp)
            .clip(RoundedCornerShape(DriftTheme.radii.pill))
            .background(bg)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = DriftTheme.spacing.sm, vertical = DriftTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = DriftTheme.typography.label, color = fg, textAlign = TextAlign.Center, maxLines = 2)
    }
}
