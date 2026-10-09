package app.drift.feature.sleep.progress

import app.drift.model.CheckIn
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.SleepEpisode
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.CatchUp
import app.drift.model.engine.DailyDebt
import app.drift.model.engine.EnergyLabel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ProgressTab { TIMES, DEBT, QUALITY }

/** One sleep day (named by its wake date) with every episode that ended on it. */
data class ProgressNight(
    val date: LocalDate,
    val episodes: List<SleepEpisode>,
) {
    val main: SleepEpisode? get() = episodes.filter { it.kind == EpisodeKind.MAIN }.maxByOrNull { Duration.between(it.start, it.end) }
    val asleep: Duration? get() = if (episodes.isEmpty()) null else episodes.fold(Duration.ZERO) { acc, e -> acc + Duration.between(e.start, e.end) }

    /** Minutes after 18:00 on the evening before [date] for each episode, for the sleep-times chart. */
    fun spans(zone: ZoneId): List<Pair<Int, Int>> {
        val origin = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        return episodes.sortedBy { it.start }.map {
            Duration.between(origin, it.start).toMinutes().toInt() to Duration.between(origin, it.end).toMinutes().toInt()
        }
    }
}

/** Ready model of the Progress tab: the last 14 sleep days of times, debt and self-rated quality. */
data class ProgressUiModel(
    val zone: ZoneId,
    val use24hSetting: Boolean?,
    val today: LocalDate,
    /** Oldest first, one entry per day, ending today. */
    val nights: List<ProgressNight>,
    /** The engine's debt rows for the same window, oldest first (empty until debt is available). */
    val debt: List<DailyDebt>,
    val catchUp: CatchUp?,
    val potentialLabel: EnergyLabel?,
    /** Start of tonight's melatonin window, drawn as the bedtime target on every night. */
    val targetBedtime: Instant?,
    /** Latest self-rating per night. */
    val ratings: Map<LocalDate, Int>,
) {
    val windowStart: LocalDate get() = nights.first().date
    val windowEnd: LocalDate get() = nights.last().date

    /** Last night still waiting for a quality rating (shows the rating prompt). */
    val unratedLastNight: LocalDate? get() = nights.lastOrNull { it.main != null }?.date?.takeIf { it !in ratings }

    fun mainId(date: LocalDate): EpisodeId? = nights.firstOrNull { it.date == date }?.main?.id
}

const val PROGRESS_NIGHTS = 14

/** Pure mapping from repository data. Episodes belong to the day they end on. */
fun buildProgressModel(
    today: LocalDate,
    zone: ZoneId,
    episodes: List<SleepEpisode>,
    snapshot: AnalysisSnapshot?,
    checkIns: List<CheckIn>,
    use24hSetting: Boolean?,
): ProgressUiModel {
    val byDay = episodes.groupBy { it.end.atZone(zone).toLocalDate() }
    val nights = (PROGRESS_NIGHTS - 1 downTo 0).map { k ->
        val d = today.minusDays(k.toLong())
        ProgressNight(d, byDay[d].orEmpty())
    }
    val ratings = checkIns.filterIsInstance<CheckIn.SleepRating>()
        .sortedBy { it.at }
        .associate { it.night to it.rating }
    return ProgressUiModel(
        zone = zone,
        use24hSetting = use24hSetting,
        today = today,
        nights = nights,
        debt = snapshot?.debtTrend.orEmpty(),
        catchUp = snapshot?.catchUp,
        potentialLabel = snapshot?.energyPotential?.label,
        targetBedtime = snapshot?.circadian?.melatoninWindow?.start,
        ratings = ratings,
    )
}
