package app.drift.model.engine

import app.drift.model.Interval
import app.drift.model.Quantity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** C4. One engine run's full output. Stored with [engineVersion] and [inputHash]. */
data class AnalysisSnapshot(
    val engineVersion: EngineVersion,
    val inputHash: String,
    val computedAt: Instant,
    val sleepNeed: Quantity<Duration>,
    /** Null when debt is unavailable (3+ consecutive missing nights); see [dataSufficiency]. */
    val sleepDebt: Quantity<Duration>?,
    val debtBand: DebtBand?,
    val debtTrend: List<DailyDebt>,
    val regularity: RegularityReport,
    val circadian: CircadianEstimate,
    val energyCurve: EnergyCurve,
    val recommendations: Recommendations,
    val explanations: List<Explanation>,
    val state: EngineState,
    val dataSufficiency: Sufficiency,
    /** Today's energy potential (0..100), driven by sleep debt; null when debt is unavailable. */
    val energyPotential: EnergyPotential? = null,
    /** Projection of debt if the recommended schedule is followed; null when debt is unavailable. */
    val catchUp: CatchUp? = null,
)

/** Plain-language band of [EnergyPotential.score], from best to worst. */
enum class EnergyLabel { SUPER, GREAT, GOOD, FAIR, LOW }

/**
 * How much of a fully rested day's energy is available (plan 4.6b). Estimated from sleep debt with the same
 * reservoir model that scales the energy curve. [trend] holds the score after each night of the debt window, oldest first.
 */
data class EnergyPotential(
    val score: app.drift.model.Quantity<Double>,
    val label: EnergyLabel,
    val trend: List<Pair<LocalDate, Double>> = emptyList(),
)

/**
 * If the user follows tonight's recommended bedtime and wake time from now on: the projected debt after each
 * night ([projection], first entry = tomorrow morning) and how many nights until the potential reaches
 * [EnergyLabel.GREAT] or better (0 = already there, null = not within the projection horizon).
 */
data class CatchUp(
    val nightsToGreat: Int?,
    val projection: List<Pair<LocalDate, Duration>>,
)

enum class DebtBand { LOW, MODERATE, HIGH }

/** One sleep day's row in the 14-night contribution list (plan 4.3). */
data class DailyDebt(
    val date: LocalDate,
    /** Total sleep incl. credited naps; null if missing. */
    val sleep: Quantity<Duration>?,
    val need: Duration,
    /** Positive = added to debt, negative = repaid. */
    val contribution: Duration,
    val debtAfter: Duration,
    val imputed: Boolean,
)

data class RegularityReport(
    val sri7: Quantity<Double>?,
    val sri14: Quantity<Double>?,
    val onsetSpread: Quantity<Duration>?,
    val wakeSpread: Quantity<Duration>?,
    val midpointSpread: Quantity<Duration>?,
    val socialJetlag: Quantity<Duration>?,
    val chronotype: Chronotype?,
    /** Daily SRI values for the trend chart (oldest first). */
    val trend: List<Pair<LocalDate, Double>> = emptyList(),
)

enum class ChronotypeLabel { EARLY, INTERMEDIATE, LATE }

/** Free-day mid-sleep corrected for debt (MSFsc), as minutes after local midnight (may exceed 1440). */
data class Chronotype(
    val msfscMinutes: Quantity<Double>,
    val label: ChronotypeLabel,
    val freeNightsUsed: Int,
    val questionnaireWeight: Double,
)

data class CircadianEstimate(
    /** Estimated DLMO as minutes after local midnight (0 until 1440, circular). */
    val dlmoMinutes: Quantity<Double>?,
    /** Next (or current) melatonin window and wind-down start, as absolute instants. */
    val melatoninWindow: Interval?,
    val windDownStart: Instant?,
    val shiftWorkMode: Boolean,
    /** Non-null while the clock is still catching up after travel / a zone change. */
    val reentrainment: Reentrainment?,
)

data class Reentrainment(val remainingShift: Duration, val eastward: Boolean, val estimatedDaysLeft: Int)

enum class EnergyPhase { GROGGINESS, MORNING_PEAK, AFTERNOON_DIP, EVENING_PEAK, WIND_DOWN, MELATONIN_WINDOW, SLEEP }

data class EnergyPoint(val at: Instant, val level: Double)

data class EnergySegment(val phase: EnergyPhase, val interval: Interval, val extremumAt: Instant?)

/** One day of the energy schedule (5-min points, level normalized 0..100). */
data class DayCurve(
    val date: LocalDate,
    val points: List<EnergyPoint>,
    val segments: List<EnergySegment>,
    /** True if no dip had enough prominence; the UI says so instead of inventing one. */
    val tooFlatForDip: Boolean,
    val confidence: app.drift.model.ConfidenceLevel,
    val provenance: app.drift.model.Provenance,
)

/** Yesterday, today and tomorrow (day switcher) plus how many check-ins calibrated it. */
data class EnergyCurve(
    val days: List<DayCurve>,
    val calibrationCheckIns: Int,
) {
    fun forDate(date: LocalDate): DayCurve? = days.firstOrNull { it.date == date }
}

enum class HabitKind { CAFFEINE_CUTOFF, LAST_MEAL, ALCOHOL_CUTOFF, MORNING_LIGHT, EXERCISE_WINDOW, DIM_LIGHTS }

/** A habit-timing card anchored to the computed schedule. [basis] is a ParameterSet citation key. */
data class HabitCard(val kind: HabitKind, val window: Interval, val basis: String)

data class NapAdvice(val window: Interval, val maxDuration: Duration, val reason: String)

/** Shown when the computed bedtime conflicts with the melatonin window (plan 4.7). */
data class TradeOff(val message: String, val bedtimeIfFollowingWindow: Instant)

data class Recommendations(
    val sleepOpportunity: Quantity<Duration>,
    val bedtime: Quantity<Instant>,
    val wakeTime: Quantity<Instant>,
    val tradeOff: TradeOff?,
    val habits: List<HabitCard>,
    val nap: NapAdvice?,
)

enum class MetricKey { SLEEP_NEED, SLEEP_DEBT, REGULARITY, CHRONOTYPE, CIRCADIAN, ENERGY, RECOMMENDATION }

enum class CauseKind { NIGHT_ENTERED_WINDOW, NIGHT_LEFT_WINDOW, NEED_REESTIMATED, SOURCE_ADDED, USER_EDIT, CIRCADIAN_SHIFT, CHECK_IN_CALIBRATION, FIRST_RUN }

data class Cause(val kind: CauseKind, val date: LocalDate?, val delta: Double?, val text: String)

/** Plan 4.8: plain-language "why this changed" line with structured causes. */
data class Explanation(val metric: MetricKey, val headline: String, val causes: List<Cause>)

enum class SufficiencyTier { PRIORS_ONLY, EARLY, PERSONALIZING, FULL }

data class Sufficiency(
    val tier: SufficiencyTier,
    val daysOfHistory: Int,
    val informativeNights: Int,
    /** Informative nights still needed before personal need replaces the prior (0 when personalized). */
    val nightsUntilPersonalNeed: Int,
    val debtAvailable: Boolean,
    val regularityAvailable: Boolean,
)
