package app.drift.data.merge

import app.drift.model.EpisodeId
import app.drift.model.Interval
import app.drift.model.QualityFlag
import app.drift.model.SleepEpisode
import app.drift.model.StageSpan
import app.drift.model.SourceRef
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/** A per-source episode as stored, with the time it was last ingested (the "recency" used for ties). */
data class RawEpisode(val episode: SleepEpisode, val ingestedAt: Instant) {
    val key: String get() = episodeKey(episode.origin)
}

/**
 * A user edit (or manual entry). [deleted] marks a tombstone: the span is blanked and nothing is emitted.
 * [suppressedKeys] are raw episodes this edit replaces, even if the edit moved out of their span.
 */
data class UserEdit(
    val episode: SleepEpisode,
    val deleted: Boolean = false,
    val suppressedKeys: Set<String> = emptySet(),
)

fun episodeKey(origin: SourceRef): String = "${origin.sourceId.value}:${origin.recordId}"

/**
 * Pure merge of per-source episodes into the canonical timeline (plan section 3 / 4.1).
 *
 * Rules, in order:
 * 1. User edits always win. Any raw episode overlapping a user edit or tombstone (or named in its
 *    suppressed keys) is dropped whole, because the edit is the user's version of that night.
 * 2. Remaining raw episodes are ranked by quality tier, then recency (ingest time, then end time), then key.
 *    A lower-ranked episode keeps only the parts of its span not already covered by a higher-ranked
 *    one; parts shorter than [minFragment] are discarded. Hence the same night reported by two sources
 *    never yields two episodes.
 * 3. A winner that overrode a lower-tier source with materially different bounds is flagged SOURCE_CONFLICT.
 */
object TimelineMerger {
    val DEFAULT_MIN_FRAGMENT: Duration = Duration.ofMinutes(90)
    private val CONFLICT_BOUNDS_DELTA: Duration = Duration.ofMinutes(15)

    fun merge(
        raw: List<RawEpisode>,
        userEdits: List<UserEdit> = emptyList(),
        minFragment: Duration = DEFAULT_MIN_FRAGMENT,
    ): List<SleepEpisode> {
        val userIntervals = userEdits.map { it.episode.interval }
        val suppressed = userEdits.flatMapTo(HashSet()) { it.suppressedKeys }
        val candidates = raw
            .filter { it.key !in suppressed && userIntervals.none { u -> u.overlaps(it.episode.interval) } }
            .sortedWith(
                compareByDescending<RawEpisode> { it.episode.origin.qualityTier }
                    .thenByDescending { it.ingestedAt }
                    .thenByDescending { it.episode.end }
                    .thenBy { it.key },
            )

        val accepted = ArrayList<Piece>()
        for (cand in candidates) {
            val whole = cand.episode.interval
            val covering = accepted.filter { it.interval.overlaps(whole) }
            if (covering.isEmpty()) {
                accepted += Piece(cand, whole, true, trimmed = false)
                continue
            }
            val remainders = subtract(whole, covering.map { it.interval })
                .filter { it.duration >= minFragment }
            for (c in covering) {
                if (c.overridden(cand)) c.conflict = true
            }
            for (r in remainders) accepted += Piece(cand, r, r == whole, trimmed = r != whole)
        }

        val out = ArrayList<SleepEpisode>(accepted.size + userEdits.size)
        accepted.mapTo(out) { it.toEpisode() }
        userEdits.filterNot { it.deleted }.mapTo(out) { it.episode.copy(userEdited = true) }
        return out.sortedWith(compareBy({ it.start }, { it.end }, { it.id.value }))
    }

    private class Piece(val source: RawEpisode, val interval: Interval, val whole: Boolean, val trimmed: Boolean) {
        var conflict = false

        fun overridden(loser: RawEpisode): Boolean {
            val a = source.episode
            val b = loser.episode
            if (b.origin.qualityTier >= a.origin.qualityTier) return false
            return Duration.between(a.start, b.start).abs() > CONFLICT_BOUNDS_DELTA ||
                Duration.between(a.end, b.end).abs() > CONFLICT_BOUNDS_DELTA
        }

        fun toEpisode(): SleepEpisode {
            val ep = source.episode
            val quality = if (conflict) ep.quality.copy(flags = ep.quality.flags + QualityFlag.SOURCE_CONFLICT) else ep.quality
            val id = if (!trimmed) canonicalId(source.key) else canonicalId("${source.key}@${interval.start.toEpochMilli()}")
            return if (!trimmed) ep.copy(id = id, quality = quality) else ep.copy(
                id = id,
                start = interval.start,
                end = interval.end,
                stages = ep.stages?.let { clip(it, interval) },
                inBed = ep.inBed?.intersect(interval),
                quality = quality,
            )
        }
    }

    private fun clip(stages: List<StageSpan>, window: Interval): List<StageSpan> =
        stages.mapNotNull { s -> s.interval.intersect(window)?.takeIf { it.duration > Duration.ZERO }?.let { StageSpan(s.stage, it) } }

    /** [whole] minus the union of [cuts]; result is ordered and non-overlapping. */
    internal fun subtract(whole: Interval, cuts: List<Interval>): List<Interval> {
        var pieces = listOf(whole)
        for (cut in cuts.sortedBy { it.start }) {
            pieces = pieces.flatMap { p ->
                if (!p.overlaps(cut)) listOf(p) else buildList {
                    if (p.start < cut.start) add(Interval(p.start, cut.start))
                    if (cut.end < p.end) add(Interval(cut.end, p.end))
                }
            }
        }
        return pieces
    }

    /** Stable id: a hash of the winning record's source key, so it survives re-syncs. */
    fun canonicalId(key: String): EpisodeId {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return EpisodeId("ep-" + digest.take(8).joinToString("") { "%02x".format(it) })
    }
}
