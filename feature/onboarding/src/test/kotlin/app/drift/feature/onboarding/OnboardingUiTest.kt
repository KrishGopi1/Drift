package app.drift.feature.onboarding

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.designsystem.DriftTheme
import app.drift.model.SourceId
import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.repository.ImportReport
import app.drift.model.repository.SourceFix
import app.drift.model.repository.ThemeMode
import app.drift.model.source.SourceStatus
import app.drift.model.ui.ScreenState
import app.drift.testing.fakes.FakeAnalysisRepository
import app.drift.testing.fakes.FakeProfileRepository
import app.drift.testing.fakes.FakeSettingsRepository
import app.drift.testing.fakes.FakeSourceRepository
import app.drift.testing.fixtures.FixtureContext
import app.drift.testing.fixtures.SnapshotFixtures
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.DayOfWeek

/** Every clickable element of the onboarding flow, driven through the real view model with fakes. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h1200dp-xhdpi")
class OnboardingUiTest {
    @get:Rule val rule = createComposeRule()

    private val ctx = FixtureContext.IST
    private val profiles = FakeProfileRepository()
    private val sources = FakeSourceRepository(Sources.allGood)
    private val settings = FakeSettingsRepository()
    private val analysis = FakeAnalysisRepository(SnapshotFixtures.priorsOnlyDay0(ctx))
    private val fixes = mutableListOf<SourceFix>()
    private var finished = 0
    private val registry = RecordingRegistryOwner()
    private lateinit var vm: OnboardingViewModel

    private fun launch(step: OnboardingStep, page: PermissionPage? = null) {
        val saved = buildMap<String, Any?> {
            put("step", step.ordinal)
            if (page != null) put("permission", page.ordinal)
        }
        vm = OnboardingViewModel(profiles, sources, settings, analysis, SavedStateHandle(saved)).also {
            it.sdkInt = 35; it.clock = ctx.clock
        }
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                DriftTheme(ThemeMode.LIGHT, reducedMotion = true) {
                    OnboardingRoute(onFinished = { finished++ }, onFix = { fixes += it }, viewModel = vm)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun click(text: String) { rule.onNodeWithText(text).performClick(); rule.waitForIdle() }
    private fun clickDesc(desc: String) { rule.onNodeWithContentDescription(desc).performClick(); rule.waitForIdle() }
    private fun model() = (vm.state.value as ScreenState.Ready).model
    private fun shown(text: String) = rule.onNodeWithText(text).assertExists()

    @Test fun getStartedAndContinueAdvance() {
        launch(OnboardingStep.WELCOME)
        click("Get started")
        assertThat(model().step).isEqualTo(OnboardingStep.HOW_IT_WORKS)
        click("Continue")
        assertThat(model().step).isEqualTo(OnboardingStep.SCHEDULE)
    }

    @Test fun backArrowGoesBack() {
        launch(OnboardingStep.HOW_IT_WORKS)
        clickDesc("Back")
        assertThat(model().step).isEqualTo(OnboardingStep.WELCOME)
    }

    @Test fun scheduleControlsChangeTheForm() {
        launch(OnboardingStep.SCHEDULE)
        clickDesc("Monday, selected")
        assertThat(DayOfWeek.MONDAY in model().form.workdays).isFalse()
        clickDesc("Make Wake-up time on work days earlier")
        assertThat(model().form.wakeMinutes).isEqualTo(ProfileForm.DEFAULT_WAKE - 15)
        clickDesc("Make Wake-up time on work days later")
        assertThat(model().form.wakeMinutes).isEqualTo(ProfileForm.DEFAULT_WAKE)
        clickDesc("Make Bedtime on free days later")
        assertThat(model().form.freeBedMinutes).isEqualTo(ProfileForm.DEFAULT_FREE_BED + 15)
        clickDesc("Make Bedtime on free days earlier")
        clickDesc("Make Wake-up time on free days later")
        assertThat(model().form.freeWakeMinutes).isEqualTo(ProfileForm.DEFAULT_FREE_WAKE + 15)
        clickDesc("Make Wake-up time on free days earlier")
        assertThat(model().form.freeWakeMinutes).isEqualTo(ProfileForm.DEFAULT_FREE_WAKE)
    }

    @Test fun aboutYouChoicesAndContinueSavesProfile() {
        launch(OnboardingStep.ABOUT_YOU)
        click("51 to 64")
        click("Definitely an evening person")
        assertThat(model().form.ageBand).isEqualTo(AgeBand.AGE_51_64)
        assertThat(model().form.chronotype).isEqualTo(ChronotypeAnswer.DEFINITELY_EVENING)
        click("I am not sure")
        assertThat(model().form.chronotype).isNull()
        click("Continue")
        assertThat(profiles.callsTo("saveProfile")).hasSize(1)
        assertThat(model().step).isEqualTo(OnboardingStep.SOURCES)
    }

    @Test fun saveFailureShowsError() {
        profiles.failNext("saveProfile", IOException("disk full"))
        launch(OnboardingStep.ABOUT_YOU)
        click("Continue")
        shown("Drift could not save your answers: disk full")
    }

    @Test fun sourceSwitchesCallRepository() {
        launch(OnboardingStep.SOURCES)
        click("Health Connect")
        click("Sleep as Android")
        click("Phone detection")
        assertThat(sources.callsTo("setEnabled")).hasSize(3)
        assertThat(settings.settings.phoneDetectionEnabled).isFalse()
    }

    @Test fun sourceFixButtonsCallOnFixOrOpenPicker() {
        sources.setSources(
            listOf(
                Sources.healthConnect(SourceStatus.NotInstalled, fix = SourceFix.InstallHealthConnect),
                Sources.sleepAsAndroid(SourceStatus.NeedsPermission(emptySet()), enabled = true, fix = SourceFix.ImportSleepAsAndroidBackup),
                Sources.phone(),
            ),
        )
        launch(OnboardingStep.SOURCES)
        click("Install Health Connect")
        assertThat(fixes).containsExactly(SourceFix.InstallHealthConnect)
        click("Import backup")
        assertThat(registry.launched.map { it.first }.single()).isInstanceOf(ActivityResultContracts.OpenDocument::class.java)
    }

    @Test fun sourcesContinueEntersPermissions() {
        launch(OnboardingStep.SOURCES)
        click("Continue")
        assertThat(model().step).isEqualTo(OnboardingStep.PERMISSIONS)
    }

    @Test fun healthConnectPermissionActionAndSkip() {
        val fix = SourceFix.GrantHealthConnectPermissions(setOf("android.permission.health.READ_SLEEP"))
        sources.setSources(listOf(Sources.healthConnect(SourceStatus.NeedsPermission(setOf("android.permission.health.READ_SLEEP")), fix = fix), Sources.phone()))
        launch(OnboardingStep.PERMISSIONS, PermissionPage.HEALTH_CONNECT)
        click("Grant access")
        assertThat(fixes).containsExactly(fix)
        click("Not now")
        assertThat(model().permissionPage).isEqualTo(PermissionPage.ACTIVITY_RECOGNITION)
    }

    @Test fun healthConnectMissingOffersInstallAndPhoneOnly() {
        sources.setSources(listOf(Sources.healthConnect(SourceStatus.NotInstalled, fix = SourceFix.InstallHealthConnect), Sources.phone(enabled = false)))
        launch(OnboardingStep.PERMISSIONS, PermissionPage.HEALTH_CONNECT)
        click("Install Health Connect")
        assertThat(fixes).containsExactly(SourceFix.InstallHealthConnect)
        click("Use phone detection only")
        assertThat(sources.sources.first { it.id == SourceId.HEALTH_CONNECT }.enabled).isFalse()
        assertThat(model().permissionPage).isEqualTo(PermissionPage.ACTIVITY_RECOGNITION)
    }

    @Test fun healthConnectMissingCanBeSkipped() {
        sources.setSources(listOf(Sources.healthConnect(SourceStatus.NotInstalled, fix = SourceFix.InstallHealthConnect), Sources.phone()))
        launch(OnboardingStep.PERMISSIONS, PermissionPage.HEALTH_CONNECT)
        click("Not now")
        assertThat(model().permissionPage).isEqualTo(PermissionPage.ACTIVITY_RECOGNITION)
    }

    @Test fun connectedHealthConnectContinues() {
        launch(OnboardingStep.PERMISSIONS, PermissionPage.HEALTH_CONNECT)
        click("Continue")
        assertThat(model().permissionPage).isEqualTo(PermissionPage.ACTIVITY_RECOGNITION)
    }

    @Test fun activityAndUsageActionsUseOnFix() {
        sources.setSources(listOf(Sources.phone(SourceStatus.NeedsPermission(setOf(ACTIVITY_RECOGNITION_PERMISSION, USAGE_ACCESS_PERMISSION)))))
        launch(OnboardingStep.PERMISSIONS, PermissionPage.ACTIVITY_RECOGNITION)
        click("Allow activity recognition")
        assertThat(fixes.single()).isEqualTo(SourceFix.GrantRuntimePermissions(setOf(ACTIVITY_RECOGNITION_PERMISSION)))
        click("Not now")
        click("Open usage access settings")
        assertThat(fixes.last()).isEqualTo(SourceFix.GrantUsageAccess)
        click("Not now")
        assertThat(model().permissionPage).isEqualTo(PermissionPage.NOTIFICATIONS)
    }

    @Test fun notificationsRequestUsesSystemLauncherThenAdvances() {
        registry.result = { true }
        launch(OnboardingStep.PERMISSIONS, PermissionPage.NOTIFICATIONS)
        click("Allow notifications")
        assertThat(registry.launched.single().second).isEqualTo("android.permission.POST_NOTIFICATIONS")
        assertThat(model().notificationsGranted).isTrue()
        assertThat(model().step).isEqualTo(OnboardingStep.IMPORT)
        assertThat(sources.callsTo("sync")).hasSize(1)
    }

    @Test fun notificationsSkipStillReachesImport() {
        launch(OnboardingStep.PERMISSIONS, PermissionPage.NOTIFICATIONS)
        click("Not now")
        assertThat(model().step).isEqualTo(OnboardingStep.IMPORT)
    }

    @Test fun importContinueAndBackupPicker() {
        sources.importResult = ImportReport(7, 0, 0, null, null, emptyList())
        val file = java.io.File.createTempFile("backup", ".sleep").apply { writeText("data") }
        registry.result = { Uri.fromFile(file) }
        launch(OnboardingStep.IMPORT)
        click("Import Sleep as Android backup")
        assertThat(sources.callsTo("importSleepAsAndroidBackup")).hasSize(1)
        assertThat(String(sources.importedPayloads.single())).isEqualTo("data")
        shown("Backup imported")
        click("Continue")
        assertThat(model().step).isEqualTo(OnboardingStep.REVEAL)
    }

    @Test fun failedImportCanRetry() {
        sources.failNext("sync", IOException("offline"))
        launch(OnboardingStep.IMPORT)
        click("Try again")
        assertThat(sources.callsTo("sync")).hasSize(2)
    }

    @Test fun cancelledImportCanBeReread() {
        launch(OnboardingStep.IMPORT)
        vm.cancelImport()
        rule.waitForIdle()
        click("Read history again")
        assertThat(sources.callsTo("sync")).hasSize(2)
    }

    @Test fun cancelButtonShownWhileRunning() {
        // The fake sync finishes at once, so show the running state through the stateless screen.
        var cancels = 0
        val m = OnboardingModel(
            OnboardingStep.IMPORT, ProfileForm(), Sources.allGood, true, true, PermissionPage.HEALTH_CONNECT,
            emptyList(), null, HistoryImport.Running(0.5f), BackupImport.Idle, Reveal.Idle, null, false,
        )
        rule.setContent {
            DriftTheme(ThemeMode.LIGHT, reducedMotion = true) {
                OnboardingScreen(ScreenState.Ready(m), OnboardingActions(onCancelImport = { cancels++ }))
            }
        }
        click("Cancel import")
        assertThat(cancels).isEqualTo(1)
    }

    @Test fun revealFailureRetryAndOpenDrift() {
        analysis.failNext("refresh", IllegalStateException("engine"))
        launch(OnboardingStep.REVEAL)
        click("Try again")
        assertThat(analysis.callsTo("refresh")).hasSize(2)
        click("Open Drift")
        assertThat(profiles.onboardingComplete).isTrue()
        assertThat(finished).isEqualTo(1)
    }

    @Test fun stateErrorRetryButton() {
        var retries = 0
        rule.setContent {
            DriftTheme(ThemeMode.LIGHT, reducedMotion = true) {
                OnboardingScreen(ScreenState.Error("boom", retryable = true), OnboardingActions(onRetryLoad = { retries++ }))
            }
        }
        click("Try again")
        assertThat(retries).isEqualTo(1)
    }

    @Test fun stateSourceProblemFixButton() {
        val got = mutableListOf<SourceFix>()
        rule.setContent {
            DriftTheme(ThemeMode.LIGHT, reducedMotion = true) {
                OnboardingScreen(ScreenState.SourceProblem(SourceFix.OpenAppSettings, "x"), OnboardingActions(onFix = { got += it }))
            }
        }
        click("Open app settings")
        assertThat(got).containsExactly(SourceFix.OpenAppSettings)
    }
}
