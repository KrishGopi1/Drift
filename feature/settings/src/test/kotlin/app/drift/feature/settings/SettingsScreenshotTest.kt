package app.drift.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.drift.model.repository.AppSettings
import app.drift.model.repository.ImportReport
import app.drift.model.repository.LocalBackup
import app.drift.model.repository.SourceFix
import app.drift.model.repository.SourceInfo
import app.drift.model.repository.ThemeMode
import app.drift.model.source.SourceStatus
import app.drift.model.ui.ScreenState
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal val backupsFixture = listOf(
    LocalBackup("b2", Instant.parse("2026-10-14T02:00:00Z"), 1_450_000),
    LocalBackup("b1", Instant.parse("2026-10-13T02:00:00Z"), 1_380_000),
)

internal fun settingsModel(
    sources: List<SourceInfo> = Sources.allGood,
    settings: AppSettings = AppSettings(),
    backups: List<LocalBackup> = backupsFixture,
    syncing: Boolean = false,
    notice: Notice? = null,
    outcome: ImportOutcome? = null,
) = SettingsModel(sources, settings, backups, syncing, false, notice, outcome, ZoneOffset.UTC)

private val importReport = ImportReport(48, 3, 2, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 10, 12), listOf("Two rows had no end time"))

/** Full-length captures of the main settings list. */
@org.robolectric.annotation.Config(sdk = [35], qualifiers = "w360dp-h1600dp-xhdpi")
class SettingsTallScreenshotTest : ShotTest() {
    override val heightDp = 1600

    private fun case(name: String, m: SettingsModel) =
        shoot(name) { SettingsScreen(ScreenState.Ready(m), "1.0.0", SettingsActions()) }

    @Test fun ready() = case("settings_ready", settingsModel(settings = AppSettings(use24HourClock = true)))
    @Test fun readyDarkThemeChosen() = case(
        "settings_ready_prefs",
        settingsModel(
            settings = AppSettings(theme = ThemeMode.DARK, use24HourClock = false, reminders = AppSettings().reminders + (app.drift.model.repository.ReminderKind.ENERGY_CHECK_IN to true)),
            backups = emptyList(), syncing = true, notice = Notice(NoticeKind.SYNCED, "4"),
        ),
    )
    @Test fun revokedPermission() = case(
        "settings_revoked",
        settingsModel(
            sources = listOf(
                Sources.healthConnect(SourceStatus.NeedsPermission(setOf("android.permission.health.READ_SLEEP")), fix = SourceFix.GrantHealthConnectPermissions(setOf("android.permission.health.READ_SLEEP"))),
                Sources.sleepAsAndroid(),
                Sources.phone(SourceStatus.NotInstalled, enabled = false),
            ),
            notice = Notice(NoticeKind.SYNC_FAILED, "Health Connect refused access"),
        ),
    )
}

class SettingsScreenshotTest : ShotTest() {
    @Test fun loading() = shoot("settings_loading") { SettingsScreen(ScreenState.Loading, "1.0.0", SettingsActions()) }
    @Test fun error() = shoot("settings_error") {
        SettingsScreen(ScreenState.Error("Settings could not be read.", retryable = true), "1.0.0", SettingsActions())
    }
    @Test fun sourceProblem() = shoot("settings_source_problem") {
        SettingsScreen(ScreenState.SourceProblem(SourceFix.OpenAppSettings, "Drift cannot read its own storage."), "1.0.0", SettingsActions())
    }

    @Test fun importSuccess() = shoot("dialog_import_success") {
        Column(Modifier.padding(16.dp)) { ImportOutcomeCard(ImportOutcome.Success(ImportKind.DRIFT_EXPORT, importReport), {}, {}) }
    }
    @Test fun importFailure() = shoot("dialog_import_failed") {
        Column(Modifier.padding(16.dp)) { ImportOutcomeCard(ImportOutcome.Failure(ImportKind.SLEEP_AS_ANDROID, "Not a Sleep as Android backup (line 3)"), {}, {}) }
    }
    @Test fun resetEmpty() = shoot("dialog_reset_empty") {
        Column(Modifier.padding(16.dp)) { ResetConfirmCard("", {}, {}, {}) }
    }
    @Test fun resetTyped() = shoot("dialog_reset_typed") {
        Column(Modifier.padding(16.dp)) { ResetConfirmCard("RESET", {}, {}, {}) }
    }

    @Test fun scheduleReady() = shoot("schedule_ready") {
        ScheduleScreen(ScreenState.Ready(ScheduleModel(ScheduleForm.Default, false, null)), true, ScheduleActions())
    }
    @Test fun scheduleSaveError() = shoot("schedule_error") {
        ScheduleScreen(ScreenState.Ready(ScheduleModel(ScheduleForm.Default, false, "disk full")), true, ScheduleActions())
    }
    @Test fun scheduleLoading() = shoot("schedule_loading") { ScheduleScreen(ScreenState.Loading, true, ScheduleActions()) }

    @Test fun methodologyCollapsed() = shoot("methodology_collapsed") { MethodologyScreen({}) }
    @Test fun methodologyExpanded() = shoot("methodology_expanded") { MethodologyScreen({}, initiallyExpanded = setOf(0, 2)) }
    @Test fun licenses() = shoot("licenses") { LicensesScreen({}) }
}
