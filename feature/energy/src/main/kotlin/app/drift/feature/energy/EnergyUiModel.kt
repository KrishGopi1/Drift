package app.drift.feature.energy

import app.drift.model.SleepEpisode
import app.drift.model.engine.DayCurve
import app.drift.model.engine.Recommendations
import app.drift.model.engine.Reentrainment
import app.drift.model.engine.SufficiencyTier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Something a user action on this screen failed to do. Shown as a dismissible line at the top. */
enum class EnergyActionError { CONFIRM_FAILED, SAVE_FAILED, CHECK_IN_FAILED }

/**
 * The Energy screen's Ready model. Everything on screen derives from these fields; nothing is formatted here.
 * [use24h] is null when the user follows the system clock setting (resolved in the UI).
 */
data class EnergyUiModel(
    val now: Instant,
    val zone: ZoneId,
    val use24h: Boolean?,
    val today: LocalDate,
    /** Yesterday, today and tomorrow as computed by the engine. */
    val days: List<DayCurve>,
    val calibrationCheckIns: Int,
    val tier: SufficiencyTier,
    val shiftWorkMode: Boolean,
    val reentrainment: Reentrainment?,
    val recommendations: Recommendations,
    /** Inferred night waiting for the morning "confirm your sleep" card. */
    val pending: SleepEpisode?,
    val checkInDue: Boolean,
    val actionError: EnergyActionError? = null,
) {
    val priorsOnly: Boolean get() = tier == SufficiencyTier.PRIORS_ONLY
    fun dayAt(offsetFromToday: Int): DayCurve? = days.firstOrNull { it.date == today.plusDays(offsetFromToday.toLong()) }
}
