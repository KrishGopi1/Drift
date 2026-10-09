package app.drift.testing.synthetic

import app.drift.model.CheckIn
import app.drift.model.DayAnnotation
import app.drift.model.Interval
import app.drift.model.QualityTier
import app.drift.model.Signal
import app.drift.model.SleepEpisode
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.StageSpan
import app.drift.model.engine.EngineInput
import app.drift.model.engine.SignalWindow
import app.drift.model.engine.UserProfile
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Seeded random source; every generator draws from one in a fixed order so output is reproducible per seed. */
class Rng(seed: Long) {
    private val random = Random(seed)
    fun double(): Double = random.nextDouble()
    fun int(from: Int, untilInclusive: Int): Int = random.nextInt(from, untilInclusive + 1)
    fun chance(p: Double): Boolean = random.nextDouble() < p
    fun range(lo: Double, hi: Double): Double = lo + (hi - lo) * random.nextDouble()

    /** Normal(0, sd) via Box-Muller. */
    fun gauss(sd: Double): Double {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return sd * sqrt(-2.0 * ln(u1)) * cos(2 * PI * u2)
    }
}

/**
 * How one data source records sleep: its tier, how far its times drift from the truth (minutes, 1 sd),
 * whether it provides stages, and the [DataQuality][app.drift.model.DataQuality] it reports.
 */
data class SourceProfile(
    val sourceId: SourceId,
    val tier: QualityTier,
    val onsetNoiseMinutes: Double,
    val endNoiseMinutes: Double,
    val hasStages: Boolean,
    val completeness: ClosedFloatingPointRange<Double>,
    val detectionConfidence: ClosedFloatingPointRange<Double>,
    val dataOrigin: String?,
    val device: String?,
    /** Systematic offset added to onset/end, in minutes (how "slight time offsets" between sources arise). */
    val biasMinutes: Double = 0.0,
) {
    companion object {
        val RING = SourceProfile(SourceId.HEALTH_CONNECT, QualityTier.WEARABLE_RING, 4.0, 4.0, true, 0.97..1.0, 1.0..1.0, "com.example.ring", "ring")
        val WRIST = SourceProfile(SourceId.HEALTH_CONNECT, QualityTier.WEARABLE_WRIST, 8.0, 8.0, true, 0.9..1.0, 1.0..1.0, "com.example.band", "band")
        val PHONE_APP = SourceProfile(SourceId.SLEEP_AS_ANDROID, QualityTier.PHONE_APP, 12.0, 10.0, true, 0.85..1.0, 1.0..1.0, "com.urbandroid.sleep", null, biasMinutes = 4.0)
        val PHONE_INFERRED = SourceProfile(SourceId.PHONE, QualityTier.PHONE_INFERRED, 25.0, 25.0, false, 0.7..1.0, 0.55..0.9, null, null)
    }
}

enum class CheckInDensity { NONE, SPARSE, DENSE }
enum class SignalDensity { NONE, PHONE_ONLY, FULL }

/** One night of the truth, before any source observed it. [date] is the local date of waking. */
data class TrueNight(
    val date: LocalDate,
    val zone: ZoneId,
    val interval: Interval,
    val awakenings: Int,
    val nap: Interval?,
)

/**
 * What the generator knows and a real device would not: the real need, the real DLMO per day (minutes after local
 * midnight in that day's zone), and the true sleep of every night, including nights no source recorded.
 */
data class GroundTruth(
    val sleepNeed: Duration,
    val dlmoMinutesByDate: Map<LocalDate, Double>,
    val nights: List<TrueNight>,
    val missingDates: Set<LocalDate>,
    /** Scenario-specific advertised properties, e.g. `dstTransitionDate`. Keys are documented per scenario. */
    val facts: Map<String, String> = emptyMap(),
) {
    /** The (constant or last) DLMO, for scenarios where it does not move. */
    val finalDlmoMinutes: Double get() = dlmoMinutesByDate.entries.maxByOrNull { it.key }!!.value
}

/**
 * A complete synthetic user. [episodes] is the canonical timeline an engine would see after the merge
 * (the best tier per night); [rawEpisodes] holds every source's original record (equal to [episodes] unless the
 * scenario has duplicate sources).
 */
