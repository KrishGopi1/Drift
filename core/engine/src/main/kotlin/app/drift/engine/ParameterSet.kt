package app.drift.engine

import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.QualityTier

/**
 * Where a constant comes from. [verificationNote] is non-null when the author could not confirm the
 * constant (or the bibliographic detail) against the source and a sleep-science reviewer should check it.
 */
data class Citation(
    val authors: String,
    val year: Int?,
    val title: String,
    val venue: String? = null,
    val doi: String? = null,
    val verificationNote: String? = null,
)

/** One tunable constant. All numeric values are Doubles; durations are in minutes unless [unit] says otherwise. */
data class Param(
    val key: String,
    val value: Double,
    val unit: String,
    val description: String,
    val citation: Citation,
) {
    val v: Double get() = value
    val i: Int get() = value.toInt()
}

internal object Cites {
    private const val DESIGN_NOTE = "Engineering default chosen by the project plan; no direct source. Tune against replayed real data (plan 9.2)."

    val PLAN = Citation("Drift project plan", 2026, "Analysis engine specification, section 4", verificationNote = DESIGN_NOTE)
    val HIRSHKOWITZ = Citation("Hirshkowitz M, et al.", 2015, "National Sleep Foundation's sleep time duration recommendations: methodology and results summary", "Sleep Health 1(1):40-43", "10.1016/j.sleh.2014.12.010")
    val KLERMAN = Citation("Klerman EB, Dijk DJ", 2008, "Age-related reduction in the maximal capacity for sleep: implications for insomnia", "Current Biology 18(15):1118-1123", "10.1016/j.cub.2008.06.047", "Age-band priors are rounded interpolations of the published extended-opportunity sleep durations; verify the exact numbers.")
    val KALMAN = Citation("Kalman RE", 1960, "A new approach to linear filtering and prediction problems", "Journal of Basic Engineering 82(1):35-45", "10.1115/1.3662552")
    val TOBIN = Citation("Tobin J", 1958, "Estimation of relationships for limited dependent variables", "Econometrica 26(1):24-36", "10.2307/1907382")
    val BELENKY = Citation("Belenky G, et al.", 2003, "Patterns of performance degradation and restoration during sleep restriction and subsequent recovery", "Journal of Sleep Research 12(1):1-12", "10.1046/j.1365-2869.2003.00337.x")
    val BANKS = Citation("Banks S, Van Dongen HPA, Maislin G, Dinges DF", 2010, "Neurobehavioral dynamics following chronic sleep restriction: dose-response effects of one night for recovery", "Sleep 33(8):1013-1026", null, "DOI not recorded; verify.")
    val VAN_DONGEN = Citation("Van Dongen HPA, Maislin G, Mullington JM, Dinges DF", 2003, "The cumulative cost of additional wakefulness", "Sleep 26(2):117-126", "10.1093/sleep/26.2.117")
    val KITAMURA = Citation("Kitamura S, et al.", 2016, "Estimating individual optimal sleep duration and potential sleep debt", "Scientific Reports 6:35812", "10.1038/srep35812", "Used for the idea that free-night excess sleep tracks accumulated debt; the 0.3 coefficient is a design value, verify.")
    val PHILLIPS = Citation("Phillips AJK, et al.", 2017, "Irregular sleep/wake patterns are associated with poorer academic performance and delayed circadian and sleep/wake timing", "Scientific Reports 7:3216", "10.1038/s41598-017-03171-4")
    val ROENNEBERG_2003 = Citation("Roenneberg T, Wirz-Justice A, Merrow M", 2003, "Life between clocks: daily temporal patterns of human chronotypes", "Journal of Biological Rhythms 18(1):80-90", "10.1177/0748730402239679")
    val WITTMANN = Citation("Wittmann M, Dinich J, Merrow M, Roenneberg T", 2006, "Social jetlag: misalignment of biological and social time", "Chronobiology International 23(1-2):497-509", "10.1080/07420520500545979")
    val WRIGHT_2005 = Citation("Wright KP Jr, Gronfier C, Duffy JF, Czeisler CA", 2005, "Intrinsic period and light intensity determine the phase relationship between melatonin and sleep in humans", "Journal of Biological Rhythms 20(2):168-177", null, "DLMO-to-sleep-onset offsets of roughly 2-3 h are widely quoted; exact offsets and DOI to verify.")
    val BURGESS_FOGG = Citation("Burgess HJ, Fogg LF", 2008, "Individual differences in the amount and timing of salivary melatonin secretion", "PLoS ONE 3(8):e3055", null, "Used for the spread of DLMO relative to sleep timing; volume and article number to verify.")
    val EASTMAN_BURGESS = Citation("Eastman CI, Burgess HJ", 2009, "How to travel the world without jet lag", "Sleep Medicine Clinics 4(2):241-255", "10.1016/j.jsmc.2009.02.006", "Re-entrainment of roughly 1 h/day eastward and 1.5 h/day westward is a commonly quoted rule of thumb; verify rates.")
    val WATERHOUSE = Citation("Waterhouse J, Reilly T, Atkinson G, Edwards B", 2007, "Jet lag: trends and coping strategies", "The Lancet 369(9567):1117-1129", null, "Secondary support for direction-dependent re-entrainment rates; verify.")
    val BORBELY = Citation("Borbely AA", 1982, "A two process model of sleep regulation", "Human Neurobiology 1(3):195-204", null, "No DOI (pre-DOI journal).")
    val DAAN = Citation("Daan S, Beersma DGM, Borbely AA", 1984, "Timing of human sleep: recovery process gated by a circadian pacemaker", "American Journal of Physiology 246(2):R161-R183", "10.1152/ajpregu.1984.246.2.R161", "Time constants 18.2 h (wake) and 4.2 h (sleep) as I recall them; verify.")
    val AKERSTEDT_FOLKARD_1995 = Citation("Akerstedt T, Folkard S", 1995, "Validation of the S and C components of the three-process model of alertness regulation", "Sleep 18(1):1-6", "10.1093/sleep/18.1.1")
    val AKERSTEDT_FOLKARD_1997 = Citation("Akerstedt T, Folkard S", 1997, "The three-process model of alertness and its extension to performance, sleep latency, and sleep length", "Chronobiology International 14(2):115-123", "10.3109/07420529709001149")
    val TASSI = Citation("Tassi P, Muzet A", 2000, "Sleep inertia", "Sleep Medicine Reviews 4(4):341-353", "10.1053/smrv.2000.0098")
    val HILDITCH = Citation("Hilditch CJ, McHill AW", 2019, "Sleep inertia: current insights", "Nature and Science of Sleep 11:155-165", "10.2147/NSS.S188911")
    val DRAKE = Citation("Drake C, Roehrs T, Shambroom J, Roth T", 2013, "Caffeine effects on sleep taken 0, 3, or 6 hours before going to bed", "Journal of Clinical Sleep Medicine 9(11):1195-1200", "10.5664/jcsm.3170")
    val CLARK_LANDOLT = Citation("Clark I, Landolt HP", 2017, "Coffee, caffeine, and sleep: a systematic review of epidemiological studies and randomized controlled trials", "Sleep Medicine Reviews 31:70-78", "10.1016/j.smrv.2016.01.006")
    val EBRAHIM = Citation("Ebrahim IO, Shapiro CM, Williams AJ, Fenwick PB", 2013, "Alcohol and sleep I: effects on normal sleep", "Alcoholism: Clinical and Experimental Research 37(4):539-549", "10.1111/acer.12006")
    val HURSH_SAFTE = Citation("Hursh SR, Redmond DP, Johnson ML, et al.", 2004, "Fatigue models for applied research in warfighting", "Aviation, Space, and Environmental Medicine 75(3 Suppl):A44-A53", null, "SAFTE reservoir model (patent US 6,579,233, expired). Used for the idea that the sleep reservoir caps the day's effectiveness and that a depleted reservoir lowers peaks and deepens troughs; our exponential mapping and its constants are design values fitted to a commercial app's published examples, verify against replayed check-ins.")
    val WRIGHT_2013 = Citation("Wright KP Jr, et al.", 2013, "Entrainment of the human circadian clock to the natural light-dark cycle", "Current Biology 23(16):1554-1558", "10.1016/j.cub.2013.06.039")
    val YOUNGSTEDT = Citation("Youngstedt SD, Elliott JA, Kripke DF", 2019, "Human circadian phase-response curves for exercise", "The Journal of Physiology 597(8):2253-2268", "10.1113/JP276943")
    val GOOLEY = Citation("Gooley JJ, et al.", 2011, "Exposure to room light before bedtime suppresses melatonin onset and shortens melatonin duration in humans", "Journal of Clinical Endocrinology & Metabolism 96(3):E463-E472", "10.1210/jc.2010-2098")
    val BROOKS_LACK = Citation("Brooks A, Lack L", 2006, "A brief afternoon nap following nocturnal sleep restriction: which nap duration is most recuperative?", "Sleep 29(6):831-840", "10.1093/sleep/29.6.831")
    val ROENNEBERG_SLEEP_DEBT = Citation("Roenneberg T, Kuehnle T, Juda M, et al.", 2007, "Epidemiology of the human circadian clock", "Sleep Medicine Reviews 11(6):429-438", "10.1016/j.smrv.2007.07.005", "Source of the MSFsc sleep-debt correction as I recall it; verify.")
    val OHAYON = Citation("Ohayon M, et al.", 2017, "National Sleep Foundation's sleep quality recommendations: first report", "Sleep Health 3(1):6-19", "10.1016/j.sleh.2016.11.006", "The guideline calls waking after sleep onset of 20 min or less good; the 60 min threshold used here is a stricter, engineering choice. Verify.")
    val DERIVED_MEQ = Citation("Drift project plan", 2026, "Questionnaire answer to mid-sleep prior mapping", verificationNote = "Mapping from the five onboarding answers to MSFsc is a design choice informed by Roenneberg 2003 chronotype distribution; verify.")
}

