package app.drift.feature.sleep.sleep

import app.drift.feature.sleep.common.MIN_NIGHTS
import app.drift.model.EpisodeId
import app.drift.model.Provenance
import app.drift.model.Quantity
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.DailyDebt
import app.drift.model.engine.DebtBand
import app.drift.model.engine.TradeOff
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Ready model of the Sleep screen (plan 6.2). Built from a snapshot by [toSleepUiModel]; holds no formatted text. */
data class SleepUiModel(
    val zone: ZoneId,
    val use24hSetting: Boolean?,
    val debt: DebtSection,
    /** The 14-night chart input, oldest first. */
    val days: List<DailyDebt>,
    /** The same nights, newest first, for the "which nights built or repaid debt" list. */
    val contributions: List<ContributionRow>,
    val need: NeedSection,
    val regularity: RegularitySection,
    val tonight: TonightSection,
)

sealed interface DebtSection {
    data class Available(val debt: Quantity<Duration>, val band: DebtBand?) : DebtSection

    /** Fewer than [MIN_NIGHTS] nights of history: the panel with a counter replaces the hero. */
    data class TooFewNights(val nightsNeeded: Int) : DebtSection

    /** Debt is null after 3+ missing nights in a row. [missingNights] counts the nights without recorded sleep. */
    data class Unavailable(val missingNights: Int) : DebtSection
}

enum class ContributionKind { BUILT, REPAID, EVEN, NO_DATA }

data class ContributionRow(
    val date: LocalDate,
    val kind: ContributionKind,
    val sleep: Duration?,
    val sleepProvenance: Provenance?,
    val need: Duration,
    /** Positive = added to debt, negative = repaid. */
    val contribution: Duration,
    val imputed: Boolean,
    /** The merged episode of this night, when one exists (opens night detail). */
    val episodeId: EpisodeId?,
)

data class NeedSection(
    val need: Quantity<Duration>,
    val personalized: Boolean,
    val informativeNights: Int,
    val nightsUntilPersonal: Int,
)

data class RegularitySection(
    val available: Boolean,
    val nightsNeeded: Int,
    val sri7: Quantity<Double>?,
    val sri14: Quantity<Double>?,
    val trend: List<Pair<LocalDate, Double>>,
    val onsetSpread: Quantity<Duration>?,
    val wakeSpread: Quantity<Duration>?,
    val midpointSpread: Quantity<Duration>?,
) {
    /** The trend chart appears under the numbers once there are 7+ points (docs/ux/sleep.md). */
    val showTrend: Boolean get() = trend.size >= 7
}

data class TonightSection(
    val bedtime: Quantity<Instant>,
    val wake: Quantity<Instant>,
    val opportunity: Quantity<Duration>,
    val tradeOff: TradeOff?,
)

/** How the debt bands are defined (plan 4.3, shown to the user as the basis of the bands). */
object DebtBandBasis {
    val lowBelow: Duration = Duration.ofHours(5)
    val highAbove: Duration = Duration.ofHours(10)
    const val WINDOW_NIGHTS = 14
}

/**
 * Pure mapping from the engine snapshot. [nightIds] maps a sleep day (wake date) to its merged main episode.
 */
fun AnalysisSnapshot.toSleepUiModel(
    zone: ZoneId,
    use24hSetting: Boolean?,
    nightIds: Map<LocalDate, EpisodeId> = emptyMap(),
): SleepUiModel {
    val history = dataSufficiency.daysOfHistory
    val debtSection: DebtSection = when {
        history < MIN_NIGHTS -> DebtSection.TooFewNights((MIN_NIGHTS - history).coerceAtLeast(1))
        sleepDebt == null || !dataSufficiency.debtAvailable -> DebtSection.Unavailable(debtTrend.count { it.sleep == null })
        else -> DebtSection.Available(sleepDebt!!, debtBand)
    }
    return SleepUiModel(
        zone = zone,
        use24hSetting = use24hSetting,
        debt = debtSection,
        days = debtTrend,
        contributions = debtTrend.asReversed().map { it.toRow(nightIds[it.date]) },
        need = NeedSection(
            need = sleepNeed,
            personalized = dataSufficiency.nightsUntilPersonalNeed == 0,
            informativeNights = dataSufficiency.informativeNights,
            nightsUntilPersonal = dataSufficiency.nightsUntilPersonalNeed,
        ),
        regularity = RegularitySection(
            available = dataSufficiency.regularityAvailable,
            nightsNeeded = (MIN_NIGHTS - history).coerceAtLeast(1),
            sri7 = regularity.sri7,
            sri14 = regularity.sri14,
            trend = regularity.trend,
            onsetSpread = regularity.onsetSpread,
            wakeSpread = regularity.wakeSpread,
            midpointSpread = regularity.midpointSpread,
        ),
        tonight = TonightSection(
            bedtime = recommendations.bedtime,
            wake = recommendations.wakeTime,
            opportunity = recommendations.sleepOpportunity,
            tradeOff = recommendations.tradeOff,
        ),
    )
}

internal fun DailyDebt.toRow(episodeId: EpisodeId?): ContributionRow = ContributionRow(
    date = date,
    kind = when {
        sleep == null -> ContributionKind.NO_DATA
        contribution > Duration.ZERO -> ContributionKind.BUILT
        contribution < Duration.ZERO -> ContributionKind.REPAID
        else -> ContributionKind.EVEN
    },
    sleep = sleep?.value,
    sleepProvenance = sleep?.provenance,
    need = need,
    contribution = contribution,
    imputed = imputed,
    episodeId = episodeId,
)
