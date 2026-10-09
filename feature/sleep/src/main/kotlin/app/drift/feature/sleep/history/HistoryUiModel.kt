package app.drift.feature.sleep.history

import app.drift.charts.TimingBar
import app.drift.charts.TimingRange
import app.drift.charts.timingBars
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Provenance
import app.drift.model.QualityTier
import app.drift.model.SleepEpisode
import app.drift.model.repository.SourceFix
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

enum class HistoryRange(val timing: TimingRange) {
    WEEK(TimingRange.WEEK),
    MONTH(TimingRange.MONTH),
    QUARTER(TimingRange.QUARTER),
}

data class HistorySummary(
    val averageSleep: Duration?,
    /** Minutes relative to local midnight of the wake date; negative = before midnight. */
    val averageOnsetMinutes: Int?,
    val averageWakeMinutes: Int?,
    val recorded: Int,
    val total: Int,
)

/** One day of the night list. [episodeId] is null for nights with no data. */
data class NightRow(
    val date: LocalDate,
    val episodeId: EpisodeId?,
    val start: Instant?,
    val end: Instant?,
    val zone: ZoneId?,
    val duration: Duration?,
    val userEdited: Boolean,
    val provenance: Provenance?,
) {
    val hasData: Boolean get() = episodeId != null
}

data class HistoryProblem(val fix: SourceFix, val message: String)

data class HistoryUiModel(
    val range: HistoryRange,
    val zone: ZoneId,
    val use24hSetting: Boolean?,
    val need: Duration?,
    val bars: List<TimingBar>,
    val summary: HistorySummary,
    /** Newest first. */
    val nights: List<NightRow>,
    val problem: HistoryProblem? = null,
)

/** The merged main episode that ended on [date] (longest wins), as the charts pick it. */
internal fun List<SleepEpisode>.mainEpisodeByWakeDate(): Map<LocalDate, SleepEpisode> =
    filter { it.kind == EpisodeKind.MAIN }
        .groupBy { it.end.atZone(it.zone).toLocalDate() }
        .mapValues { (_, list) -> list.maxBy { Duration.between(it.start, it.end) } }

/** Pure mapping from episodes to the History screen. Missing nights stay gaps; averages exclude them. */
fun buildHistoryModel(
    range: HistoryRange,
    episodes: List<SleepEpisode>,
    today: LocalDate,
    zone: ZoneId,
    need: Duration?,
    use24hSetting: Boolean?,
    problem: HistoryProblem? = null,
): HistoryUiModel {
    val bars = timingBars(episodes, today, range.timing)
    val byDate = episodes.mainEpisodeByWakeDate()
    val rows = bars.asReversed().map { bar ->
        val ep = byDate[bar.date]?.takeIf { bar.onsetMinutes != null }
        NightRow(
            date = bar.date,
            episodeId = ep?.id,
            start = ep?.start,
            end = ep?.end,
            zone = ep?.zone,
            duration = ep?.let { Duration.between(it.start, it.end) },
            userEdited = ep?.userEdited ?: false,
            provenance = ep?.let { provenanceOf(it) },
        )
    }
    val present = rows.filter { it.hasData }
    val onsets = bars.mapNotNull { it.onsetMinutes }
    val wakes = bars.mapNotNull { it.wakeMinutes }
    val summary = HistorySummary(
        averageSleep = present.mapNotNull { it.duration }.takeIf { it.isNotEmpty() }
            ?.let { list -> Duration.ofMinutes(list.map { it.toMinutes() }.average().roundToInt().toLong()) },
        averageOnsetMinutes = onsets.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        averageWakeMinutes = wakes.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        recorded = present.size,
        total = rows.size,
    )
    return HistoryUiModel(range, zone, use24hSetting, need, bars, summary, rows, problem)
}

/** User edits show as User-entered, phone-inferred nights as Estimated, everything recorded as Measured. */
internal fun provenanceOf(episode: SleepEpisode): Provenance = when {
    episode.userEdited -> Provenance.USER_ENTERED
    episode.origin.qualityTier == QualityTier.PHONE_INFERRED -> Provenance.ESTIMATED
    else -> Provenance.MEASURED
}