/**
 * Every constant the engine uses, versioned and cited (plan section 4). Models read values through the
 * public properties; [entries] lists them all for the methodology screen and for tests.
 */
class ParameterSet private constructor(private val overrides: Map<String, Double>) {
    val version: String = "1.1.1"
    private val registry = LinkedHashMap<String, Param>()

    val entries: List<Param> get() = registry.values.toList()

    /** A copy with some values replaced (sensitivity analyses and tests). Unknown keys are rejected. */
    fun withOverrides(vararg changes: Pair<String, Double>): ParameterSet {
        val merged = overrides + changes.toMap()
        val copy = ParameterSet(merged)
        val unknown = merged.keys - copy.registry.keys
        require(unknown.isEmpty()) { "unknown parameter keys: $unknown" }
        return copy
    }

    fun get(key: String): Param = registry[key] ?: throw NoSuchElementException(key)

    private fun p(key: String, value: Double, unit: String, description: String, citation: Citation): Param {
        require(registry[key] == null) { "duplicate parameter key $key" }
        return Param(key, overrides[key] ?: value, unit, description, citation).also { registry[key] = it }
    }

    // ---- 4.1 Preprocessing ------------------------------------------------------------------
    val mergeGapMin = p("pre.mergeGapMin", 45.0, "min", "Fragments of one night separated by less than this wake gap are merged", Cites.PLAN)
    val nightWindowStartMin = p("pre.nightWindowStartMin", 1200.0, "min of previous day", "Habitual night window start (20:00 the evening before the wake date)", Cites.PLAN)
    val nightWindowEndMin = p("pre.nightWindowEndMin", 720.0, "min of wake day", "Habitual night window end (12:00 on the wake date)", Cites.PLAN)
    val implausibleMinMin = p("pre.implausibleMinMin", 120.0, "min", "Main sleep shorter than this is flagged and excluded", Cites.PLAN)
    val implausibleMaxMin = p("pre.implausibleMaxMin", 960.0, "min", "Main sleep longer than this is flagged and excluded", Cites.PLAN)
    val shiftDayStartMin = p("pre.shiftDayStartMin", 480.0, "min of day", "Start of the daytime band that signals shift-work sleep (08:00)", Cites.PLAN)
    val shiftDayEndMin = p("pre.shiftDayEndMin", 1080.0, "min of day", "End of the daytime band (18:00)", Cites.PLAN)
    val shiftOverlapFraction = p("pre.shiftOverlapFraction", 0.5, "fraction", "A main sleep is a day sleep when at least this fraction lies in the daytime band", Cites.PLAN)
    val shiftLookbackDays = p("pre.shiftLookbackDays", 7.0, "days", "Look-back for shift-work detection", Cites.PLAN)
    val shiftMinDays = p("pre.shiftMinDays", 3.0, "days", "Day sleeps within the look-back that switch shift-work mode on", Cites.PLAN)
    val lastDayCutoffHour = p("pre.lastDayCutoffHour", 14.0, "hour", "Before this local hour, a missing sleep for today is not yet a missing night", Cites.PLAN)
    val weightPhoneInferred = p("pre.weight.phoneInferred", 0.5, "weight", "Observation weight for phone-inferred episodes", Cites.PLAN)
    val weightPhoneApp = p("pre.weight.phoneApp", 0.7, "weight", "Observation weight for phone-app episodes", Cites.PLAN)
    val weightWrist = p("pre.weight.wrist", 0.9, "weight", "Observation weight for wrist-wearable episodes", Cites.PLAN)
    val weightRing = p("pre.weight.ring", 1.0, "weight", "Observation weight for ring-wearable episodes", Cites.PLAN)
    val completenessFloor = p("pre.completenessFloor", 0.5, "weight", "Weight multiplier of a night with zero completeness (linear up to 1.0)", Cites.PLAN)
    val userConfirmedBoost = p("pre.userConfirmedBoost", 1.25, "x", "Weight multiplier for user-confirmed or user-edited nights", Cites.PLAN)

