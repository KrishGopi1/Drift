package app.drift.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale in dp (4-pt base). See docs/ux/tokens.md. */
@Immutable
data class DriftSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    /** Horizontal screen gutter. */
    val gutter: Dp = 20.dp,
    /** Inner padding of a card. */
    val cardPadding: Dp = 16.dp,
    /** Vertical gap between cards / sections on a screen. */
    val sectionGap: Dp = 16.dp,
    /** Minimum touch target edge. */
    val minTouch: Dp = 48.dp,
)

/** Corner radii in dp. */
@Immutable
data class DriftRadii(
    val xs: Dp = 6.dp,
    val sm: Dp = 10.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val sheet: Dp = 28.dp,
    val pill: Dp = 999.dp,
)

/** Elevation in dp. Dark theme prefers tonal surfaces and uses elevation only for sheets. */
@Immutable
data class DriftElevation(
    val none: Dp = 0.dp,
    val card: Dp = 1.dp,
    val raised: Dp = 3.dp,
    val sheet: Dp = 8.dp,
)

/** Stroke widths for charts and outlines, in dp. */
@Immutable
data class DriftStrokes(
    val hairline: Dp = 1.dp,
    val outline: Dp = 1.dp,
    val chartLine: Dp = 2.5.dp,
    val chartAxis: Dp = 1.dp,
    val focus: Dp = 2.dp,
)
