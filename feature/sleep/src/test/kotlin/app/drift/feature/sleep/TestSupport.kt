package app.drift.feature.sleep

import app.drift.feature.sleep.di.EpisodeIdGenerator
import app.drift.feature.sleep.di.SleepTexts
import app.drift.model.DataQuality
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.SleepEpisode
import app.drift.model.SleepStage
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.StageSpan
import app.drift.testing.fixtures.FixtureContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object FakeTexts : SleepTexts {
    override fun sourceProblem(sourceName: String) = "$sourceName needs attention"
    override fun loadFailed() = "load failed"
    override fun nightUnavailable() = "night unavailable"
    override fun saveFailed() = "save failed"
}

class CountingIds : EpisodeIdGenerator {
    var n = 0
    override fun next(): String = "manual-${++n}"
}

object Eps {
    /** One main night that ends on [wakeDate] in [zone]. */
    fun night(
        id: String,
        wakeDate: LocalDate,
        zone: ZoneId,
        bed: LocalTime = LocalTime.of(23, 0),
        wake: LocalTime = LocalTime.of(7, 0),
        stages: Boolean = false,
        userEdited: Boolean = false,
        tier: QualityTier = QualityTier.WEARABLE_WRIST,
        sourceId: SourceId = SourceId.HEALTH_CONNECT,
        recordId: String = "rec-$id",
        flags: Set<QualityFlag> = emptySet(),
        kind: EpisodeKind = EpisodeKind.MAIN,
    ): SleepEpisode {
        val end = wakeDate.atTime(wake).atZone(zone).toInstant()
        val startDate = if (bed.isAfter(wake)) wakeDate.minusDays(1) else wakeDate
        val start = startDate.atTime(bed).atZone(zone).toInstant()
        val spans = if (stages) {
            val total = Duration.between(start, end).toMinutes()
            val order = listOf(SleepStage.LIGHT, SleepStage.DEEP, SleepStage.LIGHT, SleepStage.REM, SleepStage.AWAKE, SleepStage.LIGHT, SleepStage.REM)
            val chunk = total / order.size
            order.mapIndexed { i, s ->
                StageSpan(s, Interval(start.plus(Duration.ofMinutes(chunk * i)), start.plus(Duration.ofMinutes(if (i == order.lastIndex) total else chunk * (i + 1)))))
            }
        } else null
        return SleepEpisode(
            id = EpisodeId(id), start = start, end = end, zone = zone, kind = kind, stages = spans,
            origin = SourceRef(sourceId, recordId, tier, "com.example.band", "band"),
            quality = DataQuality(0.9, 1.0, flags), userEdited = userEdited,
        )
    }

    /** [days] consecutive nights ending on the context's today, skipping the offsets in [gaps]. */
    fun history(ctx: FixtureContext, days: Int, gaps: Set<Int> = emptySet()): List<SleepEpisode> =
        (0 until days).filter { it !in gaps }.map { back ->
            val d = ctx.today.minusDays(back.toLong())
            night("n-$d", d, ctx.zone, bed = LocalTime.of(23, (back * 7) % 50), wake = LocalTime.of(7, (back * 5) % 30), stages = back == 0, userEdited = back == 3)
        }
}
