package app.drift.feature.sleep.night

import app.drift.model.DataQuality
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.SleepEpisode
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.StageSpan
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class EditError { END_NOT_AFTER_START, TOO_SHORT, TOO_LONG, ENDS_IN_FUTURE }

/** Limits the editor enforces (docs/ux/night-detail.md: "under 20 h"). */
object EditLimits {
    val minDuration: Duration = Duration.ofMinutes(10)
    val maxDuration: Duration = Duration.ofHours(20)

    /** The time picker has no step of its own, so picked minutes snap to this grid. */
    const val STEP_MINUTES = 5
}

/** Rounds [minutes] after midnight to the editor's step, wrapping at 24 h. */
fun snapToStep(minutes: Int): Int {
    val step = EditLimits.STEP_MINUTES
    return ((Math.round(minutes / step.toDouble()).toInt() * step) % 1440 + 1440) % 1440
}

/**
 * Times of an episode being edited, as clock minutes in [zone]. The wake time sits on [wakeDate]; the start is the
 * latest moment before it with the chosen clock reading, so a 23:05 bedtime lands on the evening before and a 14:00
 * nap start on the same day.
 */
data class EpisodeDraft(
    val wakeDate: LocalDate,
    val zone: ZoneId,
    val startMinutes: Int,
    val endMinutes: Int,
) {
    val endLocal: LocalDateTime get() = wakeDate.atTime(endMinutes / 60, endMinutes % 60)
    val startLocal: LocalDateTime
        get() {
            val sameDay = wakeDate.atTime(startMinutes / 60, startMinutes % 60)
            return if (sameDay.isBefore(endLocal)) sameDay else sameDay.minusDays(1)
        }
    val start: Instant get() = startLocal.atZone(zone).toInstant()
    val end: Instant get() = endLocal.atZone(zone).toInstant()
    val duration: Duration get() = Duration.between(start, end)

    fun withStart(minutes: Int): EpisodeDraft = copy(startMinutes = snapToStep(minutes))
    fun withEnd(minutes: Int): EpisodeDraft = copy(endMinutes = snapToStep(minutes))

    /** Errors that block saving. [now] is only needed to reject nights that end in the future. */
    fun errors(now: Instant? = null): List<EditError> = buildList {
        val d = duration
        when {
            !end.isAfter(start) -> add(EditError.END_NOT_AFTER_START)
            d < EditLimits.minDuration -> add(EditError.TOO_SHORT)
            d > EditLimits.maxDuration -> add(EditError.TOO_LONG)
        }
        if (now != null && end.isAfter(now)) add(EditError.ENDS_IN_FUTURE)
    }

    companion object {
        fun of(episode: SleepEpisode): EpisodeDraft {
            val s = episode.start.atZone(episode.zone).toLocalTime()
            val e = episode.end.atZone(episode.zone).toLocalTime()
            return EpisodeDraft(
                wakeDate = episode.end.atZone(episode.zone).toLocalDate(),
                zone = episode.zone,
                startMinutes = s.hour * 60 + s.minute,
                endMinutes = e.hour * 60 + e.minute,
            )
        }

        /** Starting values of the add-a-night and add-a-nap forms. */
        fun blank(kind: EpisodeKind, wakeDate: LocalDate, zone: ZoneId): EpisodeDraft = when (kind) {
            EpisodeKind.MAIN -> EpisodeDraft(wakeDate, zone, startMinutes = 23 * 60, endMinutes = 7 * 60)
            EpisodeKind.NAP -> EpisodeDraft(wakeDate, zone, startMinutes = 13 * 60, endMinutes = 13 * 60 + 30)
        }
    }
}

/** State of the editor shown on night detail (edit) or the add screens. */
data class EditorUiState(
    val draft: EpisodeDraft,
    val errors: List<EditError> = emptyList(),
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val canSave: Boolean get() = errors.isEmpty() && !saving
}

/** Applies the user's edit to [original]: new times, clipped stages, and the night counts as confirmed. */
fun SleepEpisode.withDraft(draft: EpisodeDraft): SleepEpisode {
    val window = Interval(draft.start, draft.end)
    val clipped = stages?.mapNotNull { span ->
        span.interval.intersect(window)?.takeIf { !it.duration.isZero }?.let { StageSpan(span.stage, it) }
    }?.takeIf { it.isNotEmpty() }
    return copy(
        start = draft.start,
        end = draft.end,
        zone = draft.zone,
        stages = clipped,
        inBed = inBed?.intersect(window),
        userEdited = true,
        quality = quality.copy(flags = quality.flags - QualityFlag.IMPLAUSIBLE_DURATION + QualityFlag.USER_CONFIRMED),
    )
}

/** A brand-new manual episode (night or nap): USER source, ring-tier weight, edited by the user. */
fun newManualEpisode(id: String, draft: EpisodeDraft, kind: EpisodeKind): SleepEpisode = SleepEpisode(
    id = EpisodeId(id),
    start = draft.start,
    end = draft.end,
    zone = draft.zone,
    kind = kind,
    origin = SourceRef(SourceId.USER, recordId = id, qualityTier = QualityTier.WEARABLE_RING),
    quality = DataQuality(1.0, 1.0, setOf(QualityFlag.USER_CONFIRMED)),
    userEdited = true,
)
