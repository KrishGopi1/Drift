package app.drift.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class CardTone { Default, Raised, Accent }

/**
 * Standard card. Pass [onClick] to make the whole card one button (role Button, min 48 dp tall);
 * [contentDescription] then replaces the merged child text for screen readers.
 */
@Composable
fun DriftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentDescription: String? = null,
    tone: CardTone = CardTone.Default,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DriftTheme.colors
    val shape = RoundedCornerShape(DriftTheme.radii.lg)
    val container = when (tone) {
        CardTone.Default -> colors.surface
        CardTone.Raised -> colors.surfaceRaised
        CardTone.Accent -> colors.accentSurface
    }
    val base = modifier
        .then(if (!colors.isDark) Modifier.shadow(DriftTheme.elevation.card, shape, clip = false) else Modifier)
        .clip(shape)
        .background(container)
        .then(if (!colors.isDark) Modifier.border(BorderStroke(DriftTheme.strokes.outline, colors.outlineSubtle), shape) else Modifier)
    val interactive = if (onClick != null) {
        base.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .heightIn(min = DriftTheme.spacing.minTouch)
    } else base
    val described = if (contentDescription != null) {
        interactive.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
    } else if (onClick != null) {
        interactive.semantics(mergeDescendants = true) {}
    } else interactive
    Column(
        modifier = described.fillMaxWidth().padding(DriftTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
        content = content,
    )
}

/** Section title row above a group of cards. Announced as a heading. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = DriftTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = DriftTheme.typography.title,
            color = DriftTheme.colors.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke()
    }
}

/**
 * Card entrance: fades in and rises 16 dp over 480 ms (decelerate), each card delayed by 45 ms x index
 * (max 8 steps). No-op under reduced motion.
 */
@Composable
fun Modifier.staggeredEntrance(index: Int): Modifier {
    val reduced = DriftTheme.reducedMotion
    val motion = DriftTheme.motion
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) progress.snapTo(1f)
        else progress.animateTo(1f, motion.spec(false, motion.long, motion.decelerate, motion.staggerDelay(index)))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 16.dp.toPx()
    }
}

/** Plain surface colour helper for custom containers. */
@Composable
fun driftSurfaceColor(tone: CardTone = CardTone.Default): Color = when (tone) {
    CardTone.Default -> DriftTheme.colors.surface
    CardTone.Raised -> DriftTheme.colors.surfaceRaised
    CardTone.Accent -> DriftTheme.colors.accentSurface
}
