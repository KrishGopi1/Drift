package app.drift.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.designsystem.DriftTheme
import app.drift.model.repository.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base for screenshot tests. [shoot] renders the content light/dark at font scale 1.0 and 2.0 and writes
 * `src/test/screenshots/<name>_<theme>_<scale>.png` when run with `-Proborazzi.test.record=true`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
abstract class ShotTest {
    @get:Rule val rule = createComposeRule()

    protected fun shoot(name: String, content: @Composable () -> Unit) {
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var scale by mutableFloatStateOf(1f)
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                DriftTheme(mode, reducedMotion = true) {
                    Box(Modifier.size(360.dp, 800.dp).background(DriftTheme.colors.background)) { content() }
                }
            }
        }
        for ((m, s, tag) in listOf(
            Triple(ThemeMode.LIGHT, 1f, "light_1x"), Triple(ThemeMode.DARK, 1f, "dark_1x"),
            Triple(ThemeMode.LIGHT, 2f, "light_2x"), Triple(ThemeMode.DARK, 2f, "dark_2x"),
        )) {
            rule.runOnIdle { mode = m; scale = s }
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("src/test/screenshots/${name}_$tag.png")
        }
    }
}
