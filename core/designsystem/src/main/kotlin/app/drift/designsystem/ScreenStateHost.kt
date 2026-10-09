package app.drift.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.drift.model.repository.SourceFix
import app.drift.model.ui.ScreenState

/**
 * Renders the right panel for every C5 [ScreenState] variant and calls [ready] with the model for Ready.
 * Every feature screen puts its content inside this host so all six states look and behave the same.
 *
 * - Loading: skeleton cards (override with [loading]).
 * - FirstUse: "Continue setup" panel; [onStartSetup] runs when pressed.
 * - Insufficient: explanation plus a "nights needed" counter.
 * - SourceProblem: message plus one fix button, which calls [onFix].
 * - Error: message plus Try again (only when `retryable`), which calls [onRetry].
 */
@Composable
fun <T> ScreenStateHost(
    state: ScreenState<T>,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onFix: (SourceFix) -> Unit = {},
    onStartSetup: () -> Unit = {},
    loading: @Composable () -> Unit = { LoadingSkeleton() },
    ready: @Composable (T) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when (state) {
            ScreenState.Loading -> loading()
            is ScreenState.FirstUse -> FirstUsePanel(state.stepsRemaining, onStartSetup)
            is ScreenState.Insufficient -> InsufficientDataPanel(state.reason, state.daysNeeded)
            is ScreenState.Ready -> ready(state.model)
            is ScreenState.SourceProblem -> SourceProblemPanel(state.message, state.fix, onFix)
            is ScreenState.Error -> ErrorPanel(state.message, onRetry = if (state.retryable) onRetry else null)
        }
    }
}