    fun tierWeight(tier: QualityTier): Double = when (tier) {
        QualityTier.PHONE_INFERRED -> weightPhoneInferred.v
        QualityTier.PHONE_APP -> weightPhoneApp.v
        QualityTier.WEARABLE_WRIST -> weightWrist.v
        QualityTier.WEARABLE_RING -> weightRing.v
    }

    // ---- 4.2 Sleep need ----------------------------------------------------------------------
    val priorNeed18 = p("need.prior.age18_25", 495.0, "min", "Population need prior, age 18-25 (8.25 h)", Cites.KLERMAN)
    val priorNeed26 = p("need.prior.age26_35", 480.0, "min", "Population need prior, age 26-35 (8.0 h)", Cites.HIRSHKOWITZ)
    val priorNeed36 = p("need.prior.age36_50", 465.0, "min", "Population need prior, age 36-50 (7.75 h)", Cites.HIRSHKOWITZ)
    val priorNeed51 = p("need.prior.age51_64", 450.0, "min", "Population need prior, age 51-64 (7.5 h)", Cites.HIRSHKOWITZ)
    val priorNeed65 = p("need.prior.age65plus", 435.0, "min", "Population need prior, age 65+ (7.25 h)", Cites.HIRSHKOWITZ)
    val priorSd = p("need.prior.sd", 36.0, "min", "Standard deviation of the need prior (0.6 h)", Cites.PLAN)
    val reboundBeta = p("need.reboundBeta", 0.3, "fraction", "Free-night excess sleep per minute of previous debt (obs = TST - beta * D)", Cites.KITAMURA)
    val reboundCap = p("need.reboundCap", 120.0, "min", "Upper limit on the rebound correction", Cites.PLAN)
    val processNoise = p("need.processNoiseMinPerMonth", 10.0, "min/month", "Real drift of need (standard deviation per month)", Cites.PLAN)
    val obsSd = p("need.obsSd", 45.0, "min", "Night-to-night standard deviation of a free night around need", Cites.BELENKY)
    val alarmObsSd = p("need.alarmObsSd", 60.0, "min", "Observation standard deviation used for censored (alarm) nights", Cites.TOBIN)
    val freeWakeLateMin = p("need.freeWakeLateMin", 45.0, "min", "Wake later than the workday median by more than this marks a free night", Cites.PLAN)
    val minInformative = p("need.minInformativeNights", 5.0, "nights", "Informative nights needed before the personal estimate replaces the prior", Cites.PLAN)
    val needMin = p("need.min", 300.0, "min", "Physiological lower bound on estimated need (5 h)", Cites.HIRSHKOWITZ)
    val needMax = p("need.max", 660.0, "min", "Physiological upper bound on estimated need (11 h)", Cites.HIRSHKOWITZ)
    val needObsClampLow = p("need.obsClampLow", 240.0, "min", "Observations are clamped to at least this value", Cites.PLAN)
    val needObsClampHigh = p("need.obsClampHigh", 780.0, "min", "Observations are clamped to at most this value", Cites.PLAN)
    val bandZ = p("need.bandZ", 1.645, "z", "Normal quantile for the 90% band", Cites.PLAN)
    val disturbedWaso = p("need.disturbedWasoMin", 60.0, "min", "Wake after sleep onset at or above this marks a night as disturbed (sleep was interrupted, not satisfied)", Cites.OHAYON)
    val alarmNearWakeMin = p("need.alarmNearWakeMin", 45.0, "min", "An alarm event within this distance of the wake counts as an alarm night", Cites.PLAN)

