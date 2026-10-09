package app.drift.data.repo

import app.drift.data.db.DriftDatabase
import app.drift.data.db.toEdit
import app.drift.data.db.toRaw
import app.drift.data.merge.TimelineMerger
import app.drift.model.Interval
import app.drift.model.SleepEpisode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Reads raw and user rows and runs the pure [TimelineMerger] over them. */
@Singleton
class TimelineStore @Inject constructor(private val db: DriftDatabase) {

    /** Merged episodes overlapping [range]. Inputs are padded so trimming near the edges matches the full timeline. */
    fun observeMerged(range: Interval): Flow<List<SleepEpisode>> {
        val from = range.start.minus(PAD).toEpochMilli()
        val to = range.end.plus(PAD).toEpochMilli()
        return combine(db.sourceEpisodes().observeOverlapping(from, to), db.userEpisodes().observeOverlapping(from, to)) { raw, user ->
            TimelineMerger.merge(raw.map { it.toRaw() }, user.map { it.toEdit() }).filter { it.interval.overlaps(range) }
        }.distinctUntilChanged()
    }

    suspend fun merged(range: Interval): List<SleepEpisode> {
        val from = range.start.minus(PAD).toEpochMilli()
        val to = range.end.plus(PAD).toEpochMilli()
        val raw = db.sourceEpisodes().overlapping(from, to).map { it.toRaw() }
        val user = db.userEpisodes().all().filter { it.endMs > from && it.startMs < to }.map { it.toEdit() }
        return TimelineMerger.merge(raw, user).filter { it.interval.overlaps(range) }
    }

    fun observeRaw(range: Interval): Flow<List<SleepEpisode>> =
        db.sourceEpisodes().observeOverlapping(range.start.toEpochMilli(), range.end.toEpochMilli())
            .map { rows -> rows.map { it.toRaw().episode } }

    companion object {
        val PAD: Duration = Duration.ofHours(24)
        val ALL: Interval = Interval(Instant.EPOCH, Instant.parse("2200-01-01T00:00:00Z"))
    }
}
