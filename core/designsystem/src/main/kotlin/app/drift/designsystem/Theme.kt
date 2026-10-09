package app.drift.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ProvideTextStyle
import app.drift.model.repository.ThemeMode

val LocalDriftColors = staticCompositionLocalOf { DriftDarkColors }
val LocalDriftTypography = staticCompositionLocalOf { DriftTypography() }
val LocalDriftSpacing = staticCompositionLocalOf { DriftSpacing() }
val LocalDriftRadii = staticCompositionLocalOf { DriftRadii() }
val LocalDriftElevation = staticCompositionLocalOf { DriftElevation() }
val LocalDriftStrokes = staticCompositionLocalOf { DriftStrokes() }
val LocalDriftMotion = staticCompositionLocalOf { DriftMotion() }
val LocalReducedMotion = compositionLocalOf { false }

/** Accessors for the current theme tokens: `DriftTheme.colors.energy`, `DriftTheme.spacing.lg`, ... */
object DriftTheme {
    val colors: DriftColors @Composable @ReadOnlyComposable get() = LocalDriftColors.current
    val typography: DriftTypography @Composable @ReadOnlyComposable get() = LocalDriftTypography.current
    val spacing: DriftSpacing @Composable @ReadOnlyComposable get() = LocalDriftSpacing.current
    val radii: DriftRadii @Composable @ReadOnlyComposable get() = LocalDriftRadii.current
    val elevation: DriftElevation @Composable @ReadOnlyComposable get() = LocalDriftElevation.current
    val strokes: DriftStrokes @Composable @ReadOnlyComposable get() = LocalDriftStrokes.current
    val motion: DriftMotion @Composable @ReadOnlyComposable get() = LocalDriftMotion.current
    val reducedMotion: Boolean @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}

/**
 * Wraps [MaterialTheme] with the Drift colour scheme and type scale and provides all token locals.
 * [themeMode] comes from `AppSettings.theme`. [reducedMotion] defaults to the system animator scale.
 */
@Composable
fun DriftTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    reducedMotion: Boolean = rememberReducedMotion(),
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DriftDarkColors else DriftLightColors
    val typography = DriftTypography()
    val radii = DriftRadii()
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(radii.xs),
        small = RoundedCornerShape(radii.sm),
        medium = RoundedCornerShape(radii.md),
        large = RoundedCornerShape(radii.lg),
        extraLarge = RoundedCornerShape(radii.sheet),
    )
    CompositionLocalProvider(
        LocalDriftColors provides colors,
        LocalDriftTypography provides typography,
        LocalDriftRadii provides radii,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(colorScheme = colors.toMaterial(), typography = typography.toMaterial(), shapes = shapes) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                ProvideTextStyle(typography.body, content)
            }
        }
    }
}
