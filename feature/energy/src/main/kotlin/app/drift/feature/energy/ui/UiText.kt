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

/** The instruction a habit window stands for: cutoffs are periods to avoid, so the sentence names the cutoff time. */
@Composable
internal fun habitInstruction(habit: app.drift.model.engine.HabitCard, fmt: TimeFormat): String {
    val start = fmt.clock(habit.window.start)
    val end = fmt.clock(habit.window.end)
    return when (habit.kind) {
        HabitKind.CAFFEINE_CUTOFF -> androidx.compose.ui.res.stringResource(R.string.energy_habit_do_caffeine, start)
        HabitKind.LAST_MEAL -> androidx.compose.ui.res.stringResource(R.string.energy_habit_do_meal, start)
        HabitKind.ALCOHOL_CUTOFF -> androidx.compose.ui.res.stringResource(R.string.energy_habit_do_alcohol, start)
        HabitKind.MORNING_LIGHT -> androidx.compose.ui.res.stringResource(R.string.energy_habit_do_light, start, end)
        HabitKind.EXERCISE_WINDOW -> androidx.compose.ui.res.stringResource(R.string.energy_habit_do_exercise, start, end)
        HabitKind.DIM_LIGHTS -> androidx.compose.ui.res.stringResource(R.string.energy_habit_do_dim, start)
    }
}

/** Resource for a known basis key (engine parameter key or citation key), or null. */
@StringRes
internal fun basisRes(key: String): Int? = when (key.lowercase().removePrefix("habit.")) {
    "caffeinecutoffhours", "drake2013" -> R.string.energy_basis_drake
    "alcoholcutoffhours", "ebrahim2013" -> R.string.energy_basis_ebrahim
    "morninglightwithinmin", "wright2013" -> R.string.energy_basis_wright2013
    "exerciseafterwakemin", "youngstedt2019" -> R.string.energy_basis_youngstedt
    "dimlightsfromwinddown", "gooley2011" -> R.string.energy_basis_gooley
    "sutton2018" -> R.string.energy_basis_sutton
    "lastmealhours" -> R.string.energy_basis_plan
    else -> null
}

/** "Author2019" becomes "Author 2019"; "habit.someKeyName" becomes "Some key name". */
internal fun cleanBasisKey(key: String): String {
    Regex("^([A-Za-z]+)(\\d{4})$").matchEntire(key)?.let { return "${it.groupValues[1]} ${it.groupValues[2]}" }
    val tail = key.substringAfterLast('.')
    val spaced = tail.replace(Regex("([a-z])([A-Z0-9])"), "$1 $2").replace('_', ' ').trim().lowercase()
    return spaced.replaceFirstChar { it.uppercase() }
}

@Composable
internal fun basisText(key: String): String {
    val res = basisRes(key)
    val line = if (res != null) androidx.compose.ui.res.stringResource(res) else cleanBasisKey(key)
    return androidx.compose.ui.res.stringResource(R.string.energy_habit_basis, line)
}
