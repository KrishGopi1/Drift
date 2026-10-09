package app.drift.feature.energy

import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.QualityTier
import app.drift.model.SleepEpisode
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.DataQuality
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.repository.AppSettings
import app.drift.model.ui.ScreenState
import app.drift.testing.fixtures.FixtureContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** A clock the test can move. */
class MutableClock(var now: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
}

/** Phone-inferred night that ended this morning: 23:40 yesterday to 07:05 today, local time. */
fun pendingEpisode(ctx: FixtureContext, id: String = "pending-1"): SleepEpisode {
    val start = ctx.today.minusDays(1).atTime(23, 40).atZone(ctx.zone).toInstant()
    val end = ctx.today.atTime(7, 5).atZone(ctx.zone).toInstant()
    return SleepEpisode(
        id = EpisodeId(id),
        start = start,
        end = end,
        zone = ctx.zone,
        kind = EpisodeKind.MAIN,
        origin = SourceRef(SourceId.PHONE, "rec-$id", QualityTier.PHONE_INFERRED),
        quality = DataQuality(1.0, 0.8),
    )
}

/** Runs the real mapper on a snapshot to get the Ready model screens render. */
fun readyModel(
    snapshot: AnalysisSnapshot,
    ctx: FixtureContext,
    pending: SleepEpisode? = null,
    checkInDue: Boolean = false,
    use24h: Boolean? = true,
    now: Instant = ctx.now,
): EnergyUiModel {
    val state = mapEnergyState(
        EnergyInputs(
            snapshot = snapshot,
            analysisFailed = false,
            refreshFailed = false,
            pending = pending,
            settings = AppSettings(use24HourClock = use24h),
            checkIns = emptyList(),
            now = now,
            zone = ctx.zone,
            actionError = null,
            dismissedEpisodes = emptySet(),
        ),
    )
    val model = (state as ScreenState.Ready).model
    return model.copy(checkInDue = checkInDue)
}
