package app.drift.feature.settings

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftDivider
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.DriftTopBar
import app.drift.designsystem.ErrorPanel
import app.drift.designsystem.ScreenStateHost
import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.ui.ScreenState
import java.time.DayOfWeek

data class ScheduleActions(
    val onBack: () -> Unit = {},
    val onToggleWorkday: (DayOfWeek) -> Unit = {},
    val onWake: (Int) -> Unit = {},
    val onFreeBed: (Int) -> Unit = {},
    val onFreeWake: (Int) -> Unit = {},
    val onAge: (AgeBand) -> Unit = {},
    val onChronotype: (ChronotypeAnswer?) -> Unit = {},
    val onSave: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

@Composable
fun ScheduleRoute(onBack: () -> Unit, viewModel: ScheduleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect { onBack() } }
    ScheduleScreen(
        state = state, use24h = null,
        actions = ScheduleActions(
            onBack = onBack, onToggleWorkday = viewModel::toggleWorkday, onWake = viewModel::setWake,
            onFreeBed = viewModel::setFreeBed, onFreeWake = viewModel::setFreeWake, onAge = viewModel::setAgeBand,
            onChronotype = viewModel::setChronotype, onSave = viewModel::save, onRetry = viewModel::load,
        ),
    )
}

/** [use24h] null follows the system clock setting. */
@Composable
fun ScheduleScreen(state: ScreenState<ScheduleModel>, use24h: Boolean?, actions: ScheduleActions) {
    val resolved24h = use24h ?: DateFormat.is24HourFormat(LocalContext.current)
    Column(Modifier.fillMaxSize().background(DriftTheme.colors.background)) {
        DriftTopBar(stringResource(R.string.st_schedule_screen_title), onBack = actions.onBack, applyStatusBarInsets = true)
        ScreenStateHost(
            state = state, modifier = Modifier.weight(1f).padding(horizontal = DriftTheme.spacing.gutter), onRetry = actions.onRetry,
        ) { model ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = DriftTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sectionGap),
            ) {
                model.saveError?.let { ErrorPanel(stringResource(R.string.st_schedule_save_failed, it.ifBlank { stringResource(R.string.st_unknown_error) })) }
                val form = model.form
                DriftCard {
                    Text(stringResource(R.string.st_workdays_label), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
                    WorkdayChips(form.workdays, actions.onToggleWorkday)
                    if (form.workdays.isEmpty()) {
                        Text(stringResource(R.string.st_workdays_none), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
                    } else {
                        TimeStepper(stringResource(R.string.st_wake_workday), form.wakeMinutes, resolved24h, actions.onWake)
                    }
                }
                DriftCard {
                    TimeStepper(stringResource(R.string.st_bed_free), form.freeBedMinutes, resolved24h, actions.onFreeBed)
                    DriftDivider()
                    TimeStepper(stringResource(R.string.st_wake_free), form.freeWakeMinutes, resolved24h, actions.onFreeWake)
                }
                DriftCard { ChoiceGroup(stringResource(R.string.st_age_label), AgeBand.entries.map { it to ageBandLabel(it) }, form.ageBand, actions.onAge) }
                DriftCard {
                    ChoiceGroup(
                        stringResource(R.string.st_chrono_label),
                        (ChronotypeAnswer.entries + null).map { it to chronotypeLabel(it) }, form.chronotype, actions.onChronotype,
                    )
                }
                DriftButton(
                    stringResource(if (model.saving) R.string.st_schedule_saving else R.string.st_schedule_save), actions.onSave,
                    modifier = Modifier.fillMaxWidthCompat(), enabled = !model.saving,
                )
            }
        }
    }
}

private fun Modifier.fillMaxWidthCompat(): Modifier = this.then(Modifier.fillMaxWidth())
