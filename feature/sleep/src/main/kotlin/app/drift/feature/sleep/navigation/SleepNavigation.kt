package app.drift.feature.sleep.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import app.drift.feature.sleep.bodyclock.BodyClockActions
import app.drift.feature.sleep.bodyclock.BodyClockScreen
import app.drift.feature.sleep.bodyclock.BodyClockViewModel
import app.drift.feature.sleep.history.HistoryActions
import app.drift.feature.sleep.history.HistoryScreen
import app.drift.feature.sleep.history.HistoryViewModel
import app.drift.feature.sleep.night.AddEntryActions
import app.drift.feature.sleep.night.AddEntryScreen
import app.drift.feature.sleep.night.AddEntryViewModel
import app.drift.feature.sleep.night.NightDetailActions
import app.drift.feature.sleep.night.NightDetailScreen
import app.drift.feature.sleep.night.NightDetailViewModel
import app.drift.feature.sleep.night.NightEvent
import app.drift.feature.sleep.sleep.SleepActions
import app.drift.feature.sleep.sleep.SleepScreen
import app.drift.feature.sleep.sleep.SleepViewModel
import app.drift.feature.sleep.why.WhyActions
import app.drift.feature.sleep.why.WhySheet
import app.drift.feature.sleep.why.WhyViewModel
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.engine.MetricKey
import app.drift.model.repository.SourceFix
import java.time.Clock
import java.time.LocalDate

/** Sleep tab. */
const val SLEEP_ROUTE = "sleep"

/** History: week / month / 3-month review. Reached from the Sleep screen. */
const val HISTORY_ROUTE = "sleep/history"

/** Body clock tab. */
const val BODY_CLOCK_ROUTE = "bodyclock"

internal const val NIGHT_PATTERN = "sleep/night/{${NightDetailViewModel.ARG_EPISODE_ID}}"
internal const val WHY_PATTERN = "sleep/why/{${WhyViewModel.ARG_METRIC}}"
internal const val ADD_PATTERN = "sleep/add?${AddEntryViewModel.ARG_DATE}={${AddEntryViewModel.ARG_DATE}}&${AddEntryViewModel.ARG_KIND}={${AddEntryViewModel.ARG_KIND}}"

/** Opens night detail (and from there edit, nap, free day and delete) for [id]. */
fun NavHostController.navigateToNight(id: EpisodeId) {
    navigate("sleep/night/${Uri.encode(id.value)}")
}

/** Opens the "Why this changed" bottom sheet for [metric]. */
fun NavHostController.navigateToWhy(metric: MetricKey) {
    navigate("sleep/why/${metric.name}")
}

internal fun NavHostController.navigateToAdd(date: LocalDate, kind: EpisodeKind) {
    navigate("sleep/add?${AddEntryViewModel.ARG_DATE}=$date&${AddEntryViewModel.ARG_KIND}=${kind.name}")
}

/**
 * Registers the Sleep, History, Night detail, Add night or nap, Body clock and "Why this changed" destinations.
 * [onFix] handles the fix button of a SourceProblem state. [onOpenMethodology] shows "How this is calculated" in the
 * why sheet when the host can open Settings > Methodology; leave it null to hide that button.
 */
fun NavGraphBuilder.sleepGraph(
    navController: NavHostController,
    onFix: (SourceFix) -> Unit,
    onOpenMethodology: ((MetricKey) -> Unit)? = null,
) {
    composable(SLEEP_ROUTE) {
        val vm: SleepViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        SleepScreen(
            state = state,
            actions = SleepActions(
                onExplain = navController::navigateToWhy,
                onOpenHistory = { navController.navigate(HISTORY_ROUTE) },
                onOpenBodyClock = { navController.navigate(BODY_CLOCK_ROUTE) { launchSingleTop = true } },
                onOpenNight = navController::navigateToNight,
                onAddNight = { navController.navigateToAdd(it, EpisodeKind.MAIN) },
                onRetry = vm::retry,
                onFix = onFix,
            ),
        )
    }
    composable(HISTORY_ROUTE) {
        val vm: HistoryViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        HistoryScreen(
            state = state,
            today = LocalDate.now(Clock.systemDefaultZone()),
            actions = HistoryActions(
                onBack = { navController.popBackStack() },
                onSelectRange = vm::selectRange,
                onOpenNight = navController::navigateToNight,
                onAddNight = { navController.navigateToAdd(it, EpisodeKind.MAIN) },
                onRetry = vm::retry,
                onFix = onFix,
            ),
        )
    }
    composable(
        NIGHT_PATTERN,
        arguments = listOf(navArgument(NightDetailViewModel.ARG_EPISODE_ID) { type = NavType.StringType }),
    ) {
        val vm: NightDetailViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        val editor by vm.editor.collectAsStateWithLifecycle()
        LaunchedEffect(vm) {
            vm.events.collect { event -> if (event == NightEvent.DELETED) navController.popBackStack() }
        }
        NightDetailScreen(
            state = state,
            editor = editor,
            actions = NightDetailActions(
                onBack = { navController.popBackStack() },
                onEdit = vm::startEdit,
                onCancelEdit = vm::cancelEdit,
                onSave = vm::save,
                onStartChange = vm::setStart,
                onEndChange = vm::setEnd,
                onAddNap = { navController.navigateToAdd(it, EpisodeKind.NAP) },
                onFreeDayChange = vm::setFreeDay,
                onDelete = vm::delete,
                onUseSource = vm::useSource,
                onRetry = vm::retry,
            ),
        )
    }
    composable(
        ADD_PATTERN,
        arguments = listOf(
            navArgument(AddEntryViewModel.ARG_DATE) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(AddEntryViewModel.ARG_KIND) { type = NavType.StringType; defaultValue = EpisodeKind.MAIN.name },
        ),
    ) {
        val vm: AddEntryViewModel = hiltViewModel()
        val editor by vm.editor.collectAsStateWithLifecycle()
        val use24h by vm.use24hSetting.collectAsStateWithLifecycle()
        LaunchedEffect(vm) { vm.saved.collect { navController.popBackStack() } }
        AddEntryScreen(
            kind = vm.kind,
            editor = editor,
            use24hSetting = use24h,
            actions = AddEntryActions(
                onBack = { navController.popBackStack() },
                onStartChange = vm::setStart,
                onEndChange = vm::setEnd,
                onSave = vm::save,
            ),
        )
    }
    composable(BODY_CLOCK_ROUTE) {
        val vm: BodyClockViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        BodyClockScreen(
            state = state,
            actions = BodyClockActions(onExplain = navController::navigateToWhy, onRetry = vm::retry, onFix = onFix),
        )
    }
    dialog(
        WHY_PATTERN,
        arguments = listOf(navArgument(WhyViewModel.ARG_METRIC) { type = NavType.StringType }),
        dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val vm: WhyViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        // The bottom sheet draws its own scrim, so the hosting dialog window must not dim a second time.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        LaunchedEffect(vm) {
            vm.openNight.collect { id ->
                navController.popBackStack()
                navController.navigateToNight(id)
            }
        }
        WhySheet(
            metric = vm.metric,
            state = state,
            actions = WhyActions(
                onDismiss = { navController.popBackStack() },
                onOpenDate = vm::openNight,
                onRetry = vm::retry,
                onOpenMethodology = onOpenMethodology?.let { open ->
                    { metric ->
                        navController.popBackStack()
                        open(metric)
                    }
                },
            ),
        )
    }
}
