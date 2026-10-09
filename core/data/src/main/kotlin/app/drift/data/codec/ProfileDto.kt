package app.drift.data.codec

import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.engine.Goals
import app.drift.model.engine.Schedule
import app.drift.model.engine.UserProfile
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

@Serializable
data class ProfileDto(
    val ageBand: AgeBand,
    val chronotype: ChronotypeAnswer?,
    val workdays: List<DayOfWeek>,
    val wakeTime: String?,
    val freeDayBedtime: String?,
    val freeDayWakeTime: String?,
    val targetSleep: String?,
    val remindersEnabled: Boolean,
)

fun UserProfile.toDto() = ProfileDto(
    ageBand, chronotype, schedule.workdays.sorted(), schedule.wakeTime?.toString(),
    schedule.freeDayBedtime?.toString(), schedule.freeDayWakeTime?.toString(),
    goals.targetSleep?.toString(), goals.remindersEnabled,
)

fun ProfileDto.toModel() = UserProfile(
    ageBand, chronotype,
    Schedule(workdays.toSet(), wakeTime?.let(LocalTime::parse), freeDayBedtime?.let(LocalTime::parse), freeDayWakeTime?.let(LocalTime::parse)),
    Goals(targetSleep?.let(Duration::parse), remindersEnabled),
)
