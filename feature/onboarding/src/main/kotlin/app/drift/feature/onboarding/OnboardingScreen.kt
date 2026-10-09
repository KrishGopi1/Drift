package app.drift.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftIconButton
import app.drift.designsystem.DriftIcons
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.ScreenStateHost
import app.drift.model.SourceId
import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.repository.SourceFix
import app.drift.model.ui.ScreenState
import java.time.DayOfWeek

/** Everything the onboarding UI can ask for. Screenshot tests use [None]. */
data class OnboardingActions(
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onToggleWorkday: (DayOfWeek) -> Unit = {},
    val onWake: (Int) -> Unit = {},
    val onFreeBed: (Int) -> Unit = {},
    val onFreeWake: (Int) -> Unit = {},
    val onAge: (AgeBand) -> Unit = {},
    val onChronotype: (ChronotypeAnswer?) -> Unit = {},
    val onSourceEnabled: (SourceId, Boolean) -> Unit = { _, _ -> },
    val onFix: (SourceFix) -> Unit = {},
    val onUsePhoneOnly: () -> Unit = {},
    val onRequestNotifications: () -> Unit = {},
    val onSkipPermission: () -> Unit = {},
    val onCancelImport: () -> Unit = {},
    val onRetryImport: () -> Unit = {},
    val onPickBackup: () -> Unit = {},
    val onRetryReveal: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
) {
    companion object { val None = OnboardingActions() }
}

/**
 * Stateful entry point used by the navigation graph. Owns the system launchers (notification permission, backup
 * file picker) and forwards everything else to [OnboardingViewModel].
 */
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    onFix: (SourceFix) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val model = (state as? ScreenState.Ready)?.model

    LaunchedEffect(viewModel) { viewModel.finished.collect { onFinished() } }
    LaunchedEffect(model?.permissionPage, model?.step) {
        if (model?.step == OnboardingStep.PERMISSIONS && model.permissionPage == PermissionPage.NOTIFICATIONS) {
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            viewModel.noteNotificationsGranted(granted)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onNotificationsResult(granted)
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importBackup { context.contentResolver.openInputStream(uri) }
    }

    BackHandler(enabled = model?.canGoBack == true) { viewModel.back() }

    OnboardingScreen(
        state = state,
        actions = OnboardingActions(
            onNext = viewModel::next,
            onBack = viewModel::back,
            onToggleWorkday = viewModel::toggleWorkday,
            onWake = viewModel::setWake,
            onFreeBed = viewModel::setFreeBed,
            onFreeWake = viewModel::setFreeWake,
            onAge = viewModel::setAgeBand,
            onChronotype = viewModel::setChronotype,
            onSourceEnabled = viewModel::setSourceEnabled,
            onFix = onFix,
            onUsePhoneOnly = viewModel::usePhoneDetectionOnly,
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.onNotificationsResult(true)
                }
            },
            onSkipPermission = viewModel::advancePermission,
            onCancelImport = viewModel::cancelImport,
            onRetryImport = viewModel::startImportAgain,
            onPickBackup = { backupPicker.launch(arrayOf("*/*")) },
            onRetryReveal = viewModel::loadReveal,
            onRetryLoad = viewModel::retryLoad,
        ),
    )
}

/** Stateless onboarding screen. Every C5 state goes through [ScreenStateHost]. */
@Composable
fun OnboardingScreen(state: ScreenState<OnboardingModel>, actions: OnboardingActions) {
    Box(Modifier.fillMaxSize().background(DriftTheme.colors.background).statusBarsPadding()) {
        ScreenStateHost(
            state = state,
            modifier = Modifier.fillMaxSize().padding(horizontal = DriftTheme.spacing.gutter),
            onRetry = actions.onRetryLoad,
            onFix = actions.onFix,
        ) { model -> OnboardingFlow(model, actions) }
    }
}

private data class Footer(
    val primary: String,
    val enabled: Boolean,
    val onPrimary: () -> Unit,
    val secondary: String? = null,
    val onSecondary: () -> Unit = {},
)

@Composable
private fun footerFor(model: OnboardingModel, actions: OnboardingActions): Footer? {
    val next = actions.onNext
    return when (model.step) {
        OnboardingStep.WELCOME -> Footer(stringResource(R.string.ob_get_started), true, next)
        OnboardingStep.HOW_IT_WORKS, OnboardingStep.SCHEDULE, OnboardingStep.ABOUT_YOU, OnboardingStep.SOURCES ->
            Footer(stringResource(R.string.ob_continue), !model.busy, next)
        OnboardingStep.PERMISSIONS ->
            if (permissionGranted(model, model.permissionPage)) Footer(stringResource(R.string.ob_continue), true, actions.onSkipPermission) else null
        OnboardingStep.IMPORT -> {
            val running = model.history is HistoryImport.Running || model.history is HistoryImport.Idle
            Footer(
                stringResource(R.string.ob_continue), !running && model.backup != BackupImport.Running, next,
                secondary = if (running) stringResource(R.string.ob_import_cancel) else null, onSecondary = actions.onCancelImport,
            )
        }
        OnboardingStep.REVEAL -> Footer(stringResource(R.string.ob_open_drift), !model.busy, next)
    }
}

@Composable
private fun OnboardingFlow(model: OnboardingModel, actions: OnboardingActions) {
    val reduced = DriftTheme.reducedMotion
    val motion = DriftTheme.motion
    val footer = footerFor(model, actions)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = DriftTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (model.canGoBack) {
                DriftIconButton(DriftIcons.Back, stringResource(R.string.ob_back), actions.onBack)
            } else {
                Spacer(Modifier.size(DriftTheme.spacing.minTouch))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { StepDots(model.stepNumber, model.stepCount) }
            Spacer(Modifier.size(DriftTheme.spacing.minTouch))
        }
        val key = model.step to (if (model.step == OnboardingStep.PERMISSIONS) model.permissionPage else null)
        AnimatedContent(
            targetState = key,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val forward = targetState.first.ordinal > initialState.first.ordinal ||
                        (targetState.first == initialState.first && (targetState.second?.ordinal ?: 0) >= (initialState.second?.ordinal ?: 0))
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(motion.medium, easing = motion.standard), { it / 4 * dir }) + fadeIn(tween(motion.medium, easing = motion.standard))) togetherWith
                        (slideOutHorizontally(tween(motion.medium, easing = motion.standard), { -it / 4 * dir }) + fadeOut(tween(motion.medium, easing = motion.standard)))
                }
            },
            label = "step",
        ) { target ->
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = DriftTheme.spacing.lg)) {
                StepBody(
                    model.copy(step = target.first, permissionPage = target.second ?: model.permissionPage),
                    actions,
                    Modifier.focusRequester(focus).focusable(),
                )
            }
        }
        if (footer != null) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = DriftTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DriftButton(footer.primary, footer.onPrimary, modifier = Modifier.fillMaxWidth(), enabled = footer.enabled)
                if (footer.secondary != null) DriftButton(footer.secondary, footer.onSecondary, style = ButtonStyle.Text)
            }
        }
    }
}
