package app.drift.feature.energy.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftTheme
import app.drift.feature.energy.R
import app.drift.model.ConfidenceLevel
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.HabitKind
import java.time.Instant
import java.time.ZoneId

/** Clock formatting bound to the snapshot zone and the 12/24 hour setting. */
@Immutable
internal data class TimeFormat(val zone: ZoneId, val use24h: Boolean) {
    fun clock(at: Instant): String = DriftFormat.clock(at, zone, use24h)
}

@StringRes
internal fun EnergyPhase.labelRes(): Int = when (this) {
    EnergyPhase.GROGGINESS -> R.string.energy_phase_groggy
    EnergyPhase.MORNING_PEAK -> R.string.energy_phase_morning_peak
    EnergyPhase.AFTERNOON_DIP -> R.string.energy_phase_afternoon_dip
    EnergyPhase.EVENING_PEAK -> R.string.energy_phase_evening_peak
    EnergyPhase.WIND_DOWN -> R.string.energy_phase_wind_down
    EnergyPhase.MELATONIN_WINDOW -> R.string.energy_phase_melatonin
    EnergyPhase.SLEEP -> R.string.energy_phase_sleep
}

/** Peaks read as energy, the dip as primary, the melatonin window in its own colour. */
@Composable
internal fun EnergyPhase.color(): Color = when (this) {
    EnergyPhase.MORNING_PEAK, EnergyPhase.EVENING_PEAK -> DriftTheme.colors.energy
    EnergyPhase.AFTERNOON_DIP -> DriftTheme.colors.primary
    EnergyPhase.MELATONIN_WINDOW -> DriftTheme.colors.melatonin
    EnergyPhase.WIND_DOWN -> DriftTheme.colors.windDown
    else -> DriftTheme.colors.onSurface
}

@StringRes
internal fun ConfidenceLevel.labelRes(): Int = when (this) {
    ConfidenceLevel.LOW -> R.string.energy_confidence_low
    ConfidenceLevel.MEDIUM -> R.string.energy_confidence_medium
    ConfidenceLevel.HIGH -> R.string.energy_confidence_high
}

@StringRes
internal fun HabitKind.titleRes(): Int = when (this) {
    HabitKind.CAFFEINE_CUTOFF -> R.string.energy_habit_caffeine
    HabitKind.LAST_MEAL -> R.string.energy_habit_meal
    HabitKind.ALCOHOL_CUTOFF -> R.string.energy_habit_alcohol
    HabitKind.MORNING_LIGHT -> R.string.energy_habit_light
    HabitKind.EXERCISE_WINDOW -> R.string.energy_habit_exercise
    HabitKind.DIM_LIGHTS -> R.string.energy_habit_dim
}
