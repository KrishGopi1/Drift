package app.drift.model.engine

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

enum class AgeBand { AGE_18_25, AGE_26_35, AGE_36_50, AGE_51_64, AGE_65_PLUS }

/** Self-reported chronotype from onboarding (reduced MEQ-style single answer plus free-day sleep timing). */
enum class ChronotypeAnswer { DEFINITELY_MORNING, MODERATELY_MORNING, NEITHER, MODERATELY_EVENING, DEFINITELY_EVENING }

/** Workday schedule. [workdays] have a fixed [wakeTime]; other days are free days by default. */
data class Schedule(
    val workdays: Set<DayOfWeek>,
    val wakeTime: LocalTime?,
    /** Free-day sleep timing answered during onboarding; used for the chronotype prior. */
    val freeDayBedtime: LocalTime?,
    val freeDayWakeTime: LocalTime?,
)

data class Goals(
    /** User-chosen target sleep duration override; null = use the engine's estimated need. */
    val targetSleep: Duration? = null,
    val remindersEnabled: Boolean = true,
)

data class UserProfile(
    val ageBand: AgeBand,
    val chronotype: ChronotypeAnswer?,
    val schedule: Schedule,
    val goals: Goals = Goals(),
)
