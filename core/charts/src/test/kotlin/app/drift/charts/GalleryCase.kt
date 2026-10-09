package app.drift.charts

import androidx.compose.foundation.background
import app.drift.designsystem.DriftTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.model.repository.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Component gallery base: each subclass supplies [Content]; four tests render it light/dark at font scale 1.0/2.0 and
 * write `src/test/screenshots/<name>_<theme>_<scale>.png` when run with `-Proborazzi.test.record=true`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
abstract class GalleryCase(private val name: String) {
    @get:Rule val rule = createComposeRule()

    @Composable abstract fun Content()

    @Test fun light1x() = shot(ThemeMode.LIGHT, 1f, "light_1x")
    @Test fun dark1x() = shot(ThemeMode.DARK, 1f, "dark_1x")
    @Test fun light2x() = shot(ThemeMode.LIGHT, 2f, "light_2x")
    @Test fun dark2x() = shot(ThemeMode.DARK, 2f, "dark_2x")

    private fun shot(mode: ThemeMode, fontScale: Float, tag: String) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                DriftTheme(mode, reducedMotion = true) {
                    Box(Modifier.width(360.dp).background(DriftTheme.colors.background).padding(16.dp)) { Content() }
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/${name}_$tag.png")
    }
}