data class SyntheticHistory(
    val name: String,
    val seed: Long,
    val zone: ZoneId,
    val now: Instant,
    val profile: UserProfile,
    val episodes: List<SleepEpisode>,
    val rawEpisodes: List<SleepEpisode>,
    val annotations: List<DayAnnotation>,
    val checkIns: List<CheckIn>,
    val signals: List<Signal>,
    val truth: GroundTruth,
) {
    /** Engine input at [at] (default [now]): only data that existed by then. */
    fun toEngineInput(at: Instant = now, priorState: app.drift.model.engine.EngineState? = null): EngineInput {
        val horizon = at.minus(Duration.ofDays(90))
        return EngineInput(
            now = at,
            zone = zone,
            episodes = episodes.filter { !it.end.isAfter(at) && it.end.isAfter(horizon) },
            signals = SignalWindow(Interval(horizon, at), signals.filter { signalTime(it).let { t -> !t.isAfter(at) && t.isAfter(horizon) } }),
            profile = profile,
            priorState = priorState,
            annotations = annotations.filter { !it.date.isAfter(at.atZone(zone).toLocalDate()) },
            checkIns = checkIns.filter { !it.at.isAfter(at) },
        )
    }

    /**
     * Same local clock times in [newZone]: every instant is remapped so the wall-clock reading is unchanged.
     * For the property "shifting all data by a timezone leaves local-time outputs unchanged".
     */
    fun reZoned(newZone: ZoneId): SyntheticHistory {
        fun map(t: Instant): Instant = LocalDateTime.ofInstant(t, zone).atZone(newZone).toInstant()
        fun map(i: Interval) = Interval(map(i.start), map(i.end))
        fun mapEp(e: SleepEpisode) = e.copy(
            start = map(e.start), end = map(e.end), zone = newZone,
            stages = e.stages?.map { StageSpan(it.stage, map(it.interval)) },
            inBed = e.inBed?.let(::map),
        )
        return copy(
            zone = newZone,
            now = map(now),
            episodes = episodes.map(::mapEp),
            rawEpisodes = rawEpisodes.map(::mapEp),
            annotations = annotations.map { it.copy(alarmDismissedAt = it.alarmDismissedAt?.let(::map)) },
            checkIns = checkIns.map {
                when (it) {
                    is CheckIn.EnergyRating -> it.copy(at = map(it.at))
                    is CheckIn.ReactionTest -> it.copy(at = map(it.at))
                    is CheckIn.SleepRating -> it.copy(at = map(it.at))
                }
            },
            signals = signals.map { mapSignal(it, ::map, ::map) },
            truth = truth.copy(nights = truth.nights.map { it.copy(zone = newZone, interval = map(it.interval), nap = it.nap?.let(::map)) }),
        )
    }
}

internal fun signalTime(s: Signal): Instant = when (s) {
    is Signal.HeartRateSample -> s.at
    is Signal.RestingHeartRate -> s.at
    is Signal.HrvSample -> s.at
    is Signal.StepBucket -> s.interval.start
    is Signal.ActivityBucket -> s.interval.start
    is Signal.ScreenEvent -> s.at
    is Signal.MotionBucket -> s.interval.start
    is Signal.LightBucket -> s.interval.start
    is Signal.ChargingEvent -> s.at
    is Signal.AlarmEvent -> s.at
}

internal fun mapSignal(s: Signal, t: (Instant) -> Instant, i: (Interval) -> Interval): Signal = when (s) {
    is Signal.HeartRateSample -> s.copy(at = t(s.at))
    is Signal.RestingHeartRate -> s.copy(at = t(s.at))
    is Signal.HrvSample -> s.copy(at = t(s.at))
    is Signal.StepBucket -> s.copy(interval = i(s.interval))
    is Signal.ActivityBucket -> s.copy(interval = i(s.interval))
    is Signal.ScreenEvent -> s.copy(at = t(s.at))
    is Signal.MotionBucket -> s.copy(interval = i(s.interval))
    is Signal.LightBucket -> s.copy(interval = i(s.interval))
    is Signal.ChargingEvent -> s.copy(at = t(s.at))
    is Signal.AlarmEvent -> s.copy(at = t(s.at))
}

internal fun SourceProfile.ref(recordId: String) = SourceRef(sourceId, recordId, tier, dataOrigin, device)
