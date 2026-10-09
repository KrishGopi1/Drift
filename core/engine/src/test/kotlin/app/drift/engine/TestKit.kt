package app.drift.engine

import app.drift.model.DataQuality
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.Signal
import app.drift.model.SleepEpisode
import app.drift.model.SleepStage
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.StageSpan
import app.drift.model.engine.EngineInput
import app.drift.model.engine.EngineState
import app.drift.model.engine.SignalWindow
import app.drift.model.engine.UserProfile
import java.time.LocalDateTime
import java.time.ZoneId

/** Small helpers for hand-written engine inputs. Times are local ISO strings like "2026-09-01T23:00". */
object Kit {
    val UTC: ZoneId = ZoneId.of("UTC")

    fun ep(
        id: String,
        start: String,
        end: String,
        zone: ZoneId = UTC,
        kind: EpisodeKind = EpisodeKind.MAIN,
        tier: QualityTier = QualityTier.WEARABLE_WRIST,
        flags: Set<QualityFlag> = emptySet(),
        stages: List<StageSpan>? = null,
        inBed: Interval? = null,
        edited: Boolean = false,
        source: SourceId = SourceId.HEALTH_CONNECT,
        completeness: Double = 1.0,
        detection: Double = 1.0,
    ): SleepEpisode = SleepEpisode(
        id = EpisodeId(id),
        start = LocalDateTime.parse(start).atZone(zone).toInstant(),
        end = LocalDateTime.parse(end).atZone(zone).toInstant(),
        zone = zone,
        kind = kind,
        stages = stages,
        inBed = inBed,
        origin = SourceRef(source, id, tier),
        quality = DataQuality(completeness, detection, flags),
        userEdited = edited,
    )

    fun stage(stage: SleepStage, start: String, end: String, zone: ZoneId = UTC) =
        StageSpan(stage, Interval(LocalDateTime.parse(start).atZone(zone).toInstant(), LocalDateTime.parse(end).atZone(zone).toInstant()))

    fun input(
        episodes: List<SleepEpisode>,
        now: String,
        zone: ZoneId = UTC,
        profile: UserProfile = Synth.profile(),
        annotations: List<DayAnnotation> = emptyList(),
        signals: List<Signal> = emptyList(),
        prior: EngineState? = null,
    ): EngineInput {
        val n = LocalDateTime.parse(now).atZone(zone).toInstant()
        return EngineInput(n, zone, episodes, SignalWindow(Interval(n, n), signals), profile, prior, annotations)
    }

    val engine = DefaultSleepEngine(failSafe = false)
}
