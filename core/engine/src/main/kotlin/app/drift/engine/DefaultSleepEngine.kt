package app.drift.engine

import app.drift.engine.internal.Circ
import app.drift.engine.models.CircadianModel
import app.drift.engine.models.DebtModel
import app.drift.engine.models.EnergyModel
import app.drift.engine.models.ExplanationModel
import app.drift.engine.models.FreeNightDetector
import app.drift.engine.models.Habitual
import app.drift.engine.models.Headline
import app.drift.engine.models.NightType
import app.drift.engine.models.Preprocessor
import app.drift.engine.models.RecommendationModel
import app.drift.engine.models.RegularityModel
import app.drift.engine.models.SleepNeedModel
import app.drift.engine.models.minutesToDuration
import app.drift.model.Confidence
import app.drift.model.ConfidenceLevel
import app.drift.model.Provenance
import app.drift.model.Quantity
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.EngineInput
import app.drift.model.engine.EngineVersion
import app.drift.model.engine.SignalWindow
import app.drift.model.engine.SleepEngine
import app.drift.model.engine.Sufficiency
import app.drift.model.engine.SufficiencyTier


/**
 * The eight analysis models of plan section 4 run in order: preprocessing, sleep need, debt, regularity,
 * circadian phase, recommendations (sleep plan), energy curve, recommendations (habits, naps), explanations
 * and data sufficiency. Pure and deterministic: no clock, no I/O, no randomness.
 *
 * With [failSafe] set, an unexpected internal error degrades to a priors-only snapshot instead of throwing.
 */
class DefaultSleepEngine(
    private val params: ParameterSet = ParameterSet.DEFAULT,
    private val failSafe: Boolean = true,
) : SleepEngine {
    override val version: EngineVersion = ENGINE_VERSION

    override fun analyze(input: EngineInput): AnalysisSnapshot {
        val hash = InputHasher.hash(input, version.toString())
        if (!failSafe) return compute(input, hash)
        return try {
            compute(input, hash)
        } catch (e: RuntimeException) {
            compute(
                input.copy(
                    episodes = emptyList(),
                    signals = SignalWindow.empty(input.now),
                    annotations = emptyList(),
                    checkIns = emptyList(),
                ),
                hash,
            )
        }
    }

    private fun compute(input: EngineInput, hash: String): AnalysisSnapshot {
        val p = params
        val pre = Preprocessor(p).run(input)
        val detector = FreeNightDetector(p).classify(pre, input)
        val types = detector.types
        val need = SleepNeedModel(p).run(pre, types, input)
        // Debt is measured against the need established before the 14-day window, so that the window's own
        // nights are not both the yardstick and the thing measured (a short night must never look "repaid"
        // because it lowered the need estimate).
        val windowStart = pre.lastDay - p.debtWindowDays.i + 1
        val baseline = pre.before(windowStart).let { early ->
            val t = FreeNightDetector(p).classify(early, input).types
            SleepNeedModel(p).run(early, t, input)
        }
        val debtNeed = if (baseline.personal) baseline else need
        val debt = DebtModel(p).run(pre, debtNeed)
        val regularity = RegularityModel(p).run(pre, types, input)
        val rec = RecommendationModel(p)
        val habitual = Habitual.of(pre, input, p, need.displayed, rec.sleepEfficiency(pre))
        val circ = CircadianModel(p).run(pre, types, input, habitual)
        val freeWakes = pre.usable.filter { types[it.day] == NightType.FREE }.takeLast(28).map { it.main!!.wakeClock }
        val plan = rec.plan(pre, input, need, debt?.minutes, circ.estimate, habitual, detector.workdayMedianWake, freeWakes)

        val days = pre.usable.size
        val tier = when {
            days == 0 -> SufficiencyTier.PRIORS_ONLY
            days < 5 -> SufficiencyTier.EARLY
            days < 14 -> SufficiencyTier.PERSONALIZING
            else -> SufficiencyTier.FULL
        }
        val energyLevel = when (tier) {
            SufficiencyTier.PRIORS_ONLY, SufficiencyTier.EARLY -> ConfidenceLevel.LOW
            SufficiencyTier.PERSONALIZING -> ConfidenceLevel.MEDIUM
            SufficiencyTier.FULL -> if (need.level == ConfidenceLevel.HIGH) ConfidenceLevel.HIGH else ConfidenceLevel.MEDIUM
        }
        val energy = EnergyModel(p).run(pre, input, need.displayed, debt?.minutes ?: 0.0, circ, habitual, plan, energyLevel)
        val recommendations = rec.complete(plan, pre, input, circ.estimate, energy, debt?.minutes, debt?.band)

        val sufficiency = Sufficiency(
            tier = tier,
            daysOfHistory = days,
            informativeNights = need.informativeNights,
            nightsUntilPersonalNeed = maxOf(0, p.minInformative.i - need.informativeNights),
            debtAvailable = debt != null,
            regularityAvailable = regularity.sri7 != null || regularity.sri14 != null,
        )

        val needQ = Quantity(
            minutesToDuration(need.displayed), Provenance.ESTIMATED,
            Confidence(minutesToDuration(need.lower), minutesToDuration(need.upper), 0.9, need.level),
            need.sources,
        )
        val debtQ = debt?.let {
            Quantity(
                minutesToDuration(it.minutes), Provenance.ESTIMATED,
                Confidence(minutesToDuration(it.lower), minutesToDuration(it.upper), 0.9, it.level),
                it.sources,
            )
        }

        val bedClock = Circ.mod(run {
            val bedAbs = Math.floorDiv(plan.bed.epochSecond, 60L)
            val off = app.drift.engine.internal.offsetMin(input.zone, bedAbs)
            (bedAbs + off).toDouble()
        })
        val edited = pre.nights.filter { n -> n.main?.userEdited == true }.map { it.day }.toSet()
        val headline = Headline(
            todayDay = pre.todayDay,
            needMin = need.displayed,
            debtNeedMin = debtNeed.displayed,
            needPersonal = need.personal,
            informative = need.informativeNights,
            debtMin = debt?.minutes,
            daily = debt?.daily.orEmpty(),
            editedDays = edited,
            sri7 = regularity.sri7?.value,
            dlmo = circ.dlmo,
            reentraining = circ.estimate.reentrainment != null,
            sourceCount = pre.sources.size,
            calibration = energy.calibrationCheckIns,
            bedClock = bedClock,
            nights = days,
        )
        val explainer = ExplanationModel(p)
        val explanations = explainer.explain(input.priorState, version, headline)
        val state = explainer.toState(headline, version, input.now)

        return AnalysisSnapshot(
            engineVersion = version,
            inputHash = hash,
            computedAt = input.now,
            sleepNeed = needQ,
            sleepDebt = debtQ,
            debtBand = debt?.band,
            debtTrend = debt?.daily.orEmpty(),
            regularity = regularity,
            circadian = circ.estimate,
            energyCurve = energy,
            recommendations = recommendations,
            explanations = explanations,
            state = state,
            dataSufficiency = sufficiency,
        )
    }
}
