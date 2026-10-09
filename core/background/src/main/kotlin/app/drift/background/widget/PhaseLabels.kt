package app.drift.background.widget

import android.content.Context
import app.drift.background.R
import app.drift.model.engine.EnergyPhase

internal object PhaseLabels {
    fun of(context: Context, phase: EnergyPhase): String = context.getString(
        when (phase) {
            EnergyPhase.GROGGINESS -> R.string.phase_groggy
            EnergyPhase.MORNING_PEAK -> R.string.phase_morning_peak
            EnergyPhase.AFTERNOON_DIP -> R.string.phase_afternoon_dip
            EnergyPhase.EVENING_PEAK -> R.string.phase_evening_peak
            EnergyPhase.WIND_DOWN -> R.string.phase_wind_down
            EnergyPhase.MELATONIN_WINDOW -> R.string.phase_melatonin
            EnergyPhase.SLEEP -> R.string.phase_sleep
        },
    )
}
