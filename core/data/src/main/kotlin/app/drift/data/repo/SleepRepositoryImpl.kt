package app.drift.data.repo

import app.drift.data.db.AnnotationEntity
import app.drift.data.db.DriftDatabase
import app.drift.data.db.toEdit
import app.drift.data.db.toEntity
import app.drift.data.merge.UserEdit
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.QualityFlag
import app.drift.model.SleepEpisode
import app.drift.model.SourceId
import app.drift.model.repository.SleepRepository
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepositoryImpl @Inject constructor(
    private val db: DriftDatabase,
    private val timeline: TimelineStore,
    private val clock: Clock,
) : SleepRepository {

    override fun observeEpisodes(range: Interval): Flow<List<SleepEpisode>> = timeline.observeMerged(range)

    override fun observeEpisode(id: EpisodeId): Flow<SleepEpisode?> =
        timeline.observeMerged(TimelineStore.ALL).map { list -> list.firstOrNull { it.id == id } }

    override fun observeSourceEpisodes(range: Interval): Flow<List<SleepEpisode>> = timeline.observeRaw(range)

    override fun observePendingConfirmation(): Flow<SleepEpisode?> {
        val now = clock.instant()
        return timeline.observeMerged(Interval(now.minus(PENDING_WINDOW), now.plus(Duration.ofDays(1)))).map { list ->
            list.filter {
                it.kind == EpisodeKind.MAIN && !it.userEdited && it.origin.sourceId == SourceId.PHONE &&
                    QualityFlag.USER_CONFIRMED !in it.quality.flags && !it.end.isAfter(now)
            }.maxByOrNull { it.end }
        }
    }

    override suspend fun saveUserEpisode(episode: SleepEpisode) {
        db.withTransaction {
            val old = findMerged(episode.id)
            val prior = db.userEpisodes().get(episode.id.value)?.toEdit()
            val from = minOf(episode.start, old?.start ?: episode.start)
            val to = maxOf(episode.end, old?.end ?: episode.end)
            val keys = overlappingRawKeys(Interval(from, to)) + (prior?.suppressedKeys ?: emptySet())
            db.userEpisodes().upsert(UserEdit(episode.copy(userEdited = true), deleted = false, suppressedKeys = keys).toEntity())
        }
    }

    override suspend fun confirmEpisode(id: EpisodeId) {
        db.withTransaction {
            val ep = findMerged(id) ?: return@withTransaction
            val prior = db.userEpisodes().get(id.value)?.toEdit()
            val confirmed = ep.copy(
                userEdited = true,
                quality = ep.quality.copy(flags = ep.quality.flags + QualityFlag.USER_CONFIRMED),
            )
            val keys = overlappingRawKeys(ep.interval) + (prior?.suppressedKeys ?: emptySet())
            db.userEpisodes().upsert(UserEdit(confirmed, deleted = false, suppressedKeys = keys).toEntity())
        }
    }

    override suspend fun deleteEpisode(id: EpisodeId) {
        db.withTransaction {
            val ep = findMerged(id) ?: return@withTransaction
            val prior = db.userEpisodes().get(id.value)?.toEdit()
            val keys = overlappingRawKeys(ep.interval) + (prior?.suppressedKeys ?: emptySet())
            if (keys.isEmpty()) {
                db.userEpisodes().delete(id.value)
            } else {
                db.userEpisodes().upsert(UserEdit(ep, deleted = true, suppressedKeys = keys).toEntity())
            }
        }
    }

    override fun observeAnnotations(from: LocalDate, to: LocalDate): Flow<List<DayAnnotation>> =
        db.annotations().observe(from.toEpochDay(), to.toEpochDay()).map { rows -> rows.map { it.toModel() } }

    override suspend fun setAnnotation(annotation: DayAnnotation) {
        db.annotations().upsert(
            AnnotationEntity(annotation.date.toEpochDay(), annotation.freeDay, annotation.alarmSet, annotation.alarmDismissedAt?.toString()),
        )
    }

    private suspend fun findMerged(id: EpisodeId): SleepEpisode? =
        timeline.merged(TimelineStore.ALL).firstOrNull { it.id == id }

    private suspend fun overlappingRawKeys(span: Interval): Set<String> =
        db.sourceEpisodes().overlapping(span.start.toEpochMilli(), span.end.toEpochMilli()).map { it.key }.toSet()

    private companion object { val PENDING_WINDOW: Duration = Duration.ofDays(2) }
}

internal fun AnnotationEntity.toModel() = DayAnnotation(
    LocalDate.ofEpochDay(epochDay), freeDay, alarmSet, alarmDismissedAt?.let(Instant::parse),
)
