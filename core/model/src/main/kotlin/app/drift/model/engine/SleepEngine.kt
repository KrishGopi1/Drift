package app.drift.model.engine

import app.drift.model.CheckIn
import app.drift.model.DayAnnotation
import app.drift.model.Interval
import app.drift.model.Signal
import app.drift.model.SleepEpisode
import java.time.Instant
import java.time.ZoneId

/** Semantic engine version; bump on any output-affecting change (golden tests pin outputs per version). */
data class EngineVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<EngineVersion> {
    override fun compareTo(other: EngineVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    override fun toString(): String = "$major.$minor.$patch"
}

/** C4. Pure, deterministic analysis. No I/O and no clock: `now` comes in through [EngineInput]. */
interface SleepEngine {
    val version: EngineVersion
    fun analyze(input: EngineInput): AnalysisSnapshot
}

data class SignalWindow(val window: Interval, val signals: List<Signal>) {
    companion object {
        fun empty(at: Instant): SignalWindow = SignalWindow(Interval(at, at), emptyList())
    }
}

/** C4. Everything the engine needs. Episodes cover up to 90 days before [now]. */
data class EngineInput(
    val now: Instant,
    val zone: ZoneId,
    val episodes: List<SleepEpisode>,
    val signals: SignalWindow,
    val profile: UserProfile,
    val priorState: EngineState?,
    val annotations: List<DayAnnotation> = emptyList(),
    val checkIns: List<CheckIn> = emptyList(),
)

/**
 * Filter state carried between runs (Kalman means/variances etc.). The engine owns the keys;
 * storage treats it as an opaque map. [engineVersion] lets the engine discard incompatible state.
 */
data class EngineState(
    val engineVersion: EngineVersion,
    val asOf: Instant,
    val values: Map<String, Double>,
)
