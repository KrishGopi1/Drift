package app.drift.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Manrope (SIL OFL 1.1), static instances at 400/500/600/700. License: core/designsystem/OFL.txt. */
val DriftFontFamily: FontFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

/** Type scale (sp). Line heights are in sp so they grow with the system font scale. */
@Immutable
class DriftTypography(
    /** Hero metric number, tabular figures. */
    val hero: TextStyle = style(60, 64, FontWeight.Bold, tabular = true, tracking = -1.0),
    val display: TextStyle = style(40, 46, FontWeight.Bold, tabular = true, tracking = -0.5),
    val headline: TextStyle = style(28, 34, FontWeight.SemiBold),
    val title: TextStyle = style(20, 26, FontWeight.SemiBold),
    val titleSmall: TextStyle = style(16, 22, FontWeight.SemiBold),
    val body: TextStyle = style(16, 24, FontWeight.Normal),
    val bodySmall: TextStyle = style(14, 20, FontWeight.Normal),
    val label: TextStyle = style(14, 20, FontWeight.Medium),
    val caption: TextStyle = style(12, 16, FontWeight.Medium, tracking = 0.2),
    /** Unit next to a hero number. */
    val unit: TextStyle = style(20, 26, FontWeight.Medium),
) {
    companion object {
        private fun style(
            size: Int, line: Int, weight: FontWeight, tabular: Boolean = false, tracking: Double = 0.0,
        ) = TextStyle(
            fontFamily = DriftFontFamily,
            fontSize = size.sp,
            lineHeight = line.sp,
            fontWeight = weight,
            letterSpacing = tracking.sp,
            fontFeatureSettings = if (tabular) "tnum" else null,
        )
    }
}

internal fun DriftTypography.toMaterial(): Typography = Typography(
    displayLarge = hero, displayMedium = display, displaySmall = display,
    headlineLarge = headline, headlineMedium = headline, headlineSmall = title,
    titleLarge = title, titleMedium = titleSmall, titleSmall = titleSmall,
    bodyLarge = body, bodyMedium = body, bodySmall = bodySmall,
    labelLarge = label, labelMedium = label, labelSmall = caption,
)
