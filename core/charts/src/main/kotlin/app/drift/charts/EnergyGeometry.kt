package app.drift.charts

import app.drift.model.engine.DayCurve
import java.time.Duration
import java.time.Instant

/** Pure geometry for energy curves; also usable by the home-screen widget, which draws without Compose. */
object EnergyGeometry {
    /** Time span covered by the curve's points, or null if there are fewer than two points. */
    fun range(curve: DayCurve): ClosedRange<Instant>? {
        val pts = curve.points
        if (pts.size < 2) return null
        return pts.first().at..pts.last().at
    }

    /** 0..1 position of [t] within [range] (clamped). */
    fun fraction(range: ClosedRange<Instant>, t: Instant): Float {
        val total = Duration.between(range.start, range.endInclusive).toMillis().toDouble()
        if (total <= 0) return 0f
        return (Duration.between(range.start, t).toMillis() / total).coerceIn(0.0, 1.0).toFloat()
    }

    /** Linear interpolation of the energy level (0..100) at [t]; null if outside the curve. */
    fun levelAt(curve: DayCurve, t: Instant): Double? {
        val pts = curve.points
        if (pts.isEmpty() || t < pts.first().at || t > pts.last().at) return null
        var lo = 0
        var hi = pts.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (pts[mid].at <= t) lo = mid else hi = mid
        }
        val a = pts[lo]
        val b = pts[hi]
        val span = Duration.between(a.at, b.at).toMillis()
        if (span <= 0) return a.level
        val f = Duration.between(a.at, t).toMillis().toDouble() / span
        return a.level + (b.level - a.level) * f
    }

    /** Curve points as (x, y) in a box of [width] x [height], y growing downwards, level 100 at the top. */
    fun normalizedPoints(curve: DayCurve, width: Float, height: Float): List<Pair<Float, Float>> {
        val r = range(curve) ?: return emptyList()
        return curve.points.map { p -> fraction(r, p.at) * width to (1f - (p.level / 100.0).coerceIn(0.0, 1.0).toFloat()) * height }
    }
}
