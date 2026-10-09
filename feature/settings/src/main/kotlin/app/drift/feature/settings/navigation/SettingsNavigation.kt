package app.drift.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.drift.feature.settings.LicensesScreen
import app.drift.feature.settings.MethodologyScreen
import app.drift.feature.settings.ScheduleRoute
import app.drift.feature.settings.SettingsRoute
import app.drift.model.repository.SourceFix

const val SETTINGS_ROUTE = "settings"
const val SETTINGS_SCHEDULE_ROUTE = "settings/schedule"
const val SETTINGS_METHODOLOGY_ROUTE = "settings/methodology"
const val SETTINGS_LICENSES_ROUTE = "settings/licenses"

/**
 * Registers settings and its sub-screens (schedule, methodology, licences). [onFix] runs system-level fixes and is
 * implemented by `:app`. [onResetComplete] runs after all data was wiped; `:app` should restart at onboarding.
 */
fun NavGraphBuilder.settingsGraph(
    navController: NavHostController,
    onFix: (SourceFix) -> Unit,
    onResetComplete: () -> Unit,
) {
    composable(SETTINGS_ROUTE) {
        SettingsRoute(
            onOpenSchedule = { navController.navigate(SETTINGS_SCHEDULE_ROUTE) },
            onOpenMethodology = { navController.navigate(SETTINGS_METHODOLOGY_ROUTE) },
            onOpenLicenses = { navController.navigate(SETTINGS_LICENSES_ROUTE) },
            onFix = onFix,
            onResetComplete = onResetComplete,
        )
    }
    composable(SETTINGS_SCHEDULE_ROUTE) { ScheduleRoute(onBack = { navController.popBackStack() }) }
    composable(SETTINGS_METHODOLOGY_ROUTE) { MethodologyScreen(onBack = { navController.popBackStack() }) }
    composable(SETTINGS_LICENSES_ROUTE) { LicensesScreen(onBack = { navController.popBackStack() }) }
}
