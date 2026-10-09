package app.drift.engine.replay

import app.drift.model.CheckIn
import app.drift.model.DataQuality
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.SleepEpisode
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.engine.Goals
import app.drift.model.engine.Schedule
import app.drift.model.engine.UserProfile
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * JSON export consumed by the replay CLI (plan 9.2). Instants are ISO-8601 with offset or Z, dates are
 * yyyy-MM-dd and clock times HH:mm. Enum fields use the names from `app.drift.model`.
 */
@Serializable
data class ReplayFile(
    val formatVersion: Int = 1,
    val zone: String,
    val profile: ReplayProfile,
    val episodes: List<ReplayEpisode>,
    val annotations: List<ReplayAnnotation> = emptyList(),
    val checkIns: List<ReplayCheckIn> = emptyList(),
)

@Serializable
data class ReplayProfile(
    val ageBand: String,
    val chronotype: String? = null,
    val workdays: List<String> = emptyList(),
    val wakeTime: String? = null,
    val freeDayBedtime: String? = null,
    val freeDayWakeTime: String? = null,
    val targetSleepMinutes: Int? = null,
)

@Serializable
data class ReplayEpisode(
    val id: String,
    val start: String,
    val end: String,
    /** Zone the episode happened in; defaults to the file zone. */
    val zone: String? = null,
    val kind: String = "MAIN",
    val tier: String = "WEARABLE_WRIST",
    val source: String = "healthconnect",
    val userEdited: Boolean = false,
    val userConfirmed: Boolean = false,
    val completeness: Double = 1.0,
    val detectionConfidence: Double = 1.0,
)

@Serializable
data class ReplayAnnotation(
    val date: String,
    val freeDay: Boolean? = null,
    val alarmSet: Boolean? = null,
    val alarmDismissedAt: String? = null,
)

/** Either an energy rating (1..5) or a reaction test result. */
@Serializable
data class ReplayCheckIn(
    val at: String,
    val rating: Int? = null,
    val medianRtMillis: Double? = null,
    val lapses: Int? = null,
    val trials: Int? = null,
)

/** Typed form of a [ReplayFile], ready to feed the engine. */
class ReplayData(
    val zone: ZoneId,
    val profile: UserProfile,
    val episodes: List<SleepEpisode>,
    val annotations: List<DayAnnotation>,
    val checkIns: List<CheckIn>,
)

object ReplayConverter {
    fun convert(f: ReplayFile): ReplayData {
        val zone = ZoneId.of(f.zone)
        val p = f.profile
        val schedule = Schedule(
            workdays = p.workdays.map { DayOfWeek.valueOf(it.uppercase()) }.toSet(),
            wakeTime = p.wakeTime?.let { LocalTime.parse(it) },
            freeDayBedtime = p.freeDayBedtime?.let { LocalTime.parse(it) },
            freeDayWakeTime = p.freeDayWakeTime?.let { LocalTime.parse(it) },
        )
        val profile = UserProfile(
            ageBand = AgeBand.valueOf(p.ageBand),
            chronotype = p.chronotype?.let { ChronotypeAnswer.valueOf(it) },
            schedule = schedule,
            goals = Goals(targetSleep = p.targetSleepMinutes?.let { Duration.ofMinutes(it.toLong()) }),
        )
        val episodes = f.episodes.map { e ->
            val flags = if (e.userConfirmed) setOf(QualityFlag.USER_CONFIRMED) else emptySet()
            SleepEpisode(
                id = EpisodeId(e.id),
                start = parseInstant(e.start),
                end = parseInstant(e.end),
                zone = e.zone?.let { ZoneId.of(it) } ?: zone,
                kind = EpisodeKind.valueOf(e.kind),
                origin = SourceRef(SourceId(e.source), e.id, QualityTier.valueOf(e.tier)),
                quality = DataQuality(e.completeness, e.detectionConfidence, flags),
                userEdited = e.userEdited,
            )
        }
        val annotations = f.annotations.map {
            DayAnnotation(LocalDate.parse(it.date), it.freeDay, it.alarmSet, it.alarmDismissedAt?.let(::parseInstant))
        }
        val checkIns = f.checkIns.mapNotNull { c ->
            val at = parseInstant(c.at)
            when {
                c.rating != null -> CheckIn.EnergyRating(at, c.rating)
                c.medianRtMillis != null -> CheckIn.ReactionTest(at, c.medianRtMillis, c.lapses ?: 0, c.trials ?: 0)
                else -> null
            }
        }
        return ReplayData(zone, profile, episodes, annotations, checkIns)
    }

    private fun parseInstant(s: String): Instant =
        if (s.endsWith("Z")) Instant.parse(s) else java.time.OffsetDateTime.parse(s).toInstant()
}
