package app.drift.engine.internal

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

internal const val DAY_MIN = 1440.0
internal const val DAY_MIN_L = 1440L

/** Circular helpers on a 24 h clock expressed in minutes. */
internal object Circ {
    fun mod(x: Double): Double {
        val r = x - DAY_MIN * floor(x / DAY_MIN)
        return if (r >= DAY_MIN) 0.0 else r
    }

    /** Wraps a difference into [-720, 720). */
    fun wrap(x: Double): Double = mod(x + DAY_MIN / 2) - DAY_MIN / 2

    fun mean(values: List<Double>, weights: List<Double>? = null): Double? {
        if (values.isEmpty()) return null
        var c = 0.0
        var s = 0.0
        for (i in values.indices) {
            val w = weights?.get(i) ?: 1.0
            val a = values[i] / DAY_MIN * 2 * PI
            c += w * cos(a)
            s += w * sin(a)
        }
        if (c == 0.0 && s == 0.0) return mod(values[0])
        return mod(atan2(s, c) / (2 * PI) * DAY_MIN)
    }

    /** Circular standard deviation in minutes, sqrt(-2 ln R). */
    fun sd(values: List<Double>): Double? {
        if (values.size < 2) return null
        var c = 0.0
        var s = 0.0
        for (v in values) {
            val a = v / DAY_MIN * 2 * PI
            c += cos(a)
            s += sin(a)
        }
        val r = sqrt(c * c + s * s) / values.size
        val rr = r.coerceIn(1e-9, 1.0)
        return sqrt(-2 * ln(rr)) / (2 * PI) * DAY_MIN
    }
}

internal object Stats {
    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    fun mean(values: List<Double>): Double? = if (values.isEmpty()) null else values.sum() / values.size

    /** Abramowitz and Stegun 7.1.26 approximation of the standard normal CDF (abs. error < 1.5e-7). */
    fun normalCdf(x: Double): Double {
        val z = abs(x) / sqrt(2.0)
        val t = 1.0 / (1.0 + 0.3275911 * z)
        val poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))))
        val erf = 1.0 - poly * exp(-z * z)
        return if (x >= 0) 0.5 * (1.0 + erf) else 0.5 * (1.0 - erf)
    }

    /** Inverse Mills ratio phi(a) / (1 - Phi(a)), stable in the upper tail. */
    fun inverseMills(a: Double): Double {
        if (a > 3.0) {
            var f = a
            for (k in 12 downTo 1) f = a + k / f
            return f
        }
        val tail = (1.0 - normalCdf(a)).coerceAtLeast(1e-12)
        return normalPdf(a) / tail
    }
    fun normalPdf(x: Double): Double = exp(-0.5 * x * x) / sqrt(2 * PI)
}

internal fun Double.clamp(lo: Double, hi: Double): Double = if (this < lo) lo else if (this > hi) hi else this
