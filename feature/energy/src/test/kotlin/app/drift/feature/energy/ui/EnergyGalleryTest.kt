package app.drift.feature.energy.ui

import androidx.compose.foundation.background
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
import app.drift.designsystem.DriftTheme
import app.drift.feature.energy.EnergyUiModel
import app.drift.feature.energy.pendingEpisode
import app.drift.feature.energy.readyModel
import app.drift.model.repository.ThemeMode
import app.drift.model.ui.ScreenState
import app.drift.testing.fixtures.FixtureContext
import app.drift.testing.fixtures.ScreenStateFixtures
import app.drift.testing.fixtures.SnapshotFixtures
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders [Content] light/dark at font scale 1x/2x into src/test/screenshots/<name>_<theme>_<scale>.png. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h7000dp-xhdpi")
abstract class EnergyGalleryCase(private val name: String) {
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
                    Box(Modifier.width(360.dp).background(DriftTheme.colors.background).padding(bottom = 8.dp)) { Content() }
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/${name}_$tag.png")
    }
}

private fun model(snap: app.drift.model.engine.AnalysisSnapshot, c: FixtureContext, kind: String): EnergyUiModel = when (kind) {
    "evening" -> readyModel(snap, c, now = c.today.atTime(18, 30).atZone(c.zone).toInstant())
    "pending" -> readyModel(snap, c, pending = pendingEpisode(c), checkInDue = true)
    else -> readyModel(snap, c)
}

@Composable
private fun Screen(state: ScreenState<EnergyUiModel>) = EnergyScreen(state, EnergyActions(), scrollable = false)

class Energy_full_ist_Gallery : EnergyGalleryCase("energy_full_ist") {
    @Composable override fun Content() {
        val c = FixtureContext.IST
        Screen(ScreenState.Ready(model(SnapshotFixtures.full(c), c, "")))
    }
}

class Energy_priors_ist_Gallery : EnergyGalleryCase("energy_priors_ist") {
    @Composable override fun Content() {
        val c = FixtureContext.IST
        Screen(ScreenState.Ready(model(SnapshotFixtures.priorsOnlyDay0(c), c, "")))
    }
}

class Energy_shift_ist_Gallery : EnergyGalleryCase("energy_shift_ist") {
    @Composable override fun Content() {
        val c = FixtureContext.IST
        Screen(ScreenState.Ready(model(SnapshotFixtures.shiftWork(c), c, "evening")))
    }
}

class Energy_travel_ist_Gallery : EnergyGalleryCase("energy_travel_ist") {
    @Composable override fun Content() {
        val c = FixtureContext.IST
        Screen(ScreenState.Ready(model(SnapshotFixtures.travelEastward(c), c, "")))
    }
}

class Energy_flat_ist_Gallery : EnergyGalleryCase("energy_flat_ist") {
    @Composable override fun Content() {
        val c = FixtureContext.IST
        Screen(ScreenState.Ready(model(SnapshotFixtures.flatCurve(c), c, "")))
    }
}

class Energy_nap_ist_Gallery : EnergyGalleryCase("energy_nap_ist") {
    @Composable override fun Content() {
        val c = FixtureContext.IST
        Screen(ScreenState.Ready(model(SnapshotFixtures.withNap(c), c, "")))
    }
}

class Energy_tradeoff_ist_Gallery : EnergyGalleryCase("energy_tradeoff_ist") {
    @Composable override fun Content() {
        val c = FixtureContext.IST
        Screen(ScreenState.Ready(model(SnapshotFixtures.withTradeOff(c), c, "")))
    }
}

class Energy_pending_ist_Gallery : EnergyGalleryCase("energy_pending_ist") {
    @Composable override fun Content() {
        val c = FixtureContext.IST
        Screen(ScreenState.Ready(model(SnapshotFixtures.full(c), c, "pending")))
    }
}

class Energy_adjust_ist_Gallery : EnergyGalleryCase("energy_adjust_ist") {
    @Composable override fun Content() {
        val c = FixtureContext.IST
        AdjustSleepSheetBody(pendingEpisode(c), TimeFormat(c.zone, true), { _, _ -> }, { })
    }
}

