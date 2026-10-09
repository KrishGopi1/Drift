package app.drift.feature.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.drift.designsystem.DriftTheme
import app.drift.model.repository.ThemeMode
import app.drift.testing.fixtures.FixtureContext
import com.github.takahirom.roborazzi.captureRoboImage

/** One light/dark and font-scale combination of a screenshot. */
data class Combo(val mode: ThemeMode, val fontScale: Float) {
    val tag: String get() = (if (mode == ThemeMode.LIGHT) "light" else "dark") + "_" + (if (fontScale > 1f) "2x" else "1x")

    companion object {
        val ALL = listOf(Combo(ThemeMode.LIGHT, 1f), Combo(ThemeMode.DARK, 1f), Combo(ThemeMode.LIGHT, 2f), Combo(ThemeMode.DARK, 2f))
        val LIGHT_1X = Combo(ThemeMode.LIGHT, 1f)
        val DARK_2X = Combo(ThemeMode.DARK, 2f)
        val EDGES = listOf(LIGHT_1X, DARK_2X)
    }
}

private fun FixtureContext.tag() = if (zone.id.startsWith("Asia")) "ist" else "us"

/** Renders [content] through every [combo] in one composition and writes `src/test/screenshots/<name>_<zone>_<combo>.png`. */
fun ComposeContentTestRule.shots(
    name: String,
    ctx: FixtureContext,
    combos: List<Combo> = Combo.ALL,
    content: @Composable () -> Unit,
) {
    var current by mutableStateOf(combos.first())
    setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, current.fontScale)) {
            DriftTheme(current.mode, reducedMotion = true) {
                Box(Modifier.width(360.dp).background(DriftTheme.colors.background)) { content() }
            }
        }
    }
    combos.forEach { combo ->
        runOnIdle { current = combo }
        waitForIdle()
        onRoot().captureRoboImage("src/test/screenshots/${name}_${ctx.tag()}_${combo.tag}.png")
    }
}

/** Plain themed content for behaviour tests. */
fun ComposeContentTestRule.themed(content: @Composable () -> Unit) {
    setContent {
        DriftTheme(ThemeMode.DARK, reducedMotion = true) {
            Box(Modifier.background(DriftTheme.colors.background)) { content() }
        }
    }
}
