package app.drift.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.drift.designsystem.DriftBottomNavBar
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.driftDefaultNavItems
import app.drift.feature.energy.navigation.ENERGY_ROUTE
import app.drift.feature.energy.navigation.energyScreen
import app.drift.feature.onboarding.navigation.ONBOARDING_ROUTE
import app.drift.feature.onboarding.navigation.onboardingGraph
import app.drift.feature.settings.navigation.SETTINGS_METHODOLOGY_ROUTE
import app.drift.feature.settings.navigation.SETTINGS_ROUTE
import app.drift.feature.settings.navigation.settingsGraph
import app.drift.feature.sleep.navigation.BODY_CLOCK_ROUTE
import app.drift.feature.sleep.navigation.SLEEP_ROUTE
import app.drift.feature.sleep.navigation.navigateToNight
import app.drift.feature.sleep.navigation.navigateToWhy
import app.drift.feature.sleep.navigation.sleepGraph
import app.drift.fix.rememberSourceFixHandler

/** Top-level tabs in bottom-bar order; must match driftDefaultNavItems() (Energy, Sleep, Body clock, Settings). */
private val TAB_ROUTES = listOf(ENERGY_ROUTE, SLEEP_ROUTE, BODY_CLOCK_ROUTE, SETTINGS_ROUTE)

@Composable
fun DriftNavHost(
    onboardingComplete: Boolean,
    deepLink: DeepLink?,
    onDeepLinkHandled: () -> Unit,
    onOnboardingFinished: () -> Unit,
    onResetComplete: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val onFix = rememberSourceFixHandler(
        onResolved = {},
        onImportBackup = { navController.navigateToTab(SETTINGS_ROUTE) },
    )
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val tabIndex = TAB_ROUTES.indexOf(currentRoute)

    if (onboardingComplete) {
        LaunchedEffect(deepLink) {
            when (deepLink) {
                DeepLink.Energy -> navController.navigateToTab(ENERGY_ROUTE)
                is DeepLink.EditSleep -> deepLink.episodeId?.let(navController::navigateToNight)
                    ?: navController.navigateToTab(SLEEP_ROUTE)
                null -> return@LaunchedEffect
            }
            onDeepLinkHandled()
        }
    }

    Scaffold(
        containerColor = DriftTheme.colors.background,
        bottomBar = {
            if (tabIndex >= 0) {
                DriftBottomNavBar(
                    items = driftDefaultNavItems(),
                    selectedIndex = tabIndex,
                    onSelect = { navController.navigateToTab(TAB_ROUTES[it]) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = if (onboardingComplete) ENERGY_ROUTE else ONBOARDING_ROUTE,
            ) {
                onboardingGraph(
                    onFinished = {
                        onOnboardingFinished()
                        navController.navigate(ENERGY_ROUTE) { popUpTo(ONBOARDING_ROUTE) { inclusive = true } }
                    },
                    onFix = onFix,
                )
                energyScreen(
                    onExplain = navController::navigateToWhy,
                    onOpenNight = navController::navigateToNight,
                    onFix = onFix,
                    onStartSetup = { navController.navigate(ONBOARDING_ROUTE) },
                    onOpenSources = { navController.navigateToTab(SETTINGS_ROUTE) },
                )
                sleepGraph(
                    navController = navController,
                    onFix = onFix,
                    onOpenMethodology = { navController.navigate(SETTINGS_METHODOLOGY_ROUTE) },
                )
                settingsGraph(
                    navController = navController,
                    onFix = onFix,
                    onResetComplete = {
                        onResetComplete()
                        navController.navigate(ONBOARDING_ROUTE) { popUpTo(0) { inclusive = true } }
                    },
                )
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