class Energy_full_us_Gallery : EnergyGalleryCase("energy_full_us") {
    @Composable override fun Content() {
        val c = FixtureContext.US_EASTERN
        Screen(ScreenState.Ready(model(SnapshotFixtures.full(c), c, "")))
    }
}

class Energy_priors_us_Gallery : EnergyGalleryCase("energy_priors_us") {
    @Composable override fun Content() {
        val c = FixtureContext.US_EASTERN
        Screen(ScreenState.Ready(model(SnapshotFixtures.priorsOnlyDay0(c), c, "")))
    }
}

class Energy_shift_us_Gallery : EnergyGalleryCase("energy_shift_us") {
    @Composable override fun Content() {
        val c = FixtureContext.US_EASTERN
        Screen(ScreenState.Ready(model(SnapshotFixtures.shiftWork(c), c, "evening")))
    }
}

class Energy_travel_us_Gallery : EnergyGalleryCase("energy_travel_us") {
    @Composable override fun Content() {
        val c = FixtureContext.US_EASTERN
        Screen(ScreenState.Ready(model(SnapshotFixtures.travelEastward(c), c, "")))
    }
}

class Energy_flat_us_Gallery : EnergyGalleryCase("energy_flat_us") {
    @Composable override fun Content() {
        val c = FixtureContext.US_EASTERN
        Screen(ScreenState.Ready(model(SnapshotFixtures.flatCurve(c), c, "")))
    }
}

class Energy_nap_us_Gallery : EnergyGalleryCase("energy_nap_us") {
    @Composable override fun Content() {
        val c = FixtureContext.US_EASTERN
        Screen(ScreenState.Ready(model(SnapshotFixtures.withNap(c), c, "")))
    }
}

class Energy_tradeoff_us_Gallery : EnergyGalleryCase("energy_tradeoff_us") {
    @Composable override fun Content() {
        val c = FixtureContext.US_EASTERN
        Screen(ScreenState.Ready(model(SnapshotFixtures.withTradeOff(c), c, "")))
    }
}

class Energy_pending_us_Gallery : EnergyGalleryCase("energy_pending_us") {
    @Composable override fun Content() {
        val c = FixtureContext.US_EASTERN
        Screen(ScreenState.Ready(model(SnapshotFixtures.full(c), c, "pending")))
    }
}

class Energy_adjust_us_Gallery : EnergyGalleryCase("energy_adjust_us") {
    @Composable override fun Content() {
        val c = FixtureContext.US_EASTERN
        AdjustSleepSheetBody(pendingEpisode(c), TimeFormat(c.zone, true), { _, _ -> }, { })
    }
}

class Energy_loading_Gallery : EnergyGalleryCase("energy_state_loading") {
    @Composable override fun Content() = Screen(ScreenStateFixtures.all(model(SnapshotFixtures.full(), FixtureContext.IST, ""))["loading"]!!)
}

class Energy_first_use_Gallery : EnergyGalleryCase("energy_state_first_use") {
    @Composable override fun Content() = Screen(ScreenStateFixtures.all(model(SnapshotFixtures.full(), FixtureContext.IST, ""))["first_use"]!!)
}

class Energy_insufficient_Gallery : EnergyGalleryCase("energy_state_insufficient") {
    @Composable override fun Content() = Screen(ScreenStateFixtures.all(model(SnapshotFixtures.full(), FixtureContext.IST, ""))["insufficient"]!!)
}

class Energy_source_problem_Gallery : EnergyGalleryCase("energy_state_source_problem") {
    @Composable override fun Content() = Screen(ScreenStateFixtures.all(model(SnapshotFixtures.full(), FixtureContext.IST, ""))["source_problem"]!!)
}

class Energy_error_Gallery : EnergyGalleryCase("energy_state_error") {
    @Composable override fun Content() = Screen(ScreenStateFixtures.all(model(SnapshotFixtures.full(), FixtureContext.IST, ""))["error"]!!)
}