    fun needPrior(age: AgeBand): Double = when (age) {
        AgeBand.AGE_18_25 -> priorNeed18.v
        AgeBand.AGE_26_35 -> priorNeed26.v
        AgeBand.AGE_36_50 -> priorNeed36.v
        AgeBand.AGE_51_64 -> priorNeed51.v
        AgeBand.AGE_65_PLUS -> priorNeed65.v
    }

    // ---- 4.3 Debt ------------------------------------------------------------------------------
    val debtWindowDays = p("debt.windowDays", 14.0, "days", "Leaky accumulation window", Cites.VAN_DONGEN)
    val debtHalfLifeDays = p("debt.halfLifeDays", 7.0, "days", "Half-life of unrepaid debt (lambda = 0.5^(1/halfLife))", Cites.BANKS)
    val debtEta = p("debt.eta", 0.6, "fraction", "Debt repaid per minute of surplus sleep (default before per-user fit)", Cites.BANKS)
    val debtEtaMin = p("debt.etaMin", 0.3, "fraction", "Lower bound on the fitted eta", Cites.PLAN)
    val debtEtaMax = p("debt.etaMax", 1.0, "fraction", "Upper bound on the fitted eta", Cites.PLAN)
    val debtEtaMinPairs = p("debt.etaMinPairs", 4.0, "pairs", "Consecutive free-night pairs needed before eta moves from the default", Cites.PLAN)
    val debtEtaShrink = p("debt.etaShrinkPairs", 4.0, "pairs", "Shrinkage strength toward the default eta (n / (n + this))", Cites.PLAN)
    val debtEtaMinSurplus = p("debt.etaMinSurplusMin", 20.0, "min", "Surplus a free night needs to be used in the eta fit", Cites.PLAN)
    val debtLowMax = p("debt.lowMaxMin", 300.0, "min", "Debt below this is Low (5 h)", Cites.PLAN)
    val debtHighMin = p("debt.highMinMin", 600.0, "min", "Debt above this is High (10 h)", Cites.PLAN)
    val napFullCredit = p("debt.napFullCreditMin", 90.0, "min", "Nap minutes credited in full", Cites.BROOKS_LACK)
    val napOverRate = p("debt.napOverRate", 0.5, "fraction", "Credit rate for nap minutes beyond the full-credit limit", Cites.PLAN)
    val maxMissingRun = p("debt.maxMissingRun", 3.0, "nights", "Consecutive missing nights that make debt unavailable", Cites.PLAN)
    val imputeLookbackDays = p("debt.imputeLookbackDays", 56.0, "days", "Look-back for same-weekday median imputation", Cites.PLAN)
    val imputeMinSamples = p("debt.imputeMinSamples", 2.0, "nights", "Same-weekday samples needed to impute (else overall median)", Cites.PLAN)
    val imputeBandPerDay = p("debt.imputeBandPerDayMin", 25.0, "min/night", "Debt band widening per imputed night", Cites.PLAN)

