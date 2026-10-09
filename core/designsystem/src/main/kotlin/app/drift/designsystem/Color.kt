package app.drift.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colour roles for both themes. Palette is original: deep indigo "night sky" surfaces, a periwinkle primary,
 * one warm amber accent for energy peaks and one cool teal for the melatonin window.
 * Contrast ratios are listed in docs/ux/tokens.md and enforced by ContrastTest.
 */
@Immutable
data class DriftColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val outline: Color,
    val outlineSubtle: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    /** Warm accent, text-safe on [surface]. Energy peaks. */
    val energy: Color,
    /** Warm accent for chart fills (not text). */
    val energyFill: Color,
    /** Cool accent, text-safe. Melatonin window. */
    val melatonin: Color,
    val melatoninFill: Color,
    /** Wind-down phase, text-safe. */
    val windDown: Color,
    val windDownFill: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val onError: Color,
    val debt: Color,
    val repaid: Color,
    val stageAwake: Color,
    val stageRem: Color,
    val stageLight: Color,
    val stageDeep: Color,
    val scrim: Color,
) {
    /** Colour used to tint a provenance chip. */
    fun provenance(p: app.drift.model.Provenance): Color = when (p) {
        app.drift.model.Provenance.MEASURED -> success
        app.drift.model.Provenance.DERIVED -> primary
        app.drift.model.Provenance.ESTIMATED -> windDown
        app.drift.model.Provenance.USER_ENTERED -> onSurfaceMuted
    }
}

val DriftDarkColors = DriftColors(
    isDark = true,
    background = Color(0xFF0B1020),
    surface = Color(0xFF131A30),
    surfaceRaised = Color(0xFF1B2440),
    outline = Color(0xFF6A78A8),
    outlineSubtle = Color(0xFF2A3558),
    onSurface = Color(0xFFE9EDF8),
    onSurfaceMuted = Color(0xFFA9B4D0),
    primary = Color(0xFF9BAEFF),
    onPrimary = Color(0xFF0B1230),
    primaryContainer = Color(0xFF26325E),
    onPrimaryContainer = Color(0xFFDDE3FF),
    energy = Color(0xFFFFB84D),
    energyFill = Color(0xFFFFB84D),
    melatonin = Color(0xFF55D1DE),
    melatoninFill = Color(0xFF55D1DE),
    windDown = Color(0xFFC3A6FF),
    windDownFill = Color(0xFFC3A6FF),
    success = Color(0xFF6FD9A0),
    warning = Color(0xFFFFB84D),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF2B0A0A),
    debt = Color(0xFFFF9272),
    repaid = Color(0xFF6FD9A0),
    stageAwake = Color(0xFFFFB07A),
    stageRem = Color(0xFFC3A6FF),
    stageLight = Color(0xFF7FA1FF),
    stageDeep = Color(0xFF4A63D6),
    scrim = Color(0xFF000000),
)

val DriftLightColors = DriftColors(
    isDark = false,
    background = Color(0xFFF5F6FB),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFEEF0F8),
    outline = Color(0xFF7A84A3),
    outlineSubtle = Color(0xFFD5DAEA),
    onSurface = Color(0xFF12182B),
    onSurfaceMuted = Color(0xFF4B5472),
    primary = Color(0xFF3547C9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E5FF),
    onPrimaryContainer = Color(0xFF1A2680),
    energy = Color(0xFF9A5200),
    energyFill = Color(0xFFF5A524),
    melatonin = Color(0xFF006C7A),
    melatoninFill = Color(0xFF2BB5C6),
    windDown = Color(0xFF6A3FC4),
    windDownFill = Color(0xFF9B7BE6),
    success = Color(0xFF1B7A48),
    warning = Color(0xFF9A5200),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    debt = Color(0xFFB8481F),
    repaid = Color(0xFF1B7A48),
    stageAwake = Color(0xFFD9822B),
    stageRem = Color(0xFF8A63D8),
    stageLight = Color(0xFF6F8CF0),
    stageDeep = Color(0xFF2E3FA8),
    scrim = Color(0xFF000000),
)

internal fun DriftColors.toMaterial(): ColorScheme =
    if (isDark) {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            secondary = melatonin, onSecondary = background,
            tertiary = energy, onTertiary = background,
            background = background, onBackground = onSurface,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceRaised, onSurfaceVariant = onSurfaceMuted,
            surfaceContainer = surface, surfaceContainerHigh = surfaceRaised, surfaceContainerHighest = surfaceRaised,
            outline = outline, outlineVariant = outlineSubtle,
            error = error, onError = onError, scrim = scrim,
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            secondary = melatonin, onSecondary = Color.White,
            tertiary = energy, onTertiary = Color.White,
            background = background, onBackground = onSurface,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceRaised, onSurfaceVariant = onSurfaceMuted,
            surfaceContainer = surface, surfaceContainerHigh = surfaceRaised, surfaceContainerHighest = surfaceRaised,
            outline = outline, outlineVariant = outlineSubtle,
            error = error, onError = onError, scrim = scrim,
        )
    }
