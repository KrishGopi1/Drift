package app.drift.source.phone.detect

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln

/** Tunable constants of the sleep/awake model. Emission terms are log-likelihood ratios (asleep vs awake). */
internal data class HmmParams(
    val pAwakeToAsleep: Double = 0.02,
    val pAsleepToAwake: Double = 0.05,
    val pAsleepAtStart: Double = 0.05,
    /** Time-of-day prior: amplitude and offset of the cosine peaking at [todPeakHour]. */
    val todAmplitude: Double = 1.5,
    val todOffset: Double = -0.3,
    val todPeakHour: Double = 3.5,
    /** P(no screen use in a bin | asleep / awake). */
    val pQuietAsleep: Double = 0.97,
    val pQuietAwake: Double = 0.6,
    val apiWeight: Double = 0.8,
    val chargingAsleepRatio: Double = 0.8 / 0.3,
)

internal enum class Hidden { AWAKE, ASLEEP }

internal data class HmmResult(
    val path: List<Hidden>,
    /** Posterior P(asleep) per bin from forward-backward. */
    val posterior: List<Double>,
)

internal class TwoStateHmm(private val p: HmmParams = HmmParams()) {

    /** ln P(evidence | asleep) - ln P(evidence | awake) for one bin. */
    fun logRatio(bin: Bin): Double {
        var lr = p.todAmplitude * cos(2 * PI * (bin.localHour - p.todPeakHour) / 24.0) + p.todOffset
        bin.screenOnMinutes?.let { m ->
            lr += if (m >= 0.5) ln(0.01 / 0.4) else ln(p.pQuietAsleep / p.pQuietAwake)
        }
        bin.sleepApi?.let { c ->
            val cc = c.coerceIn(0.03, 0.97)
            lr += p.apiWeight * ln(cc / (1 - cc))
        }
        // Being unplugged says little (many people sleep unplugged), so only plugged-in counts as evidence.
        if (bin.charging == true) lr += ln(p.chargingAsleepRatio)
        bin.steps?.let { s ->
            lr += when {
                s >= 20 -> ln(0.01 / 0.35)
                s >= 6 -> ln(0.02 / 0.1)
                else -> ln(0.97 / 0.5)
            }
        }
        return lr
    }

    fun decode(bins: List<Bin>): HmmResult {
        val n = bins.size
        if (n == 0) return HmmResult(emptyList(), emptyList())
        val stay = arrayOf(ln(1 - p.pAwakeToAsleep), ln(1 - p.pAsleepToAwake))
        val lnA2S = ln(p.pAwakeToAsleep)
        val lnS2A = ln(p.pAsleepToAwake)
        val lr = DoubleArray(n) { logRatio(bins[it]) }

        // Viterbi in log space; awake emission is the reference (0), asleep emission is lr.
        val v = Array(2) { DoubleArray(n) }
        val back = Array(2) { IntArray(n) }
        v[0][0] = ln(1 - p.pAsleepAtStart)
        v[1][0] = ln(p.pAsleepAtStart) + lr[0]
        for (i in 1 until n) {
            val toAwakeFromAwake = v[0][i - 1] + stay[0]
            val toAwakeFromAsleep = v[1][i - 1] + lnS2A
            if (toAwakeFromAwake >= toAwakeFromAsleep) { v[0][i] = toAwakeFromAwake; back[0][i] = 0 } else { v[0][i] = toAwakeFromAsleep; back[0][i] = 1 }
            val toAsleepFromAwake = v[0][i - 1] + lnA2S
            val toAsleepFromAsleep = v[1][i - 1] + stay[1]
            if (toAsleepFromAsleep >= toAsleepFromAwake) { v[1][i] = toAsleepFromAsleep + lr[i]; back[1][i] = 1 } else { v[1][i] = toAsleepFromAwake + lr[i]; back[1][i] = 0 }
        }
        val path = MutableList(n) { Hidden.AWAKE }
        var s = if (v[1][n - 1] > v[0][n - 1]) 1 else 0
        for (i in n - 1 downTo 0) {
            path[i] = if (s == 1) Hidden.ASLEEP else Hidden.AWAKE
            s = back[s][i]
        }
        return HmmResult(path, posterior(lr, stay, lnA2S, lnS2A))
    }

    /** Scaled forward-backward on the same model. */
    private fun posterior(lr: DoubleArray, stay: Array<Double>, lnA2S: Double, lnS2A: Double): List<Double> {
        val n = lr.size
        val tAA = exp(stay[0]); val tAS = exp(lnA2S); val tSA = exp(lnS2A); val tSS = exp(stay[1])
        // Emission likelihoods relative to awake=1 (ratio form is valid since scaling cancels in normalization).
        val e1 = DoubleArray(n) { exp(lr[it].coerceIn(-30.0, 30.0)) }
        val fA = DoubleArray(n); val fS = DoubleArray(n)
        fA[0] = 1 - p.pAsleepAtStart; fS[0] = p.pAsleepAtStart * e1[0]
        normalize(fA, fS, 0)
        for (i in 1 until n) {
            fA[i] = fA[i - 1] * tAA + fS[i - 1] * tSA
            fS[i] = (fA[i - 1] * tAS + fS[i - 1] * tSS) * e1[i]
            normalize(fA, fS, i)
        }
        val bA = DoubleArray(n); val bS = DoubleArray(n)
        bA[n - 1] = 1.0; bS[n - 1] = 1.0
        for (i in n - 2 downTo 0) {
            bA[i] = tAA * bA[i + 1] + tAS * e1[i + 1] * bS[i + 1]
            bS[i] = tSA * bA[i + 1] + tSS * e1[i + 1] * bS[i + 1]
            val z = bA[i] + bS[i]
            bA[i] /= z; bS[i] /= z
        }
        return List(n) { i ->
            val a = fA[i] * bA[i]; val s = fS[i] * bS[i]
            if (a + s <= 0.0) 0.0 else s / (a + s)
        }
    }

    private fun normalize(a: DoubleArray, s: DoubleArray, i: Int) {
        val z = a[i] + s[i]
        if (z > 0.0) { a[i] /= z; s[i] /= z }
    }
}