    // ---- 4.4 Regularity and chronotype ----------------------------------------------------------
    val sriEpochMin = p("reg.sriEpochMin", 1.0, "min", "Epoch length of the sleep/wake vectors", Cites.PHILLIPS)
    val sriMinPairs = p("reg.sriMinPairedDays", 5.0, "days", "Paired days needed to report SRI", Cites.PLAN)
    val sriShortDays = p("reg.sriShortDays", 7.0, "days", "Short SRI window", Cites.PHILLIPS)
    val sriLongDays = p("reg.sriLongDays", 14.0, "days", "Long SRI window", Cites.PHILLIPS)
    val sriTrendDays = p("reg.sriTrendDays", 28.0, "days", "Days of daily SRI trend returned", Cites.PLAN)
    val spreadDays = p("reg.spreadDays", 14.0, "days", "Window for circular timing spread", Cites.PLAN)
    val spreadMinNights = p("reg.spreadMinNights", 5.0, "nights", "Nights needed to report timing spread", Cites.PLAN)
    val chronoLookbackDays = p("reg.chronoLookbackDays", 56.0, "days", "Look-back for free/work night timing", Cites.ROENNEBERG_2003)
    val chronoBlendNights = p("reg.chronoBlendNights", 4.0, "nights", "Free nights after which the questionnaire no longer contributes", Cites.PLAN)
    val chronoEarlyBelow = p("reg.chronoEarlyBelowMin", 210.0, "min after midnight", "MSFsc below this is Early (03:30)", Cites.ROENNEBERG_2003)
    val chronoLateAbove = p("reg.chronoLateAboveMin", 300.0, "min after midnight", "MSFsc above this is Late (05:00)", Cites.ROENNEBERG_2003)
    val chronoPopulation = p("reg.chronoPopulationMin", 270.0, "min after midnight", "Population MSFsc used when the user gave no questionnaire answer", Cites.ROENNEBERG_2003)
    val chronoQuestionnaireSd = p("reg.chronoQuestionnaireSd", 75.0, "min", "Uncertainty of the questionnaire-based chronotype", Cites.DERIVED_MEQ)
    val chronoDataSd = p("reg.chronoDataSd", 45.0, "min", "Per-night uncertainty of free-night mid-sleep", Cites.ROENNEBERG_SLEEP_DEBT)
    val chronoAnswerDefMorning = p("reg.chrono.defMorning", 180.0, "min after midnight", "Questionnaire prior: definitely morning", Cites.DERIVED_MEQ)
    val chronoAnswerModMorning = p("reg.chrono.modMorning", 225.0, "min after midnight", "Questionnaire prior: moderately morning", Cites.DERIVED_MEQ)
    val chronoAnswerNeither = p("reg.chrono.neither", 270.0, "min after midnight", "Questionnaire prior: neither", Cites.DERIVED_MEQ)
    val chronoAnswerModEvening = p("reg.chrono.modEvening", 330.0, "min after midnight", "Questionnaire prior: moderately evening", Cites.DERIVED_MEQ)
    val chronoAnswerDefEvening = p("reg.chrono.defEvening", 390.0, "min after midnight", "Questionnaire prior: definitely evening", Cites.DERIVED_MEQ)
    val sjlMinNights = p("reg.sjlMinNights", 2.0, "nights", "Free and work nights each needed for social jetlag", Cites.WITTMANN)

