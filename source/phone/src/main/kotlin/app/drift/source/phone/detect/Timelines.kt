package app.drift.source.phone.detect

import app.drift.model.Interval
import app.drift.source.phone.ChargeChange
import app.drift.source.phone.ScreenChange
import java.time.Instant

/** Screen state over time reconstructed from usage events. */
internal class ScreenTimeline(changes: List<ScreenChange>) {
    private val events = changes.sortedBy { it.at }

    val changes: List<ScreenChange> get() = events

    /** Intervals during which the screen was interactive, clipped to [from]..[to]. */
    fun onIntervals(from: Instant, to: Instant): List<Interval> {
        var on = false
        for (e in events) if (e.at <= from) on = e.interactive else break
        val out = mutableListOf<Interval>()
        var since: Instant = from
        for (e in events) {
            if (e.at <= from || e.at >= to) continue
            if (e.interactive && !on) {
                on = true; since = e.at
            } else if (!e.interactive && on) {
                on = false; out += Interval(since, e.at)
            }
        }
        if (on) out += Interval(since, to)
        return out
    }

    fun lastScreenOffIn(from: Instant, to: Instant): Instant? =
        events.lastOrNull { !it.interactive && it.at >= from && it.at <= to }?.at

    fun firstScreenOnIn(from: Instant, to: Instant): Instant? =
        events.firstOrNull { it.interactive && it.at >= from && it.at <= to }?.at
}

/** Plugged state over time; null before the first known change. */
internal class ChargeTimeline(changes: List<ChargeChange>) {
    private val events = changes.sortedBy { it.at }

    fun pluggedAt(t: Instant): Boolean? {
        var state: Boolean? = null
        for (e in events) if (e.at <= t) state = e.plugged else break
        return state
    }
}
