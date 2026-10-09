package app.drift.feature.onboarding

import app.drift.model.SourceId
import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.repository.ImportReport
import app.drift.model.repository.SourceFix
import app.drift.model.source.SourceStatus
import app.drift.model.ui.ScreenState
import app.drift.testing.fixtures.FixtureContext
import app.drift.testing.fixtures.SnapshotFixtures
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** Every onboarding step and special state, light/dark at 1x and 2x font. */
class OnboardingScreenshotTest : ShotTest() {
    private val ctx = FixtureContext.IST

    private fun model(
        step: OnboardingStep,
        sources: List<app.drift.model.repository.SourceInfo> = Sources.allGood,
        page: PermissionPage = PermissionPage.HEALTH_CONNECT,
        form: ProfileForm = ProfileForm(),
        history: HistoryImport = HistoryImport.Idle,
        backup: BackupImport = BackupImport.Idle,
        reveal: Reveal = Reveal.Idle,
        notifications: Boolean? = null,
        error: StepError? = null,
    ) = OnboardingModel(
        step = step, form = form, sources = sources, phoneDetection = true, use24h = true,
        permissionPage = page, permissionPages = PermissionPage.entries, notificationsGranted = notifications,
        history = history, backup = backup, reveal = reveal, error = error, busy = false,
    )

    private fun case(name: String, m: OnboardingModel) =
        shoot(name) { OnboardingScreen(ScreenState.Ready(m), OnboardingActions.None) }

    private fun revealReady(priorsOnly: Boolean): Reveal.Ready {
        val snap = if (priorsOnly) SnapshotFixtures.priorsOnlyDay0(ctx) else SnapshotFixtures.full(ctx)
        return Reveal.Ready(snap.energyCurve.forDate(ctx.today), snap.dataSufficiency.tier, snap.dataSufficiency.nightsUntilPersonalNeed, ctx.now, ctx.zone)
    }

    @Test fun welcome() = case("welcome", model(OnboardingStep.WELCOME))
    @Test fun howItWorks() = case("how_it_works", model(OnboardingStep.HOW_IT_WORKS))
    @Test fun schedule() = case("schedule", model(OnboardingStep.SCHEDULE))
    @Test fun scheduleNoWorkdays() = case("schedule_free", model(OnboardingStep.SCHEDULE, form = ProfileForm(workdays = emptySet())))
    @Test fun aboutYou() = case(
        "about_you",
        model(OnboardingStep.ABOUT_YOU, form = ProfileForm(ageBand = AgeBand.AGE_36_50, chronotype = ChronotypeAnswer.MODERATELY_EVENING)),
    )
    @Test fun aboutYouSaveFailed() = case(
        "about_you_error",
        model(OnboardingStep.ABOUT_YOU, error = StepError(ErrorKind.SAVE, "disk full")),
    )
    @Test fun sources() = case("sources", model(OnboardingStep.SOURCES))
    @Test fun sourcesNeedingFixes() = case(
        "sources_problems",
        model(
            OnboardingStep.SOURCES,
            sources = listOf(
                Sources.healthConnect(SourceStatus.NotInstalled, fix = SourceFix.InstallHealthConnect),
                Sources.sleepAsAndroid(SourceStatus.NeedsPermission(setOf("backup")), enabled = true, fix = SourceFix.ImportSleepAsAndroidBackup),
                Sources.phone(SourceStatus.NeedsPermission(setOf(ACTIVITY_RECOGNITION_PERMISSION)), fix = SourceFix.OpenAppSettings),
            ),
        ),
    )

    private val hcPerms = setOf("android.permission.health.READ_SLEEP", "android.permission.health.READ_HEART_RATE", "android.permission.health.READ_STEPS")