    fun chronoPrior(answer: ChronotypeAnswer?): Double = when (answer) {
        ChronotypeAnswer.DEFINITELY_MORNING -> chronoAnswerDefMorning.v
        ChronotypeAnswer.MODERATELY_MORNING -> chronoAnswerModMorning.v
        ChronotypeAnswer.NEITHER -> chronoAnswerNeither.v
        ChronotypeAnswer.MODERATELY_EVENING -> chronoAnswerModEvening.v
        ChronotypeAnswer.DEFINITELY_EVENING -> chronoAnswerDefEvening.v
        null -> chronoPopulation.v
    }

    // ---- 4.5 Circadian ----------------------------------------------------------------------------
    val dlmoOnsetOffset = p("circ.dlmoBeforeOnsetMin", 150.0, "min", "DLMO precedes habitual sleep onset by this much (2.5 h)", Cites.WRIGHT_2005)
    val dlmoMidpointOffset = p("circ.dlmoBeforeMidpointMin", 390.0, "min", "DLMO precedes mid-sleep by this much (6.5 h)", Cites.WRIGHT_2005)
    val dlmoOnsetWeight = p("circ.onsetWeightFreeNight", 0.5, "weight", "Weight of the onset-based estimate on free nights (rest goes to the midpoint)", Cites.PLAN)
    val dlmoProcessSd = p("circ.processSdMinPerDay", 10.0, "min/day", "Day-to-day random walk of the DLMO state", Cites.BURGESS_FOGG)
    val dlmoShiftProcessSd = p("circ.shiftProcessSdMinPerDay", 30.0, "min/day", "Random walk in shift-work mode", Cites.PLAN)
    val dlmoObsSd = p("circ.obsSd", 45.0, "min", "Night-level scatter of sleep-timing-based DLMO", Cites.BURGESS_FOGG)
    val dlmoMappingSd = p("circ.mappingSd", 30.0, "min", "Individual error of the sleep-to-DLMO mapping, added to the band", Cites.BURGESS_FOGG)
    val dlmoAlarmVarMult = p("circ.alarmVarianceMultiplier", 2.0, "x", "Observation variance multiplier for alarm nights", Cites.PLAN)
    val dlmoShiftVarMult = p("circ.shiftVarianceMultiplier", 3.0, "x", "Additional variance multiplier for shift nights", Cites.PLAN)
    val dlmoReentrainVarMult = p("circ.reentrainVarianceMultiplier", 8.0, "x", "Observation variance multiplier while the clock is re-entraining", Cites.PLAN)
    val dlmoPriorSd = p("circ.priorSd", 90.0, "min", "Uncertainty of the questionnaire-based DLMO prior", Cites.DERIVED_MEQ)
    val shiftBandMult = p("circ.shiftBandMultiplier", 2.0, "x", "Band widening in shift-work mode", Cites.PLAN)
    val advanceRate = p("circ.advanceRateMinPerDay", 60.0, "min/day", "Re-entrainment rate for eastward (advance) shifts", Cites.EASTMAN_BURGESS)
    val delayRate = p("circ.delayRateMinPerDay", 90.0, "min/day", "Re-entrainment rate for westward (delay) shifts", Cites.EASTMAN_BURGESS)
    val zoneChangeMin = p("circ.zoneChangeMinMinutes", 30.0, "min", "Offset change (with a different zone id) that counts as travel", Cites.PLAN)
    val reentrainReportMin = p("circ.reentrainReportMin", 15.0, "min", "Remaining shift below which re-entrainment is not reported", Cites.PLAN)
    val melatoninStartAfterDlmo = p("circ.melatoninStartAfterDlmoMin", 120.0, "min", "Melatonin window starts this long after DLMO", Cites.PLAN)
    val melatoninLength = p("circ.melatoninLengthMin", 60.0, "min", "Melatonin window length", Cites.PLAN)
    val windDownBeforeWindow = p("circ.windDownBeforeWindowMin", 60.0, "min", "Wind-down starts this long before the melatonin window", Cites.PLAN)

