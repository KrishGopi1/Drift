package app.drift.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.drift.feature.onboarding.OnboardingRoute
import app.drift.model.repository.SourceFix

const val ONBOARDING_ROUTE = "onboarding"

/**
 * Registers the onboarding flow. [onFinished] runs after setup is saved as complete. [onFix] receives every
 * system-level fix the flow needs (install Health Connect, grant permissions, usage access) and is implemented by `:app`.
 */
fun NavGraphBuilder.onboardingGraph(onFinished: () -> Unit, onFix: (SourceFix) -> Unit) {
    composable(ONBOARDING_ROUTE) { OnboardingRoute(onFinished = onFinished, onFix = onFix) }
}