    @Test fun permissionHealthConnectConnected() = case(
        "perm_hc_connected",
        model(OnboardingStep.PERMISSIONS, sources = listOf(Sources.healthConnect(fix = SourceFix.GrantHealthConnectPermissions(hcPerms)), Sources.phone())),
    )
    @Test fun permissionHealthConnectPartial() = case(
        "perm_hc_partial",
        model(
            OnboardingStep.PERMISSIONS,
            sources = listOf(
                Sources.healthConnect(
                    SourceStatus.NeedsPermission(setOf("android.permission.health.READ_HEART_RATE")),
                    fix = SourceFix.GrantHealthConnectPermissions(hcPerms),
                ),
                Sources.phone(),
            ),
        ),
    )
    @Test fun permissionHealthConnectMissing() = case(
        "perm_hc_missing",
        model(OnboardingStep.PERMISSIONS, sources = listOf(Sources.healthConnect(SourceStatus.NotInstalled, fix = SourceFix.InstallHealthConnect), Sources.phone())),
    )
    @Test fun permissionActivity() = case(
        "perm_activity",
        model(OnboardingStep.PERMISSIONS, page = PermissionPage.ACTIVITY_RECOGNITION, sources = listOf(Sources.phone(SourceStatus.NeedsPermission(setOf(ACTIVITY_RECOGNITION_PERMISSION))))),
    )
    @Test fun permissionActivityGranted() = case(
        "perm_activity_granted",
        model(OnboardingStep.PERMISSIONS, page = PermissionPage.ACTIVITY_RECOGNITION),
    )
    @Test fun permissionUsage() = case(
        "perm_usage",
        model(OnboardingStep.PERMISSIONS, page = PermissionPage.USAGE_ACCESS, sources = listOf(Sources.phone(SourceStatus.NeedsPermission(setOf(USAGE_ACCESS_PERMISSION))))),
    )
    @Test fun permissionNotifications() = case("perm_notifications", model(OnboardingStep.PERMISSIONS, page = PermissionPage.NOTIFICATIONS))
    @Test fun permissionNotificationsDenied() = case(
        "perm_notifications_denied",
        model(OnboardingStep.PERMISSIONS, page = PermissionPage.NOTIFICATIONS, notifications = false),
    )

    @Test fun importRunning() = case("import_running", model(OnboardingStep.IMPORT, history = HistoryImport.Running(0.34f)))
    @Test fun importIndeterminate() = case("import_waiting", model(OnboardingStep.IMPORT, history = HistoryImport.Running(null)))
    @Test fun importDone() = case("import_done", model(OnboardingStep.IMPORT, history = HistoryImport.Done(61, emptyMap())))
    @Test fun importPartial() = case(
        "import_partial",
        model(OnboardingStep.IMPORT, history = HistoryImport.Done(12, mapOf("Health Connect" to "Permission was removed"))),
    )
    @Test fun importZeroNights() = case("import_zero", model(OnboardingStep.IMPORT, history = HistoryImport.Done(0, emptyMap())))
    @Test fun importFailed() = case("import_failed", model(OnboardingStep.IMPORT, history = HistoryImport.Failed("The source did not respond")))
    @Test fun importCancelled() = case("import_cancelled", model(OnboardingStep.IMPORT, history = HistoryImport.Cancelled))
    @Test fun importBackupDone() = case(
        "import_backup_done",
        model(
            OnboardingStep.IMPORT, history = HistoryImport.Done(0, emptyMap()),
            backup = BackupImport.Done(ImportReport(48, 3, 2, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 10, 12), listOf("Two rows had no end time"))),
        ),
    )
    @Test fun importBackupFailed() = case(
        "import_backup_failed",
        model(OnboardingStep.IMPORT, history = HistoryImport.Done(20, emptyMap()), backup = BackupImport.Failed("Not a Sleep as Android backup")),
    )

    @Test fun revealPriorsOnly() = case("reveal_priors", model(OnboardingStep.REVEAL, reveal = revealReady(true)))
    @Test fun revealPersonal() = case("reveal_personal", model(OnboardingStep.REVEAL, reveal = revealReady(false)))
    @Test fun revealLoading() = case("reveal_loading", model(OnboardingStep.REVEAL, reveal = Reveal.Loading))
    @Test fun revealFailed() = case("reveal_failed", model(OnboardingStep.REVEAL, reveal = Reveal.Failed("The engine could not run")))
    @Test fun revealFinishFailed() = case(
        "reveal_finish_error",
        model(OnboardingStep.REVEAL, reveal = revealReady(true), error = StepError(ErrorKind.FINISH, "storage is full")),
    )

    @Test fun stateLoading() = shoot("state_loading") { OnboardingScreen(ScreenState.Loading, OnboardingActions.None) }
    @Test fun stateError() = shoot("state_error") {
        OnboardingScreen(ScreenState.Error("Drift could not read your sources.", retryable = true), OnboardingActions.None)
    }
    @Test fun stateSourceProblem() = shoot("state_source_problem") {
        OnboardingScreen(ScreenState.SourceProblem(SourceFix.OpenAppSettings, "Drift needs access to continue."), OnboardingActions.None)
    }
}
