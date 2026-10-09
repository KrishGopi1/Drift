package app.drift.feature.energy.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.drift.feature.energy.ui.EnergyActions
import app.drift.feature.energy.ui.EnergyRoute
import app.drift.model.EpisodeId
import app.drift.model.engine.MetricKey
import app.drift.model.repository.SourceFix

const val ENERGY_ROUTE = "energy"

/**
 * Registers the Energy home screen.
 *
 * [onStartSetup] and [onOpenSources] are optional extras beyond the ADR 0002 signature: the first leads into
 * onboarding from the first-use panel, the second to Settings > Sources from "Improve it". Without
 * [onOpenSources], "Improve it" opens the energy explanation instead.
 */
fun NavGraphBuilder.energyScreen(
    onExplain: (MetricKey) -> Unit,
    onOpenNight: (EpisodeId) -> Unit,
    onFix: (SourceFix) -> Unit,
    onStartSetup: () -> Unit = {},
    onOpenSources: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
) {
    composable(ENERGY_ROUTE) {
        EnergyRoute(
            EnergyActions(
                onExplain = onExplain,
                onOpenNight = onOpenNight,
                onFix = onFix,
                onStartSetup = onStartSetup,
                onOpenSources = onOpenSources,
                onOpenSettings = onOpenSettings,
            ),
        )
    }
}
