package app.drift.data.codec

import app.drift.model.ActivityType
import app.drift.model.AlarmKind
import app.drift.model.CheckIn
import app.drift.model.DataQuality
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.Signal
import app.drift.model.SleepEpisode
import app.drift.model.SleepStage
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.StageSpan
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Serializable surrogates for the model types (the model classes carry no serialization annotations).
 * Instants are ISO-8601 strings so nanosecond precision survives a round trip exactly.
 */
@Serializable
data class SourceRefDto(
    val sourceId: String,
    val recordId: String,
    val tier: QualityTier,
    val dataOrigin: String? = null,
    val device: String? = null,
)

@Serializable
data class IntervalDto(val start: String, val end: String)

@Serializable
data class StageDto(val stage: SleepStage, val start: String, val end: String)

@Serializable
data class EpisodeDto(
    val id: String,
    val start: String,
    val end: String,
    val zone: String,
    val kind: EpisodeKind,
    val stages: List<StageDto>? = null,
    val inBed: IntervalDto? = null,
    val origin: SourceRefDto,
    val completeness: Double = 1.0,
    val detectionConfidence: Double = 1.0,
    val flags: List<QualityFlag> = emptyList(),
    val userEdited: Boolean = false,
)

fun Interval.toDto() = IntervalDto(start.toString(), end.toString())
fun IntervalDto.toModel() = Interval(Instant.parse(start), Instant.parse(end))

fun SourceRef.toDto() = SourceRefDto(sourceId.value, recordId, qualityTier, dataOrigin, device)
fun SourceRefDto.toModel() = SourceRef(SourceId(sourceId), recordId, tier, dataOrigin, device)

fun SleepEpisode.toDto() = EpisodeDto(
    id = id.value,
    start = start.toString(),
    end = end.toString(),
    zone = zone.id,
    kind = kind,
    stages = stages?.map { StageDto(it.stage, it.interval.start.toString(), it.interval.end.toString()) },
    inBed = inBed?.toDto(),
    origin = origin.toDto(),
    completeness = quality.completeness,
    detectionConfidence = quality.detectionConfidence,
    flags = quality.flags.sorted(),
    userEdited = userEdited,
)

fun EpisodeDto.toModel() = SleepEpisode(
    id = EpisodeId(id),
    start = Instant.parse(start),
    end = Instant.parse(end),
    zone = ZoneId.of(zone),
    kind = kind,
    stages = stages?.map { StageSpan(it.stage, Interval(Instant.parse(it.start), Instant.parse(it.end))) },
    inBed = inBed?.toModel(),
    origin = origin.toModel(),
    quality = DataQuality(completeness, detectionConfidence, flags.toSet()),
    userEdited = userEdited,
)

@Serializable
data class AnnotationDto(
    val date: String,
    val freeDay: Boolean? = null,
    val alarmSet: Boolean? = null,
    val alarmDismissedAt: String? = null,
)

fun DayAnnotation.toDto() = AnnotationDto(date.toString(), freeDay, alarmSet, alarmDismissedAt?.toString())
fun AnnotationDto.toModel() = DayAnnotation(LocalDate.parse(date), freeDay, alarmSet, alarmDismissedAt?.let(Instant::parse))

@Serializable
data class CheckInDto(
    val type: String,
    val at: String,
    val rating: Int? = null,
    val medianRtMillis: Double? = null,
    val lapses: Int? = null,
    val trials: Int? = null,
    val night: String? = null,
)

fun CheckIn.toDto(): CheckInDto = when (this) {
    is CheckIn.EnergyRating -> CheckInDto("energy", at.toString(), rating = rating)
    is CheckIn.ReactionTest -> CheckInDto("reaction", at.toString(), medianRtMillis = medianRtMillis, lapses = lapses, trials = trials)
    is CheckIn.SleepRating -> CheckInDto("sleep", at.toString(), rating = rating, night = night.toString())
}

fun CheckInDto.toModel(): CheckIn = when (type) {
    "energy" -> CheckIn.EnergyRating(Instant.parse(at), requireNotNull(rating))
    "sleep" -> CheckIn.SleepRating(Instant.parse(at), LocalDate.parse(requireNotNull(night)), requireNotNull(rating))
    "reaction" -> CheckIn.ReactionTest(Instant.parse(at), requireNotNull(medianRtMillis), requireNotNull(lapses), requireNotNull(trials))
    else -> throw IllegalArgumentException("Unknown check-in type $type")
}

/** Flat signal record: one shape for every [Signal] family. */
@Serializable
data class SignalDto(
    val family: String,
    val origin: SourceRefDto,
    val startMs: Long,
    val endMs: Long,
    val v1: Double = 0.0,
    val v2: Double? = null,
    val code: String? = null,
)

fun Signal.toDto(): SignalDto = when (this) {
    is Signal.HeartRateSample -> SignalDto("hr", origin.toDto(), at.toEpochMilli(), at.toEpochMilli(), bpm)
    is Signal.RestingHeartRate -> SignalDto("rhr", origin.toDto(), at.toEpochMilli(), at.toEpochMilli(), bpm)
    is Signal.HrvSample -> SignalDto("hrv", origin.toDto(), at.toEpochMilli(), at.toEpochMilli(), rmssdMillis)
    is Signal.StepBucket -> SignalDto("steps", origin.toDto(), interval.start.toEpochMilli(), interval.end.toEpochMilli(), count.toDouble())
    is Signal.ActivityBucket -> SignalDto("activity", origin.toDto(), interval.start.toEpochMilli(), interval.end.toEpochMilli(), code = type.name)
    is Signal.ScreenEvent -> SignalDto("screen", origin.toDto(), at.toEpochMilli(), at.toEpochMilli(), if (interactive) 1.0 else 0.0)
    is Signal.MotionBucket -> SignalDto("motion", origin.toDto(), interval.start.toEpochMilli(), interval.end.toEpochMilli(), motionLevel, sleepConfidence)
    is Signal.LightBucket -> SignalDto("light", origin.toDto(), interval.start.toEpochMilli(), interval.end.toEpochMilli(), meanLux)
    is Signal.ChargingEvent -> SignalDto("charging", origin.toDto(), at.toEpochMilli(), at.toEpochMilli(), if (plugged) 1.0 else 0.0)
    is Signal.AlarmEvent -> SignalDto("alarm", origin.toDto(), at.toEpochMilli(), at.toEpochMilli(), code = kind.name)
}

fun SignalDto.toModel(): Signal {
    val o = origin.toModel()
    val s = Instant.ofEpochMilli(startMs)
    val e = Instant.ofEpochMilli(endMs)
    return when (family) {
        "hr" -> Signal.HeartRateSample(o, s, v1)
        "rhr" -> Signal.RestingHeartRate(o, s, v1)
        "hrv" -> Signal.HrvSample(o, s, v1)
        "steps" -> Signal.StepBucket(o, Interval(s, e), v1.toLong())
        "activity" -> Signal.ActivityBucket(o, Interval(s, e), ActivityType.valueOf(requireNotNull(code)))
        "screen" -> Signal.ScreenEvent(o, s, v1 >= 0.5)
        "motion" -> Signal.MotionBucket(o, Interval(s, e), v1, v2)
        "light" -> Signal.LightBucket(o, Interval(s, e), v1)
        "charging" -> Signal.ChargingEvent(o, s, v1 >= 0.5)
        "alarm" -> Signal.AlarmEvent(o, s, AlarmKind.valueOf(requireNotNull(code)))
        else -> throw IllegalArgumentException("Unknown signal family $family")
    }
}
