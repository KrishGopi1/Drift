package app.drift.feature.sleep.night

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftListRow
import app.drift.designsystem.DriftTheme
import app.drift.feature.sleep.R
import app.drift.feature.sleep.common.CardTitle
import app.drift.feature.sleep.common.shortLabel
import app.drift.designsystem.DriftFormat

private enum class Field { START, END }

/**
 * Two tappable time rows with validation text. Picking opens the Material time picker; the result goes to
 * [onStartChange] / [onEndChange] in minutes after midnight.
 */
@Composable
fun EpisodeEditor(
    state: EditorUiState,
    use24h: Boolean,
    startLabel: String,
    endLabel: String,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf<Field?>(null) }
    val draft = state.draft
    DriftCard(modifier) {
        CardTitle(stringResource(R.string.night_edit_title))
        DriftListRow(
            title = startLabel,
            subtitle = stringResource(
                R.string.night_edit_when,
                draft.startLocal.toLocalDate().shortLabel(),
                DriftFormat.clockMinutes(draft.startMinutes, use24h),
            ),
            onClick = { picking = Field.START },
            onClickLabel = stringResource(R.string.night_edit_pick_time),
        )
        DriftListRow(
            title = endLabel,
            subtitle = stringResource(
                R.string.night_edit_when,
                draft.endLocal.toLocalDate().shortLabel(),
                DriftFormat.clockMinutes(draft.endMinutes, use24h),
            ),
            onClick = { picking = Field.END },
            onClickLabel = stringResource(R.string.night_edit_pick_time),
        )
        Text(
            stringResource(R.string.night_edit_length, DriftFormat.duration(draft.duration)),
            style = DriftTheme.typography.bodySmall,
            color = DriftTheme.colors.onSurfaceMuted,
        )
        Column(
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
        ) {
            state.errors.forEach { error ->
                Text(errorText(error), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.error)
            }
            if (state.saveFailed) {
                Text(stringResource(R.string.sleep_save_failed), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.error)
            }
        }
    }
    picking?.let { field ->
        val initial = if (field == Field.START) draft.startMinutes else draft.endMinutes
        TimePickerDialog(
            title = if (field == Field.START) startLabel else endLabel,
            initialMinutes = initial,
            use24h = use24h,
            onConfirm = { minutes ->
                if (field == Field.START) onStartChange(minutes) else onEndChange(minutes)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun errorText(error: EditError): String = stringResource(
    when (error) {
        EditError.END_NOT_AFTER_START -> R.string.night_error_order
        EditError.TOO_SHORT -> R.string.night_error_short
        EditError.TOO_LONG -> R.string.night_error_long
        EditError.ENDS_IN_FUTURE -> R.string.night_error_future
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinutes: Int,
    use24h: Boolean,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = use24h)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DriftTheme.colors.surfaceRaised,
        title = { Text(title, style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface) },
        text = { TimePicker(picker) },
        confirmButton = { DriftButton(stringResource(R.string.night_pick_ok), { onConfirm(picker.hour * 60 + picker.minute) }) },
        dismissButton = { DriftButton(stringResource(R.string.night_cancel), onDismiss, style = ButtonStyle.Text) },
    )
}

/** Save and Cancel, pinned under the scrolling content while editing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorButtons(
    canSave: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
    ) {
        DriftButton(stringResource(R.string.night_save), onSave, enabled = canSave)
        DriftButton(stringResource(R.string.night_cancel), onCancel, style = ButtonStyle.Text)
    }
}
