package app.drift.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.model.Provenance
import app.drift.model.repository.SourceFix
import app.drift.model.repository.ThemeMode
import app.drift.model.ui.InsufficientReason
import app.drift.model.ui.ScreenState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DesignSystemTest {
    @get:Rule val rule = createComposeRule()

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test fun formatDurations() {
        assertThat(DriftFormat.duration(Duration.ofMinutes(440))).isEqualTo("7 h 20 min")
        assertThat(DriftFormat.duration(Duration.ofMinutes(45))).isEqualTo("45 min")
        assertThat(DriftFormat.duration(Duration.ofHours(8))).isEqualTo("8 h")
        assertThat(DriftFormat.durationSpoken(Duration.ofMinutes(61))).isEqualTo("1 hour 1 minute")
        assertThat(DriftFormat.signedDuration(Duration.ofMinutes(-20))).isEqualTo("-20 min")
        assertThat(DriftFormat.signedDuration(Duration.ofMinutes(45))).isEqualTo("+45 min")
    }

    @Test fun formatClock() {
        assertThat(DriftFormat.clockMinutes(7 * 60 + 5, true)).isEqualTo("07:05")
        assertThat(DriftFormat.clockMinutes(0, false)).isEqualTo("12:00 AM")
        assertThat(DriftFormat.clockMinutes(13 * 60 + 30, false)).isEqualTo("1:30 PM")
        assertThat(DriftFormat.clockMinutes(-30, true)).isEqualTo("23:30")
        assertThat(DriftFormat.clock(Instant.parse("2026-10-09T06:10:00Z"), ZoneId.of("UTC"), true)).isEqualTo("06:10")
    }

    @Test fun reducedMotionFollowsAnimatorScale() {
        assertThat(isReducedMotion(0f)).isTrue()
        assertThat(isReducedMotion(1f)).isFalse()
        assertThat(isReducedMotion(0.5f)).isFalse()
    }

    @Test fun reducedMotionHelperReadsSystemSetting() {
        android.provider.Settings.Global.putFloat(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f,
        )
        var reduced = false
        rule.setContent { reduced = rememberReducedMotion() }
        rule.waitForIdle()
        assertThat(reduced).isTrue()
    }

    @Test fun staggerDelayIsCapped() {
        val m = DriftMotion()
        assertThat(m.staggerDelay(0)).isEqualTo(0)
        assertThat(m.staggerDelay(3)).isEqualTo(135)
        assertThat(m.staggerDelay(50)).isEqualTo(8 * 45)
    }

    private fun contrast(a: Color, b: Color): Double {
        val l1 = maxOf(a.luminance(), b.luminance()).toDouble()
        val l2 = minOf(a.luminance(), b.luminance()).toDouble()
        return (l1 + 0.05) / (l2 + 0.05)
    }

    @Test fun textColoursMeetWcagAa() {
        for (c in listOf(DriftDarkColors, DriftLightColors)) {
            for (bg in listOf(c.background, c.surface, c.surfaceRaised, c.accentSurface)) {
                for (fg in listOf(c.onSurface, c.onSurfaceMuted, c.primary, c.energy, c.melatonin, c.windDown, c.success, c.error)) {
                    assertThat(contrast(fg, bg)).isAtLeast(4.5)
                }
            }
            assertThat(contrast(c.onPrimary, c.primary)).isAtLeast(4.5)
            assertThat(contrast(c.onPrimaryContainer, c.primaryContainer)).isAtLeast(4.5)
            assertThat(contrast(c.onSurface, c.primaryContainer)).isAtLeast(4.5)
            assertThat(contrast(c.outline, c.surface)).isAtLeast(3.0)
            assertThat(contrast(c.outline, c.background)).isAtLeast(3.0)
        }
    }

    @Test fun themeModeMapsToPalette() {
        var dark: Boolean? = null
        var light: Boolean? = null
        rule.setContent {
            Column {
                DriftTheme(ThemeMode.DARK) { dark = DriftTheme.colors.isDark }
                DriftTheme(ThemeMode.LIGHT) { light = DriftTheme.colors.isDark }
            }
        }
        rule.waitForIdle()
        assertThat(dark).isTrue()
        assertThat(light).isFalse()
    }

    @Test fun segmentedControlSelectsWithRadioRole() {
        var selected = 0
        rule.setContent {
            DriftTheme(ThemeMode.DARK, reducedMotion = true) {
                SegmentedControl(listOf("Week", "Month"), selected, { selected = it })
            }
        }
        rule.onNodeWithText("Week").assert(hasRole(Role.RadioButton)).assertIsSelected()
        rule.onNodeWithText("Month").performClick()
        assertThat(selected).isEqualTo(1)
    }

    @Test fun provenanceChipHasDescription() {
        rule.setContent { DriftTheme(ThemeMode.DARK, reducedMotion = true) { Column { Provenance.entries.forEach { ProvenanceChip(it) } } } }
        rule.onNodeWithContentDescription("Estimated: a model estimate with a margin of error").assertExists()
        rule.onNodeWithContentDescription("Measured: recorded directly by your phone or a connected app").assertExists()
        rule.onNodeWithContentDescription("Derived: calculated from measured data using fixed rules").assertExists()
        rule.onNodeWithContentDescription("User-entered: typed in or edited by you").assertExists()
    }

    @Test fun bottomNavUsesTabRoleAndReportsSelection() {
        var sel = 0
        rule.setContent {
            DriftTheme(ThemeMode.DARK, reducedMotion = true) { DriftBottomNavBar(driftDefaultNavItems(), sel, { sel = it }) }
        }
        rule.onNodeWithContentDescription("Sleep").assert(hasRole(Role.Tab)).assertIsSelected()
        rule.onNodeWithContentDescription("Progress").performClick()
        assertThat(sel).isEqualTo(2)
    }

    @Test fun switchRowToggles() {
        var on = false
        rule.setContent { DriftTheme(ThemeMode.DARK, reducedMotion = true) { DriftSwitchRow("Reminder", on, { on = it }) } }
        rule.onNodeWithText("Reminder").assert(hasRole(Role.Switch))
        rule.onNodeWithText("Reminder").performClick()
        assertThat(on).isTrue()
    }

    @Test fun hostRendersEachState() {
        var state: ScreenState<String> by androidx.compose.runtime.mutableStateOf<ScreenState<String>>(ScreenState.Loading)
        var retried = 0
        var fixed: SourceFix? = null
        var started = 0
        rule.setContent {
            DriftTheme(ThemeMode.DARK, reducedMotion = true) {
                ScreenStateHost(state, onRetry = { retried++ }, onFix = { fixed = it }, onStartSetup = { started++ }) { Text("model:$it") }
            }
        }
        rule.onNodeWithContentDescription("Loading").assertExists()

        state = ScreenState.FirstUse(2)
        rule.onNodeWithText("Continue setup").performClick()
        assertThat(started).isEqualTo(1)

        state = ScreenState.Insufficient(InsufficientReason.TOO_FEW_NIGHTS, 3)
        rule.onNodeWithText("Not enough nights yet").assertExists()
        rule.onNodeWithContentDescription("3 more nights needed").assertExists()

        state = ScreenState.Ready("hello")
        rule.onNodeWithText("model:hello").assertExists()

        state = ScreenState.SourceProblem(SourceFix.OpenAppSettings, "Lost access")
        rule.onNodeWithText("Lost access").assertExists()
        rule.onNodeWithText("Open app settings").performClick()
        assertThat(fixed).isEqualTo(SourceFix.OpenAppSettings)

        state = ScreenState.Error("Boom", retryable = true)
        rule.onNodeWithText("Try again").performClick()
        assertThat(retried).isEqualTo(1)

        state = ScreenState.Error("Boom", retryable = false)
        rule.onAllNodesWithText("Try again").assertCountEquals(0)
    }

    @Test fun confidenceBandText() {
        assertThat(confidenceBandText("25 min", "7 h 5 min", "7 h 55 min", 0.9))
            .isEqualTo("± 25 min (90% range 7 h 5 min to 7 h 55 min)")
    }
}
