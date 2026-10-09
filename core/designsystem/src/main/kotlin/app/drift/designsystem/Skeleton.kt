package app.drift.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Grey block with a slow highlight sweep (1400 ms, linear). Static under reduced motion. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(DriftTheme.radii.sm)) {
    val colors = DriftTheme.colors
    val reduced = DriftTheme.reducedMotion
    val base = colors.surfaceRaised
    val highlight = colors.outlineSubtle
    val sweep = if (reduced) 0.5f else {
        val t = rememberInfiniteTransition(label = "skeleton")
        val v by t.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(DriftTheme.motion.shimmer, easing = LinearEasing), RepeatMode.Restart),
            label = "sweep",
        )
        v
    }
    Box(
        modifier
            .clip(shape)
            .background(base)
            .drawWithContent {
                val w = size.width
                val x = -w * 0.5f + sweep * w * 2f
                drawRect(
                    Brush.linearGradient(
                        listOf(base, highlight.copy(alpha = if (reduced) 0f else 0.9f), base),
                        start = Offset(x - w * 0.3f, 0f), end = Offset(x + w * 0.3f, 0f),
                    ),
                )
            },
    )
}

/** Card-shaped skeleton with title and body lines. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier, height: Dp = 120.dp) {
    DriftCard(modifier = modifier.clearAndSetSemantics {}) {
        SkeletonBox(Modifier.fillMaxWidth(0.4f).height(14.dp))
        SkeletonBox(Modifier.fillMaxWidth(0.6f).height(36.dp))
        SkeletonBox(Modifier.fillMaxWidth().height((height - 80.dp).coerceAtLeast(12.dp)))
    }
}

/** Whole-screen loading state: a chart-sized card and two metric cards, announced as "Loading". */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.ds_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = loading; progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sectionGap),
    ) {
        SkeletonCard(height = 240.dp)
        SkeletonCard(height = 120.dp)
        SkeletonCard(height = 120.dp)
    }
}
