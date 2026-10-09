package app.drift.model

import java.time.Duration
import java.time.Instant

@JvmInline value class SourceId(val value: String) : Comparable<SourceId> {
    override fun compareTo(other: SourceId): Int = value.compareTo(other.value)

    companion object {
        val HEALTH_CONNECT = SourceId("healthconnect")
        val SLEEP_AS_ANDROID = SourceId("sleepasandroid")
        val PHONE = SourceId("phone")
        val USER = SourceId("user")
    }
}

@JvmInline value class EpisodeId(val value: String)

/** Opaque per-adapter sync position (e.g. a Health Connect change token or a last-seen timestamp). */
@JvmInline value class Checkpoint(val token: String)

/** Half-open time interval [start, end). */
data class Interval(val start: Instant, val end: Instant) : Comparable<Interval> {
    init { require(!end.isBefore(start)) { "Interval end must not be before start" } }
    val duration: Duration get() = Duration.between(start, end)
    fun overlaps(other: Interval): Boolean = start < other.end && other.start < end
    fun contains(t: Instant): Boolean = !t.isBefore(start) && t.isBefore(end)
    fun intersect(other: Interval): Interval? =
        if (!overlaps(other)) null else Interval(maxOf(start, other.start), minOf(end, other.end))
    override fun compareTo(other: Interval): Int = compareValuesBy(this, other, { it.start }, { it.end })
}