    // ---- 4.6 Energy (three-process model) ------------------------------------------------------------
    val tauWake = p("energy.tauWakeMin", 1092.0, "min", "Homeostatic decay time constant while awake (18.2 h)", Cites.DAAN)
    val tauSleep = p("energy.tauSleepMin", 252.0, "min", "Homeostatic recovery time constant while asleep (4.2 h)", Cites.DAAN)
    val refNeed = p("energy.referenceNeedMin", 480.0, "min", "Need at which sleep recovers at the nominal tau (sleep minutes are scaled by this / need)", Cites.PLAN)
    val homeoWeight = p("energy.homeostaticWeight", 0.55, "weight", "Weight of S in the alertness sum", Cites.AKERSTEDT_FOLKARD_1995)
    val circAmp = p("energy.circadianAmplitude", 0.20, "units", "Amplitude of the 24 h circadian cosine", Cites.AKERSTEDT_FOLKARD_1995)
    val circHarmonicAmp = p("energy.harmonicAmplitude", 0.10, "units", "Amplitude of the 12 h harmonic (post-lunch dip)", Cites.AKERSTEDT_FOLKARD_1997)
    val circAcrophase = p("energy.acrophaseAfterDlmoMin", -240.0, "min", "Peak of the 24 h component relative to DLMO (4 h before)", Cites.AKERSTEDT_FOLKARD_1995)
    val harmonicAcrophase = p("energy.harmonicPeakAfterDlmoMin", -780.0, "min", "Peak of the 12 h harmonic relative to DLMO (trough about 14:00 for DLMO 21:00)", Cites.AKERSTEDT_FOLKARD_1997)
    val inertiaTau = p("energy.inertiaTauMin", 40.0, "min", "Sleep inertia decay time constant", Cites.TASSI)
    val inertiaBase = p("energy.inertiaBase", 0.22, "units", "Initial inertia depression after a full night", Cites.HILDITCH)
    val inertiaPerShortHour = p("energy.inertiaPerShortfallHour", 0.20, "x per h", "Fractional increase of inertia per hour of sleep shortfall", Cites.HILDITCH)
    val inertiaPerDebtHour = p("energy.inertiaPerDebtHour", 0.04, "x per h", "Fractional increase of inertia per hour of debt", Cites.HILDITCH)
    val inertiaMaxScale = p("energy.inertiaMaxScale", 2.0, "x", "Cap on the inertia scale-up", Cites.PLAN)
    val inertiaNapScale = p("energy.inertiaNapScale", 0.5, "x", "Inertia scale after a nap of an hour or more (shorter naps scale down linearly)", Cites.TASSI)
    val napDurationMax = p("energy.napMaxMin", 120.0, "min", "Sleep episodes shorter than this use nap inertia", Cites.PLAN)
    val inertiaEndLevel = p("energy.inertiaEndLevel", 0.04, "units", "Grogginess ends when inertia falls below this", Cites.PLAN)
    val levelLow = p("energy.levelLow", 0.12, "units", "Raw alertness mapped to level 0", Cites.PLAN)
    val levelHigh = p("energy.levelHigh", 0.59, "units", "Raw alertness mapped to level 100", Cites.PLAN)
    val simHistoryDays = p("energy.simHistoryDays", 14.0, "days", "Days of real sleep fed to the homeostat", Cites.PLAN)
    val awakeEvidenceMin = p("energy.awakeEvidenceMin", 45.0, "min", "Screen use or steps this recent, during the usual night, means the person is still up", Cites.PLAN)
    val simStepMin = p("energy.stepMin", 5.0, "min", "Simulation resolution", Cites.PLAN)
    val simStartLevel = p("energy.startHomeostat", 0.75, "units", "Homeostat state at the start of the simulation window", Cites.PLAN)
    val dipProminence = p("energy.dipProminence", 5.0, "level pts", "Minimum prominence of an afternoon dip", Cites.PLAN)
    val smoothPoints = p("energy.smoothPoints", 3.0, "points", "Moving-average width before extremum search", Cites.PLAN)
    val calibMinCheckIns = p("energy.calibration.minCheckIns", 10.0, "check-ins", "Check-ins needed before the curve is calibrated", Cites.PLAN)
    val calibAmpMin = p("energy.calibration.ampMin", 0.6, "x", "Lower bound of the fitted circadian amplitude multiplier", Cites.PLAN)
    val calibAmpMax = p("energy.calibration.ampMax", 1.6, "x", "Upper bound of the fitted circadian amplitude multiplier", Cites.PLAN)
    val calibPhaseMax = p("energy.calibration.phaseMaxMin", 180.0, "min", "Bound of the fitted phase offset", Cites.PLAN)
    val calibRidge = p("energy.calibration.ridge", 4.0, "level^2", "Ridge penalty pulling the fit toward no adjustment (per unit of normalized distance)", Cites.PLAN)
    val rtBest = p("energy.calibration.rtBestMs", 250.0, "ms", "Median reaction time mapped to level 100", Cites.PLAN)
    val rtWorst = p("energy.calibration.rtWorstMs", 600.0, "ms", "Median reaction time mapped to level 0", Cites.PLAN)
    val rtLapsePenalty = p("energy.calibration.lapsePenalty", 25.0, "level pts", "Level penalty at a 100% lapse rate", Cites.PLAN)

