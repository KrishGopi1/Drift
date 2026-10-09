package app.drift.charts

import app.drift.model.engine.EnergyPhase
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** English display name of an energy phase (also used in chart descriptions). */
fun EnergyPhase.displayName(): String = when (this) {
    EnergyPhase.GROGGINESS -> "Waking up"
    EnergyPhase.MORNING_PEAK -> "Morning peak"
    EnergyPhase.AFTERNOON_DIP -> "Afternoon dip"
    EnergyPhase.EVENING_PEAK -> "Evening peak"
    EnergyPhase.WIND_DOWN -> "Wind-down"
    EnergyPhase.MELATONIN_WINDOW -> "Melatonin window"
    EnergyPhase.SLEEP -> "Sleep"
}

/** "Tue 6 Oct". */
fun shortDate(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + date.dayOfMonth + " " +
        date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

/** "Friday 9 October". */
fun longDate(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + date.dayOfMonth + " " +
        date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

/** "9 Oct". */
fun dayMonth(date: LocalDate): String = date.dayOfMonth.toString() + " " + date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

/** Single-letter weekday: M T W T F S S. */
fun weekdayInitial(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
