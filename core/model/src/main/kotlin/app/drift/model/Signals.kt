package app.drift.model

import java.time.Instant

/** C2. Non-episode signals from adapters. Each carries its origin for quality weighting. */
sealed interface Signal {
    val origin: SourceRef

    /** Point-in-time signals use [at]; bucketed signals cover [interval]. */
    data class HeartRateSample(override val origin: SourceRef, val at: Instant, val bpm: Double) : Signal
    data class RestingHeartRate(override val origin: SourceRef, val at: Instant, val bpm: Double) : Signal
    data class HrvSample(override val origin: SourceRef, val at: Instant, val rmssdMillis: Double) : Signal
    data class StepBucket(override val origin: SourceRef, val interval: Interval, val count: Long) : Signal
    data class ActivityBucket(override val origin: SourceRef, val interval: Interval, val type: ActivityType) : Signal
    data class ScreenEvent(override val origin: SourceRef, val at: Instant, val interactive: Boolean) : Signal
    /** [sleepConfidence] 0..1 as reported by an on-device detector such as the Sleep API. */
    data class MotionBucket(override val origin: SourceRef, val interval: Interval, val motionLevel: Double, val sleepConfidence: Double?) : Signal
    data class LightBucket(override val origin: SourceRef, val interval: Interval, val meanLux: Double) : Signal
    data class ChargingEvent(override val origin: SourceRef, val at: Instant, val plugged: Boolean) : Signal
    data class AlarmEvent(override val origin: SourceRef, val at: Instant, val kind: AlarmKind) : Signal
}

enum class ActivityType { STILL, WALKING, RUNNING, CYCLING, VEHICLE, EXERCISE, UNKNOWN }

enum class AlarmKind { SCHEDULED, DISMISSED, SNOOZED }
