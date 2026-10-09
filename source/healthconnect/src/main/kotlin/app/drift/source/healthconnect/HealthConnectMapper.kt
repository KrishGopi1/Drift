package app.drift.source.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import app.drift.model.ActivityType
import app.drift.model.DataQuality
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.Signal
import app.drift.model.SleepEpisode
import app.drift.model.SleepStage
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.StageSpan
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Converts Health Connect records to canonical model types. Pure; no Android services needed. */
class HealthConnectMapper(
    private val tiers: OriginTierTable = OriginTierTable.DEFAULT,
    private val defaultZone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    class Mapped(val episodes: List<SleepEpisode>, val signals: List<Signal>)

    fun map(records: Collection<Record>): Mapped {
        val episodes = ArrayList<SleepEpisode>()
        val signals = ArrayList<Signal>()
        for (record in records) {
            when (record) {
                is SleepSessionRecord -> sleep(record)?.let(episodes::add)
                is HeartRateRecord -> signals += heartRate(record)
                is RestingHeartRateRecord -> signals += Signal.RestingHeartRate(ref(record.metadata), record.time, record.beatsPerMinute.toDouble())
                is HeartRateVariabilityRmssdRecord -> signals += Signal.HrvSample(ref(record.metadata), record.time, record.heartRateVariabilityMillis)
                is StepsRecord -> if (record.endTime.isAfter(record.startTime)) {
                    signals += Signal.StepBucket(ref(record.metadata), Interval(record.startTime, record.endTime), record.count)
                }
                is ExerciseSessionRecord -> if (record.endTime.isAfter(record.startTime)) {
                    signals += Signal.ActivityBucket(ref(record.metadata), Interval(record.startTime, record.endTime), activityType(record.exerciseType))
                }
                else -> Unit
            }
        }
        return Mapped(episodes, signals)
    }

    fun ref(metadata: Metadata): SourceRef {
        val origin = metadata.dataOrigin.packageName.ifEmpty { null }
        val tier = tiers.tierFor(origin, deviceKind(metadata.device))
        return SourceRef(SourceId.HEALTH_CONNECT, metadata.id, tier, origin, metadata.device?.let(::deviceLabel))
    }

    fun sleep(record: SleepSessionRecord): SleepEpisode? {
        if (!record.endTime.isAfter(record.startTime)) return null
        val window = Interval(record.startTime, record.endTime)
        val stages = record.stages
            .mapNotNull { s ->
                val iv = Interval(s.startTime, s.endTime).intersect(window)
                iv?.takeIf { it.duration > Duration.ZERO }?.let { StageSpan(mapStage(s.stage), it) }
            }
            .sortedBy { it.interval.start }
            .takeIf { it.isNotEmpty() }
        val covered = stages?.sumOf { it.interval.duration.toMillis() } ?: 0L
        val completeness = if (stages == null) 1.0 else (covered.toDouble() / window.duration.toMillis()).coerceIn(0.0, 1.0)
        val zone = zoneFor(record.startZoneOffset, record.startTime)
        return SleepEpisode(
            id = EpisodeId("hc-" + record.metadata.id),
            start = record.startTime,
            end = record.endTime,
            zone = zone,
            kind = kindOf(window, zone),
            stages = stages,
            inBed = window,
            origin = ref(record.metadata),
            quality = DataQuality(completeness, 1.0),
        )
    }

    /** One mean sample per 5-minute bucket keeps 90 days of wrist HR compact. */
    private fun heartRate(record: HeartRateRecord): List<Signal> {
        val origin = ref(record.metadata)
        return record.samples
            .groupBy { it.time.toEpochMilli() / HR_BUCKET_MS }
            .toSortedMap()
            .map { (bucket, samples) ->
                val at = if (samples.size == 1) samples[0].time else Instant.ofEpochMilli(bucket * HR_BUCKET_MS)
                Signal.HeartRateSample(origin, at, samples.sumOf { it.beatsPerMinute.toDouble() } / samples.size)
            }
    }

    private fun zoneFor(offset: ZoneOffset?, at: Instant): ZoneId {
        val system = defaultZone()
        return if (offset == null || system.rules.getOffset(at) == offset) system else offset
    }

    /** Short sessions in the middle of the day are naps; the engine still re-classifies against the user's night window. */
    private fun kindOf(window: Interval, zone: ZoneId): EpisodeKind {
        if (window.duration >= NAP_MAX) return EpisodeKind.MAIN
        val mid = window.start.plus(window.duration.dividedBy(2)).atZone(zone).toLocalTime()
        return if (mid.hour in 9..19) EpisodeKind.NAP else EpisodeKind.MAIN
    }

    private fun deviceKind(device: Device?): OriginTierTable.DeviceKind = when (device?.type) {
        Device.TYPE_RING -> OriginTierTable.DeviceKind.RING
        Device.TYPE_WATCH -> OriginTierTable.DeviceKind.WATCH
        Device.TYPE_FITNESS_BAND -> OriginTierTable.DeviceKind.BAND
        Device.TYPE_PHONE -> OriginTierTable.DeviceKind.PHONE
        else -> OriginTierTable.DeviceKind.UNKNOWN
    }

    private fun deviceLabel(d: Device): String? =
        listOfNotNull(d.manufacturer, d.model).joinToString(" ").ifBlank { null }

    companion object {
        private const val HR_BUCKET_MS = 5 * 60 * 1000L
        private val NAP_MAX: Duration = Duration.ofHours(3)

        fun mapStage(type: Int): SleepStage = when (type) {
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStage.AWAKE
            SleepSessionRecord.STAGE_TYPE_LIGHT,
            SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStage.LIGHT
            SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.DEEP
            SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.REM
            else -> SleepStage.UNKNOWN
        }

        fun activityType(exerciseType: Int): ActivityType = when (exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> ActivityType.WALKING
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> ActivityType.RUNNING
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> ActivityType.CYCLING
            else -> ActivityType.EXERCISE
        }
    }
}

