package app.drift.feature.sleep.bodyclock

import app.drift.feature.sleep.common.MIN_NIGHTS
import app.drift.feature.sleep.sleep.RegularitySection
import app.drift.model.Interval
import app.drift.model.Quantity
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.Chronotype
import app.drift.model.engine.CircadianEstimate
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong

sealed interface ChronotypeSection {
    data class Available(val chronotype: Chronotype) : ChronotypeSection
    data class NotEnoughFreeNights(val nightsNeeded: Int) : ChronotypeSection
}

sealed interface JetlagSection {
    data class Available(val socialJetlag: Quantity<Duration>) : JetlagSection
    data class NotEnoughFreeNights(val nightsNeeded: Int) : JetlagSection
}

data class BodyClockUiModel(
    val zone: ZoneId,
    val use24hSetting: Boolean?,
    val now: Instant,
    val circadian: CircadianEstimate,
    /** Tonight's recommended bedtime to wake time, drawn on the dial. */
    val plannedSleep: Interval?,
    val dlmo: Quantity<Double>?,
    /** Half the width of the DLMO band ("give or take"). */
    val dlmoHalfBand: Duration?,
    val chronotype: ChronotypeSection,
    val socialJetlag: JetlagSection,
    val regularity: RegularitySection,
)

/** Free (alarm-less) nights before the chronotype leaves the questionnaire prior behind (plan 4.4). */
const val FREE_NIGHTS_NEEDED = 4

fun AnalysisSnapshot.toBodyClockUiModel(zone: ZoneId, use24hSetting: Boolean?, now: Instant): BodyClockUiModel {
    val history = dataSufficiency.daysOfHistory
    val dlmo = circadian.dlmoMinutes
    val bed = recommendations.bedtime.value
    val wake = recommendations.wakeTime.value
    val freeUsed = regularity.chronotype?.freeNightsUsed ?: 0
    val missingFree = (FREE_NIGHTS_NEEDED - freeUsed).coerceAtLeast(1)
    return BodyClockUiModel(
        zone = zone,
        use24hSetting = use24hSetting,
        now = now,
        circadian = circadian,
        plannedSleep = if (!wake.isBefore(bed)) Interval(bed, wake) else null,
        dlmo = dlmo,
        dlmoHalfBand = dlmo?.confidence?.let { Duration.ofMinutes(((it.upper - it.lower) / 2).roundToLong()) },
        chronotype = regularity.chronotype?.let { ChronotypeSection.Available(it) } ?: ChronotypeSection.NotEnoughFreeNights(missingFree),
        socialJetlag = regularity.socialJetlag?.let { JetlagSection.Available(it) } ?: JetlagSection.NotEnoughFreeNights(missingFree),
        regularity = RegularitySection(
            available = dataSufficiency.regularityAvailable && regularity.sri7 != null,
            nightsNeeded = (MIN_NIGHTS - history).coerceAtLeast(1),
            sri7 = regularity.sri7,
            sri14 = regularity.sri14,
            trend = regularity.trend,
            onsetSpread = regularity.onsetSpread,
            wakeSpread = regularity.wakeSpread,
            midpointSpread = regularity.midpointSpread,
        ),
    )
}
