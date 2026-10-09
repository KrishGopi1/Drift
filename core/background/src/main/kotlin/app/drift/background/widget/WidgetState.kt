package app.drift.background.widget

import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.EnergyPoint
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** What the widget shows, decided purely from the latest snapshot. */
sealed interface WidgetState {
    /** No snapshot yet, or no curve for today. */
    data object Empty : WidgetState
    data class Fresh(val model: WidgetModel) : WidgetState
    /** Snapshot older than [WidgetStateMapper.STALE_AFTER]; the curve is shown dimmed with a refresh hint. */
    data class Stale(val model: WidgetModel, val age: Duration) : WidgetState
}

data class WidgetModel(
    /** Today's curve, 5-minute points, level 0..100. */
    val points: List<EnergyPoint>,
    /** 0..1 position of now along the day, or null if now is outside the plotted day. */
    val nowFraction: Float?,
    val currentPhase: EnergyPhase?,
    val nextPhase: EnergyPhase?,
    val nextPhaseAt: Instant?,
)

object WidgetStateMapper {
    val STALE_AFTER: Duration = Duration.ofHours(12)

    fun map(snapshot: AnalysisSnapshot?, now: Instant, zone: ZoneId): WidgetState {
        if (snapshot == null) return WidgetState.Empty
        val curve = snapshot.energyCurve.forDate(now.atZone(zone).toLocalDate())
        if (curve == null || curve.points.size < 2) return WidgetState.Empty

        val points = curve.points.sortedBy { it.at }
        val first = points.first().at
        val last = points.last().at
        val span = Duration.between(first, last).toMillis().toFloat()
        val nowFraction = if (now < first || now > last || span <= 0f) null else Duration.between(first, now).toMillis() / span

        val current = curve.segments.firstOrNull { it.interval.contains(now) }?.phase
        val next = curve.segments.filter { it.interval.start.isAfter(now) && it.phase != current }.minByOrNull { it.interval.start }
        val model = WidgetModel(points, nowFraction, current, next?.phase, next?.interval?.start)

        val age = Duration.between(snapshot.computedAt, now)
        return if (age > STALE_AFTER) WidgetState.Stale(model, age) else WidgetState.Fresh(model)
    }
}
