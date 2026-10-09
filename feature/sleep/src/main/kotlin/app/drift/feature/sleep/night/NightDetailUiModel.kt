package app.drift.feature.sleep.night

import app.drift.model.DayAnnotation
import app.drift.model.Provenance
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.SleepEpisode
import app.drift.model.SleepStage
import java.time.Duration
import java.time.LocalDate
import kotlin.math.roundToInt

enum class CompletenessWord { COMPLETE, SOME_GAPS, MANY_GAPS }
enum class DetectionWord { RECORDED, BEST_GUESS }

data class StageTotal(val stage: SleepStage, val duration: Duration, val percent: Int)

/** A per-source episode competing for a night (plan 6.2 "conflicting sources"). */
data class SourceOption(val episode: SleepEpisode, val used: Boolean)

data class NightDetailUiModel(
    val episode: SleepEpisode,
    val use24hSetting: Boolean?,
    /** The sleep day: the date of the wake time in the episode's zone. */
    val sleepDay: LocalDate,
    val asleep: Duration,
    val provenance: Provenance,
    /** Null when the source recorded no stages. */
    val stages: List<StageTotal>?,
    val completeness: CompletenessWord,
    val detection: DetectionWord,
    val flags: List<QualityFlag>,
    /** Non-empty only when the SOURCE_CONFLICT flag is set. */
    val conflicts: List<SourceOption>,
    val freeDay: Boolean,
)

internal fun completenessWord(completeness: Double): CompletenessWord = when {
    completeness >= 0.95 -> CompletenessWord.COMPLETE
    completeness >= 0.75 -> CompletenessWord.SOME_GAPS
    else -> CompletenessWord.MANY_GAPS
}

internal fun detectionWord(episode: SleepEpisode): DetectionWord =
    if (episode.origin.qualityTier == QualityTier.PHONE_INFERRED || episode.quality.detectionConfidence < 0.95) {
        DetectionWord.BEST_GUESS
    } else {
        DetectionWord.RECORDED
    }

/** Time asleep: light, deep and REM when stages exist, otherwise the whole episode. */
internal fun asleepDuration(episode: SleepEpisode): Duration {
    val staged = episode.stages
        ?.filter { it.stage == SleepStage.LIGHT || it.stage == SleepStage.DEEP || it.stage == SleepStage.REM }
        ?.fold(Duration.ZERO) { acc, s -> acc.plus(s.interval.duration) }
    return if (staged != null && !staged.isZero) staged else Duration.between(episode.start, episode.end)
}

/** Minutes and share per stage (awake, REM, light, deep), or null when the source has no stages. */
internal fun stageTotals(episode: SleepEpisode): List<StageTotal>? {
    val spans = episode.stages?.takeIf { it.isNotEmpty() } ?: return null
    val order = listOf(SleepStage.AWAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)
    val totals = order.associateWith { stage ->
        spans.filter { it.stage == stage }.fold(Duration.ZERO) { acc, s -> acc.plus(s.interval.duration) }
    }
    val all = totals.values.fold(Duration.ZERO) { a, b -> a.plus(b) }
    if (all.isZero) return null
    return order.map { StageTotal(it, totals.getValue(it), (totals.getValue(it).seconds * 100.0 / all.seconds).roundToInt()) }
}

fun buildNightDetail(
    episode: SleepEpisode,
    sourceEpisodes: List<SleepEpisode>,
    annotation: DayAnnotation?,
    use24hSetting: Boolean?,
): NightDetailUiModel {
    val conflicted = QualityFlag.SOURCE_CONFLICT in episode.quality.flags
    val conflicts = if (conflicted) {
        sourceEpisodes
            .filter { it.interval.overlaps(episode.interval) }
            .sortedWith(compareByDescending<SleepEpisode> { it.origin.qualityTier }.thenBy { it.start })
            .map { SourceOption(it, used = it.origin.sourceId == episode.origin.sourceId && it.origin.recordId == episode.origin.recordId) }
    } else {
        emptyList()
    }
    return NightDetailUiModel(
        episode = episode,
        use24hSetting = use24hSetting,
        sleepDay = episode.end.atZone(episode.zone).toLocalDate(),
        asleep = asleepDuration(episode),
        provenance = when {
            episode.userEdited -> Provenance.USER_ENTERED
            episode.origin.qualityTier == QualityTier.PHONE_INFERRED -> Provenance.ESTIMATED
            else -> Provenance.MEASURED
        },
        stages = stageTotals(episode),
        completeness = completenessWord(episode.quality.completeness),
        detection = detectionWord(episode),
        flags = episode.quality.flags.sorted(),
        conflicts = conflicts,
        freeDay = annotation?.freeDay == true,
    )
}

/** Takes the times and stages of a competing source as the user's choice for this night. */
fun SleepEpisode.adoptSource(other: SleepEpisode): SleepEpisode = copy(
    start = other.start,
    end = other.end,
    zone = other.zone,
    stages = other.stages,
    inBed = other.inBed,
    origin = other.origin,
    quality = other.quality.copy(flags = other.quality.flags - QualityFlag.SOURCE_CONFLICT + QualityFlag.USER_CONFIRMED),
    userEdited = true,
)
