package app.drift.data.repo

import androidx.room.withTransaction
import app.drift.data.codec.DriftJson
import app.drift.data.codec.EngineStateDto
import app.drift.data.codec.SnapshotCodec
import app.drift.data.codec.toDto
import app.drift.data.codec.toModel
import app.drift.data.db.DriftDatabase
import app.drift.data.db.EngineStateEntity
import app.drift.data.db.SnapshotEntity
import app.drift.data.db.toSignal
import app.drift.model.Interval
import app.drift.model.SleepEpisode
import app.drift.model.engine.AgeBand
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.EngineInput
import app.drift.model.engine.EngineState
import app.drift.model.engine.Goals
import app.drift.model.engine.Schedule
import app.drift.model.engine.SignalWindow
import app.drift.model.engine.SleepEngine
import app.drift.model.engine.UserProfile
import app.drift.model.repository.AnalysisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalysisRepositoryImpl @Inject constructor(
    private val db: DriftDatabase,
    private val timeline: TimelineStore,
    private val engine: SleepEngine,
    private val clock: Clock,
) : AnalysisRepository {

    override fun observeLatest(): Flow<AnalysisSnapshot?> = db.snapshots().observeLatest().map { it?.decode() }
    override fun observePrevious(): Flow<AnalysisSnapshot?> = db.snapshots().observePrevious().map { it?.decode() }

    private val previewLock = Mutex()
    private var previewBase: EngineInput? = null

    override suspend fun refresh(now: Instant): AnalysisSnapshot {
        previewLock.withLock { previewBase = null }
        val input = buildInput(now)
        val snapshot = withContext(Dispatchers.Default) { engine.analyze(input) }
        persist(snapshot)
        return snapshot
    }

    override suspend fun preview(edits: List<SleepEpisode>, now: Instant): AnalysisSnapshot {
        // Drags call this many times a second: reuse the stored input for a short while, then apply the edits.
        val base = previewLock.withLock {
            previewBase?.takeIf { Duration.between(it.now, now).abs() < PREVIEW_REUSE }
                ?: buildInput(now).also { previewBase = it }
        }
        val kept = base.episodes.filter { e -> edits.none { it.id == e.id || it.interval.overlaps(e.interval) } }
        val input = base.copy(episodes = (kept + edits).sortedBy { it.start })
        return withContext(Dispatchers.Default) { engine.analyze(input) }
    }

    internal suspend fun buildInput(now: Instant): EngineInput {
        val zone = clock.zone
        val from = now.minus(HISTORY)
        val range = Interval(from, now.plus(Duration.ofDays(1)))
        val episodes = timeline.merged(range)
        val signals = db.signals().window(from.toEpochMilli(), now.toEpochMilli()).map { it.toSignal() }
        val profile = db.profile().get()?.let { DriftJson.decodeFromString(app.drift.data.codec.ProfileDto.serializer(), it.payload).toModel() }
            ?: NEUTRAL_PROFILE
        val prior = db.engineState().get()?.let { DriftJson.decodeFromString(EngineStateDto.serializer(), it.payload).toModel() }
        val firstDay = from.atZone(zone).toLocalDate()
        val lastDay = now.atZone(zone).toLocalDate().plusDays(1)
        val annotations = db.annotations().all()
            .filter { it.epochDay in firstDay.toEpochDay()..lastDay.toEpochDay() }
            .map { it.toModel() }
        val checkIns = db.checkIns().since(from.toEpochMilli()).map { CheckInCodec.decode(it) }
        return EngineInput(
            now = now,
            zone = zone,
            episodes = episodes,
            signals = SignalWindow(Interval(from, now), signals),
            profile = profile,
            priorState = prior,
            annotations = annotations,
            checkIns = checkIns,
        )
    }

    private suspend fun persist(snapshot: AnalysisSnapshot) {
        db.withTransaction {
            val latest = db.snapshots().latest()
            val row = SnapshotEntity(
                id = if (latest != null && latest.inputHash == snapshot.inputHash && latest.engineVersion == snapshot.engineVersion.toString()) latest.id else 0,
                computedAtMs = snapshot.computedAt.toEpochMilli(),
                engineVersion = snapshot.engineVersion.toString(),
                inputHash = snapshot.inputHash,
                computedAt = snapshot.computedAt.toString(),
                payload = SnapshotCodec.encode(snapshot),
            )
            db.snapshots().upsert(row)
            db.snapshots().prune(KEEP_SNAPSHOTS)
            db.engineState().upsert(EngineStateEntity(1, DriftJson.encodeToString(EngineStateDto.serializer(), snapshot.state.toDto())))
        }
    }

    private fun SnapshotEntity.decode(): AnalysisSnapshot = SnapshotCodec.decode(payload)

    companion object {
        val HISTORY: Duration = Duration.ofDays(90)
        /** How long a preview reuses the input it built (the stored timeline cannot change much in this time). */
        val PREVIEW_REUSE: Duration = Duration.ofMinutes(2)
        const val KEEP_SNAPSHOTS = 30

        /** Used until onboarding saves a real profile, so the engine can still serve its priors-only output. */
        val NEUTRAL_PROFILE = UserProfile(
            ageBand = AgeBand.AGE_26_35,
            chronotype = null,
            schedule = Schedule(
                workdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                wakeTime = null, freeDayBedtime = null, freeDayWakeTime = null,
            ),
            goals = Goals(),
        )
    }
}