    // ---- 4.6b Energy potential (SAFTE-style reservoir) -------------------------------------------------
    val potentialTauHours = p("potential.tauHours", 24.0, "h", "Debt at which potential falls to 1/e of full (potential = 100 * exp(-debt / tau))", Cites.HURSH_SAFTE)
    val potentialSuperMin = p("potential.superMin", 92.0, "score", "Potential at or above this is Super (debt under about 2 h)", Cites.PLAN)
    val potentialGreatMin = p("potential.greatMin", 80.0, "score", "Potential at or above this is Great (debt under about 5.3 h, the usual 5 h target)", Cites.BELENKY)
    val potentialGoodMin = p("potential.goodMin", 68.0, "score", "Potential at or above this is Good", Cites.PLAN)
    val potentialFairMin = p("potential.fairMin", 55.0, "score", "Potential at or above this is Fair; below is Low", Cites.PLAN)
    val debtCurveScale = p("energy.debtCurveScale", 0.5, "level pts per pt", "Curve level lost per point of potential below 100", Cites.HURSH_SAFTE)
    val debtDipBoost = p("energy.debtDipBoost", 1.0, "x", "Extra loss at low levels, so debt deepens dips more than it lowers peaks", Cites.HURSH_SAFTE)
    val catchUpHorizon = p("potential.catchUpHorizonNights", 14.0, "nights", "Nights projected when estimating the catch-up time", Cites.PLAN)

    // ---- 4.7 Recommendations ----------------------------------------------------------------------------
    val sleepEfficiencyDefault = p("rec.sleepEfficiencyDefault", 0.88, "fraction", "Sleep efficiency (asleep / in bed) when the user has none recorded", Cites.PLAN)
    val seMinNights = p("rec.sleepEfficiencyMinNights", 5.0, "nights", "Nights with in-bed data needed to use the personal median", Cites.PLAN)
    val debtPaybackFraction = p("rec.debtPaybackFraction", 0.25, "fraction", "Fraction of current debt added to tonight's opportunity", Cites.BANKS)
    val debtPaybackCap = p("rec.debtPaybackCapMin", 60.0, "min", "Cap on the debt payback added to tonight's opportunity", Cites.BANKS)
    val bedConflictTolerance = p("rec.bedConflictToleranceMin", 45.0, "min", "Bedtime earlier than the melatonin window by more than this is a conflict", Cites.PLAN)
    val caffeineHours = p("habit.caffeineCutoffHours", 8.0, "h", "Caffeine cutoff before bedtime", Cites.DRAKE)
    val mealHours = p("habit.lastMealHours", 3.0, "h", "Last large meal before bedtime", Cites.PLAN)
    val alcoholHours = p("habit.alcoholCutoffHours", 3.0, "h", "Alcohol cutoff before bedtime", Cites.EBRAHIM)
    val lightWindowMin = p("habit.morningLightWithinMin", 60.0, "min", "Seek bright light within this long after waking", Cites.WRIGHT_2013)
    val exerciseAfterWakeMin = p("habit.exerciseAfterWakeMin", 60.0, "min", "Intense exercise window opens this long after waking and closes at wind-down", Cites.YOUNGSTEDT)
    val dimLightsFromWindDown = p("habit.dimLightsFromWindDown", 1.0, "flag", "Dim lights from the start of wind-down", Cites.GOOLEY)
    val napMinMin = p("nap.minMin", 20.0, "min", "Shortest recommended nap", Cites.BROOKS_LACK)
    val napMaxMin = p("nap.maxMin", 30.0, "min", "Longest recommended nap", Cites.BROOKS_LACK)
    val napEndBeforeBedMin = p("nap.endBeforeBedMin", 420.0, "min", "Nap must end at least this long before bedtime", Cites.PLAN)
    val napMinDebtMin = p("nap.minDebtMin", 300.0, "min", "Debt at which naps are suggested (Moderate)", Cites.PLAN)
    val defaultWakeMin = p("rec.defaultWakeMin", 450.0, "min of day", "Wake clock used when the user has no schedule and no history (07:30)", Cites.PLAN)

    companion object {
        /** Parameters of engine 1.1.x. */
        val DEFAULT: ParameterSet by lazy { ParameterSet(emptyMap()) }
    }
}
