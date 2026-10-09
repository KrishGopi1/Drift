package app.drift.engine.models

import app.drift.engine.ParameterSet
import app.drift.model.Confidence
import app.drift.model.Provenance
import app.drift.model.Quantity
import app.drift.model.engine.CatchUp
import app.drift.model.engine.EnergyLabel
import app.drift.model.engine.EnergyPotential
import app.drift.model.engine.PotentialDay
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Model 4.6b: energy potential and catch-up. A SAFTE-style reservoir view of sleep debt (Hursh 2004): the more debt,
 * the less of a rested day's energy is available. potential = 100 * exp(-debt / tau). The same score scales the energy
 * curve (lower peaks, deeper dips), and projecting the recommended schedule forward gives the nights until Great.
 */
internal class PotentialModel(private val p: ParameterSet) {

    fun score(debtMin: Double): Double = 100.0 * exp(-maxOf(0.0, debtMin) / (p.potentialTauHours.v * 60.0))

    fun label(score: Double): EnergyLabel = when {
        score >= p.potentialSuperMin.v -> EnergyLabel.SUPER
        score >= p.potentialGreatMin.v -> EnergyLabel.GREAT
        score >= p.potentialGoodMin.v -> EnergyLabel.GOOD
        score >= p.potentialFairMin.v -> EnergyLabel.FAIR
        else -> EnergyLabel.LOW
    }

    /** Level points lost at curve level [level] (0..100) for a day carrying [debtMin] of debt. */
    fun curvePenalty(level: Double, debtMin: Double): Double {
        val missing = 100.0 - score(debtMin)
        return p.debtCurveScale.v * missing * (1.0 + p.debtDipBoost.v * (1.0 - level / 100.0))
    }

    fun potential(debt: DebtResult): EnergyPotential {
        val s = round1(score(debt.minutes))
        // More debt means a lower score, so the debt's upper bound gives the score's lower bound.
        val lo = round1(score(debt.upper))
        val hi = round1(score(debt.lower))
        val q = Quantity(s, Provenance.ESTIMATED, Confidence(minOf(lo, s), maxOf(hi, s), 0.9, debt.level), debt.sources)
        val trend = debt.daily.map { d ->
            val v = round1(score(d.debtAfter.seconds / 60.0))
            PotentialDay(d.date, v, label(v))
        }
        return EnergyPotential(q, label(s), trend)
    }

    /**
     * Follows the plan's rule each night: sleep = need + min(fraction * debt, cap) (what the recommended bedtime and
     * wake time give at the user's sleep efficiency), then one step of the leaky accumulator.
     */
    fun catchUp(debtMin: Double, needMin: Double, eta: Double, lambda: Double, firstNight: LocalDate): CatchUp {
        var debt = debtMin
        val projection = ArrayList<Pair<LocalDate, java.time.Duration>>()
        var nights: Int? = if (score(debt) >= p.potentialGreatMin.v) 0 else null
        for (k in 0 until p.catchUpHorizon.i) {
            val payback = minOf(p.debtPaybackFraction.v * debt, p.debtPaybackCap.v)
            debt = DebtMath.step(debt, needMin, needMin + payback, lambda, eta)
            projection += firstNight.plusDays(k.toLong()) to minutesToDuration(debt)
            if (nights == null && score(debt) >= p.potentialGreatMin.v) nights = k + 1
        }
        return CatchUp(nights, projection)
    }

    private fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0
}
