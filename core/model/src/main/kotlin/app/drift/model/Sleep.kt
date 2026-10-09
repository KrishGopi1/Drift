package app.drift.model

import java.time.Instant
import java.time.ZoneId

/** C2. Ranking of data sources; higher wins when episodes overlap. Order matters. */
enum class QualityTier { PHONE_INFERRED, PHONE_APP, WEARABLE_WRIST, WEARABLE_RING }

enum class EpisodeKind { MAIN, NAP }

enum class SleepStage { AWAKE, LIGHT, DEEP, REM, UNKNOWN }

data class StageSpan(val stage: SleepStage, val interval: Interval)

/** Where a record came from. [dataOrigin] is e.g. the Health Connect package name of the writer. */
data class SourceRef(
    val sourceId: SourceId,
    val recordId: String,
    val qualityTier: QualityTier,
    val dataOrigin: String? = null,
    val device: String? = null,
)

/**
 * [completeness] 0..1: fraction of the episode covered by real data (vs gaps).
 * [detectionConfidence] 0..1: for inferred episodes, how sure the detector is; 1.0 for recorded sessions.
 */
data class DataQuality(
    val completeness: Double,
    val detectionConfidence: Double,
    val flags: Set<QualityFlag> = emptySet(),
) {
    init {
        require(completeness in 0.0..1.0) { "completeness must be in 0..1" }
        require(detectionConfidence in 0.0..1.0) { "detectionConfidence must be in 0..1" }
    }

    companion object {
        val RECORDED = DataQuality(1.0, 1.0)
    }
}

enum class QualityFlag {
    /** Main sleep under 2 h or over 16 h; excluded from analysis unless the user confirmed it. */
    IMPLAUSIBLE_DURATION,
    ENDS_IN_FUTURE,
    /** User tapped "confirm" on the morning card. */
    USER_CONFIRMED,
    /** Several sources disagreed on this span and a lower tier was overridden. */
    SOURCE_CONFLICT,
}

/** C2. One canonical sleep episode after merge. Times are UTC instants; [zone] is the local zone at the time. */
data class SleepEpisode(
    val id: EpisodeId,
    val start: Instant,
    val end: Instant,
    val zone: ZoneId,
    val kind: EpisodeKind,
    val stages: List<StageSpan>? = null,
    val inBed: Interval? = null,
    val origin: SourceRef,
    val quality: DataQuality = DataQuality.RECORDED,
    val userEdited: Boolean = false,
) {
    init { require(end.isAfter(start)) { "Episode end must be after start" } }
    val interval: Interval get() = Interval(start, end)
}

/** Per-day annotations the user (or the alarm detector) supplies. Keyed by sleep day (date of wake). */
data class DayAnnotation(
    val date: java.time.LocalDate,
    val freeDay: Boolean? = null,
    val alarmSet: Boolean? = null,
    val alarmDismissedAt: Instant? = null,
)

/** Calibration input (plan 4.6): an energy check-in or a reaction-time test result. */
sealed interface CheckIn {
    val at: Instant

    /** 1 (exhausted) to 5 (very alert). */
    data class EnergyRating(override val at: Instant, val rating: Int) : CheckIn {
        init { require(rating in 1..5) { "rating must be 1..5" } }
    }

    /** Brief psychomotor vigilance test: median reaction time and lapses (>500 ms). */
    data class ReactionTest(override val at: Instant, val medianRtMillis: Double, val lapses: Int, val trials: Int) : CheckIn
}
