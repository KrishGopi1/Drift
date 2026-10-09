package app.drift.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import app.drift.feature.settings.ArticleScreen
import app.drift.feature.settings.LearnScreen
import app.drift.feature.settings.LicensesScreen
import app.drift.feature.settings.MethodologyScreen
import app.drift.feature.settings.ScheduleRoute
import app.drift.feature.settings.SettingsRoute
import app.drift.model.repository.SourceFix

const val SETTINGS_ROUTE = "settings"
const val SETTINGS_SCHEDULE_ROUTE = "settings/schedule"
const val SETTINGS_METHODOLOGY_ROUTE = "settings/methodology"
const val SETTINGS_LICENSES_ROUTE = "settings/licenses"

/** Learn tab and its articles. */
const val LEARN_ROUTE = "learn"
internal const val LEARN_ARTICLE_PATTERN = "learn/article/{index}"

/**
 * Registers settings and its sub-screens (schedule, methodology, licences). [onFix] runs system-level fixes and is
 * implemented by `:app`. [onResetComplete] runs after all data was wiped; `:app` should restart at onboarding.
 */
fun NavGraphBuilder.settingsGraph(
    navController: NavHostController,
    onFix: (SourceFix) -> Unit,
    onResetComplete: () -> Unit,
    onOpenBodyClock: () -> Unit = {},
) {
    composable(LEARN_ROUTE) {
        LearnScreen(
            onOpenArticle = { navController.navigate("learn/article/$it") },
            onOpenMethodology = { navController.navigate(SETTINGS_METHODOLOGY_ROUTE) },
            onOpenBodyClock = onOpenBodyClock,
            onOpenSettings = { navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } },
        )
    }
    composable(LEARN_ARTICLE_PATTERN, arguments = listOf(navArgument("index") { type = NavType.IntType })) { entry ->
        ArticleScreen(index = entry.arguments?.getInt("index") ?: 0, onBack = { navController.popBackStack() })
    }
    composable(SETTINGS_ROUTE) {
        SettingsRoute(
            onBack = { navController.popBackStack() },
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
