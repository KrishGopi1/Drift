package app.drift.feature.sleep.night

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.drift.designsystem.DriftTopBar
import app.drift.feature.sleep.R
import app.drift.feature.sleep.common.ScreenBody
import app.drift.feature.sleep.common.rememberUse24h
import app.drift.model.EpisodeKind

data class AddEntryActions(
    val onBack: () -> Unit = {},
    val onStartChange: (Int) -> Unit = {},
    val onEndChange: (Int) -> Unit = {},
    val onSave: () -> Unit = {},
)

/** Form for a missing night or a nap. Saving writes a USER_ENTERED episode. */
@Composable
fun AddEntryScreen(
    kind: EpisodeKind,
    editor: EditorUiState,
    use24hSetting: Boolean?,
    actions: AddEntryActions,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    val use24h = rememberUse24h(use24hSetting)
    val nap = kind == EpisodeKind.NAP
    ScreenBody(
        modifier = modifier,
        scrollable = scrollable,
        topBar = {
            DriftTopBar(
                title = stringResource(if (nap) R.string.add_nap_title else R.string.add_night_title),
                onBack = actions.onBack,
            )
        },
        bottomBar = { EditorButtons(editor.canSave, actions.onSave, actions.onBack) },
    ) {
        EpisodeEditor(
            state = editor,
            use24h = use24h,
            startLabel = stringResource(if (nap) R.string.add_nap_start else R.string.night_bedtime),
            endLabel = stringResource(if (nap) R.string.add_nap_end else R.string.night_wake),
            onStartChange = actions.onStartChange,
            onEndChange = actions.onEndChange,
        )
    }
}
