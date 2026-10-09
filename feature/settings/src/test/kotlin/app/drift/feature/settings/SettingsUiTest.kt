package app.drift.feature.settings

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.designsystem.DriftTheme
import app.drift.model.repository.ImportReport
import app.drift.model.repository.ReminderKind
import app.drift.model.repository.SourceFix
import app.drift.model.repository.ThemeMode
import app.drift.model.source.SourceStatus
import app.drift.model.ui.ScreenState
import app.drift.testing.fakes.FakeBackupRepository
import app.drift.testing.fakes.FakeProfileRepository
import app.drift.testing.fakes.FakeSettingsRepository
import app.drift.testing.fakes.FakeSourceRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/** Every clickable element of settings, driven through the real view model with fakes. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h4000dp-xhdpi")
class SettingsUiTest {
    @get:Rule val rule = createComposeRule()

    private val sources = FakeSourceRepository(Sources.allGood)
    private val settings = FakeSettingsRepository()
    private val backups = FakeBackupRepository()
    private val profiles = FakeProfileRepository(onboardingComplete = true)
    private val registry = RecordingRegistryOwner()
    private val fixes = mutableListOf<SourceFix>()
    private val nav = mutableListOf<String>()
    private var resets = 0

    private fun launch() {
        val vm = SettingsViewModel(sources, settings, backups, profiles)
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                DriftTheme(ThemeMode.LIGHT, reducedMotion = true) {
                    SettingsRoute(
                        onOpenSchedule = { nav += "schedule" }, onOpenMethodology = { nav += "methodology" },
                        onOpenLicenses = { nav += "licenses" }, onFix = { fixes += it }, onResetComplete = { resets++ }, viewModel = vm,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun click(text: String) { rule.onNodeWithText(text).also { runCatching { it.performScrollTo() } }.performClick(); rule.waitForIdle() }
    private fun clickDesc(d: String) { rule.onNodeWithContentDescription(d).also { runCatching { it.performScrollTo() } }.performClick(); rule.waitForIdle() }
    private fun shown(text: String) = rule.onNodeWithText(text).assertExists()

    @Test fun syncNowCallsSync() { launch(); click("Sync now"); assertThat(sources.callsTo("sync")).hasSize(1); shown("Sync finished. New or updated sleep sessions: 0.") }

    @Test fun dismissNotice() { launch(); click("Sync now"); click("Dismiss"); rule.onNodeWithText("Sync finished. New or updated sleep sessions: 0.").assertDoesNotExist() }

    @Test fun sourceSwitchesToggle() {
        launch()
        click("Sleep as Android"); click("Phone detection")
        assertThat(sources.callsTo("setEnabled")).hasSize(2)
        assertThat(settings.settings.phoneDetectionEnabled).isFalse()
    }

    @Test fun revokedSourceFixButtons() {
        val fix = SourceFix.GrantHealthConnectPermissions(setOf("p"))
        sources.setSources(listOf(Sources.healthConnect(SourceStatus.NeedsPermission(setOf("p")), fix = fix), Sources.phone()))
        launch()
        shown("Access removed or not yet granted")
        rule.onAllNodesWithTextCompat("Grant access").forEach { it.performScrollTo().performClick() }
        assertThat(fixes).containsExactly(fix, fix)
    }

    @Test fun saaFixOpensPicker() {
        sources.setSources(listOf(Sources.sleepAsAndroid(SourceStatus.NeedsPermission(emptySet()), enabled = true, fix = SourceFix.ImportSleepAsAndroidBackup)))
        launch()
        rule.onAllNodesWithTextCompat("Import backup").first().performScrollTo().performClick()
        assertThat(registry.launched.single().first).isInstanceOf(ActivityResultContracts.OpenDocument::class.java)
    }

    @Test fun saaBackupImportShowsReport() {
        sources.importResult = ImportReport(11, 1, 0, null, null, emptyList())
        val file = File.createTempFile("saa", ".bak").apply { writeText("saa") }
        registry.result = { Uri.fromFile(file) }
        launch()
        click("Import Sleep as Android backup")
        assertThat(String(sources.importedPayloads.single())).isEqualTo("saa")
        shown("11 nights imported")
        click("Close")
        rule.onNodeWithText("11 nights imported").assertDoesNotExist()
    }

    @Test fun failedImportOffersAnotherFile() {
        sources.failNext("importSleepAsAndroidBackup", IOException("not a backup"))
        registry.result = { Uri.fromFile(File.createTempFile("drift-x", ".bak")) }
        launch()
        click("Import Sleep as Android backup")
        shown("not a backup")
        click("Try another file")
        assertThat(registry.launched).hasSize(2)
    }

    @Test fun navigationRows() {
        launch()
        click("Edit schedule"); click("How Drift calculates"); click("Open-source licences")
        assertThat(nav).containsExactly("schedule", "methodology", "licenses").inOrder()
    }

    @Test fun reminderSwitch() {
        launch(); click("Wind-down")
        assertThat(settings.settings.reminders[ReminderKind.WIND_DOWN]).isFalse()
    }

    @Test fun themeAndClockControls() {
        launch()
        click("Dark"); assertThat(settings.settings.theme).isEqualTo(ThemeMode.DARK)
        click("24 hour"); assertThat(settings.settings.use24HourClock).isTrue()
        click("12 hour"); assertThat(settings.settings.use24HourClock).isFalse()
        click("Light"); assertThat(settings.settings.theme).isEqualTo(ThemeMode.LIGHT)
    }

    @Test fun exportLaunchesCreateDocumentAndWrites() {
        val file = File.createTempFile("export", ".drift")
        registry.result = { Uri.fromFile(file) }
        launch()
        click("Export everything")
        assertThat(registry.launched.single().first).isInstanceOf(ActivityResultContracts.CreateDocument::class.java)
        assertThat(backups.callsTo("exportAll")).hasSize(1)
        assertThat(file.readText()).isEqualTo("drift-fake-export-v1")
        shown("Export saved.")
    }

    @Test fun importDriftExport() {
        backups.importResult = ImportReport(3, 0, 0, null, null, emptyList())
        registry.result = { Uri.fromFile(File.createTempFile("drift-in", ".drift").apply { writeText("zz") }) }
        launch()
        click("Import a Drift export")
        assertThat(String(backups.importedPayloads.single())).isEqualTo("zz")
        shown("3 nights imported")
    }

    @Test fun backupNowAndRestoreWithConfirmation() {
        launch()
        click("Back up now")
        assertThat(backups.callsTo("createLocalBackup")).hasSize(1)
        rule.onAllNodesWithTextCompat("Restore").first().performScrollTo().performClick()
        shown("Restore this backup?")
        click("Cancel")
        assertThat(backups.callsTo("restoreLocalBackup")).isEmpty()
        rule.onAllNodesWithTextCompat("Restore").first().performScrollTo().performClick()
        rule.onAllNodesWithTextCompat("Restore").last().performClick()
        rule.waitForIdle()
        assertThat(backups.callsTo("restoreLocalBackup")).hasSize(1)
        shown("Backup restored.")
    }

    /**
     * The reset confirmation is rendered inline here: the platform Dialog keeps the text field's cursor animating, which
     * stops Compose tests from going idle. The Dialog is a thin wrapper around this same card.
     */
    private fun launchResetCard(onCancel: () -> Unit = {}) {
        val vm = SettingsViewModel(sources, settings, backups, profiles)
        rule.setContent {
            DriftTheme(ThemeMode.LIGHT, reducedMotion = true) {
                var typed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                ResetConfirmCard(typed, { typed = it }, onConfirm = { vm.resetAll(); resets++ }, onDismiss = onCancel)
            }
        }
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(100)
    }

    @Test fun resetNeedsTypedWord() {
        launchResetCard()
        rule.onNodeWithText("Delete everything").assertIsNotEnabled()
        rule.onNodeWithText("Type RESET to confirm").performTextInput("RESET")
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Delete everything").assertIsEnabled().performClick()
        rule.mainClock.advanceTimeBy(100)
        assertThat(backups.callsTo("wipeAll")).hasSize(1)
        assertThat(profiles.onboardingComplete).isFalse()
        assertThat(resets).isEqualTo(1)
    }

    @Test fun resetCancelDoesNothing() {
        var cancelled = 0
        launchResetCard { cancelled++ }
        rule.onNodeWithText("Cancel").performClick()
        assertThat(cancelled).isEqualTo(1)
        assertThat(backups.callsTo("wipeAll")).isEmpty()
    }

    @Test fun resetRowIsPresentAndEnabled() {
        launch()
        rule.onNodeWithText("Reset all data").assertExists().assertIsEnabled()
    }

    @Test fun errorStateRetry() {
        var retries = 0
        rule.setContent {
            DriftTheme(ThemeMode.LIGHT, reducedMotion = true) {
                SettingsScreen(ScreenState.Error("boom", true), "1", SettingsActions(onRetry = { retries++ }))
            }
        }
        click("Try again"); assertThat(retries).isEqualTo(1)
    }

    @Test fun scheduleScreenControlsAndSave() {
        val p = FakeProfileRepository(null)
        val vm = ScheduleViewModel(p)
        var backs = 0
        rule.setContent {
            DriftTheme(ThemeMode.LIGHT, reducedMotion = true) { ScheduleRoute(onBack = { backs++ }, viewModel = vm) }
        }
        rule.waitForIdle()
        clickDesc("Monday, selected")
        clickDesc("Make Wake-up time on work days later")
        clickDesc("Make Bedtime on free days earlier")
        clickDesc("Make Wake-up time on free days later")
        click("65 or older")
        click("More of a morning person")
        click("Save schedule")
        val saved = p.profile!!
        assertThat(saved.schedule.workdays.size).isEqualTo(4)
        assertThat(saved.ageBand).isEqualTo(app.drift.model.engine.AgeBand.AGE_65_PLUS)
        assertThat(backs).isEqualTo(1)
    }

    @Test fun scheduleBackArrow() {
        var backs = 0
        rule.setContent {
            DriftTheme(ThemeMode.LIGHT, reducedMotion = true) {
                ScheduleScreen(ScreenState.Ready(ScheduleModel(ScheduleForm.Default, false, null)), true, ScheduleActions(onBack = { backs++ }))
            }
        }
        clickDesc("Back"); assertThat(backs).isEqualTo(1)
    }

    @Test fun methodologyExpandsAndBackWorks() {
        var backs = 0
        rule.setContent { DriftTheme(ThemeMode.LIGHT, reducedMotion = true) { MethodologyScreen({ backs++ }) } }
        rule.onNodeWithText("Phillips", substring = true).assertDoesNotExist()
        rule.onAllNodesWithTextCompat("Show details")[2].performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Phillips", substring = true).assertExists()
        rule.onAllNodesWithTextCompat("Hide details").single().performScrollTo().performClick()
        rule.onNodeWithText("Phillips", substring = true).assertDoesNotExist()
        clickDesc("Back"); assertThat(backs).isEqualTo(1)
    }

    @Test fun licensesShowFontAndBack() {
        var backs = 0
        rule.setContent { DriftTheme(ThemeMode.LIGHT, reducedMotion = true) { LicensesScreen { backs++ } } }
        rule.onNodeWithText("SIL OPEN FONT LICENSE", substring = true).assertExists()
        clickDesc("Back"); assertThat(backs).isEqualTo(1)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCompat(t: String) =
        onAllNodesWithText(t).fetchSemanticsNodes().indices.map { i -> onAllNodesWithText(t)[i] }

}
