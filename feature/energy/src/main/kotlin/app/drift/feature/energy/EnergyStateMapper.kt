package app.drift.feature.energy

import app.drift.model.CheckIn
import app.drift.model.EpisodeId
import app.drift.model.SleepEpisode
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.SufficiencyTier
import app.drift.model.repository.AppSettings
import app.drift.model.ui.InsufficientReason
import app.drift.model.ui.ScreenState
import java.time.Instant
import java.time.ZoneId

/** Everything the mapper needs, gathered by the view model. */
internal data class EnergyInputs(
    val snapshot: AnalysisSnapshot?,
    /** The snapshot flow itself failed. */
    val analysisFailed: Boolean,
    /** The refresh on open failed. */
    val refreshFailed: Boolean,
    val pending: SleepEpisode?,
    val settings: AppSettings,
    val checkIns: List<CheckIn>,
    val now: Instant,
    val zone: ZoneId,
    val actionError: EnergyActionError?,
    val dismissedEpisodes: Set<EpisodeId>,
    val episodes: List<SleepEpisode> = emptyList(),
    /** A preview run for an unsaved sleep edit; replaces [snapshot]'s curve and potential while set. */
    val preview: AnalysisSnapshot? = null,
)

/**
 * Maps repository data to the C5 screen state.
 *
 * - No snapshot yet: Loading, or Error when loading failed (the UI supplies the message text).
 * - A snapshot always carries a curve (priors are enough), so every sufficiency tier is Ready, with the
 *   prior-only banner driven by [EnergyUiModel.priorsOnly].
 * - A snapshot with no curve days is the only case where the curve itself is missing: FirstUse before any
 *   history, Insufficient otherwise (debt availability picks the reason).
 */
internal fun mapEnergyState(input: EnergyInputs): ScreenState<EnergyUiModel> {
    val snapshot = input.preview ?: input.snapshot
        ?: return if (input.analysisFailed || input.refreshFailed) ScreenState.Error(message = "", retryable = true) else ScreenState.Loading
    val sufficiency = snapshot.dataSufficiency
    if (snapshot.energyCurve.days.isEmpty()) {
        return if (sufficiency.tier == SufficiencyTier.PRIORS_ONLY) {
            ScreenState.FirstUse(stepsRemaining = 1)
        } else {
            val reason = if (!sufficiency.debtAvailable && sufficiency.daysOfHistory > 0) {
                InsufficientReason.TOO_MANY_MISSING_NIGHTS
            } else {
                InsufficientReason.TOO_FEW_NIGHTS
            }
            ScreenState.Insufficient(reason, daysNeeded = sufficiency.nightsUntilPersonalNeed.coerceAtLeast(1))
        }
    }
    val today = input.now.atZone(input.zone).toLocalDate()
    val todayCurve = snapshot.energyCurve.forDate(today)
    val phase = currentSegment(todayCurve, input.now)?.phase
    return ScreenState.Ready(
        EnergyUiModel(
            now = input.now,
            zone = input.zone,
            use24h = input.settings.use24HourClock,
            today = today,
            days = snapshot.energyCurve.days,
            calibrationCheckIns = snapshot.energyCurve.calibrationCheckIns,
            tier = sufficiency.tier,
            shiftWorkMode = snapshot.circadian.shiftWorkMode,
            reentrainment = snapshot.circadian.reentrainment,
            recommendations = snapshot.recommendations,
            pending = input.pending?.takeIf { it.id !in input.dismissedEpisodes },
            checkInDue = isCheckInDue(input.checkIns, input.now, phase),
            actionError = input.actionError,
            potential = snapshot.energyPotential,
            episodes = input.episodes,
            melatoninWindow = snapshot.circadian.melatoninWindow,
            previewing = input.preview != null,
        ),
    )
}
