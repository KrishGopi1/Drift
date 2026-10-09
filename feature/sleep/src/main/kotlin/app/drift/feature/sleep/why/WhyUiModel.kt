package app.drift.feature.sleep.why

import app.drift.model.Provenance
import app.drift.model.Quantity
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.CauseKind
import app.drift.model.engine.Explanation
import app.drift.model.engine.MetricKey
import java.time.Duration

enum class WhyVariant {
    /** First day, or no earlier snapshot to compare with: the starting estimate. */
    FIRST_DAY,

    /** A snapshot exists but nothing changed for this metric. */
    NO_CHANGE,

    CHANGES,
}

data class WhyUiModel(
    val metric: MetricKey,
    val variant: WhyVariant,
    val explanations: List<Explanation>,
    val provenance: Provenance?,
    /** Set for duration metrics so the sheet can draw the confidence band. */
    val duration: Quantity<Duration>?,
)

/** Deltas of these causes are minutes; the others are counts or unitless and are not shown as a duration. */
fun CauseKind.deltaIsMinutes(): Boolean = when (this) {
    CauseKind.NIGHT_ENTERED_WINDOW, CauseKind.NIGHT_LEFT_WINDOW, CauseKind.USER_EDIT,
    CauseKind.NEED_REESTIMATED, CauseKind.CIRCADIAN_SHIFT -> true
    CauseKind.SOURCE_ADDED, CauseKind.CHECK_IN_CALIBRATION, CauseKind.FIRST_RUN -> false
}

/** Pure mapping from the latest and previous snapshots to the sheet. */
fun buildWhyModel(metric: MetricKey, latest: AnalysisSnapshot?, previous: AnalysisSnapshot?): WhyUiModel {
    val explanations = latest?.explanations?.filter { it.metric == metric }.orEmpty()
    val firstRun = explanations.any { e -> e.causes.any { it.kind == CauseKind.FIRST_RUN } }
    val variant = when {
        latest == null -> WhyVariant.NO_CHANGE
        previous == null || firstRun -> WhyVariant.FIRST_DAY
        explanations.isEmpty() -> WhyVariant.NO_CHANGE
        else -> WhyVariant.CHANGES
    }
    val duration: Quantity<Duration>? = when (metric) {
        MetricKey.SLEEP_NEED -> latest?.sleepNeed
        MetricKey.SLEEP_DEBT -> latest?.sleepDebt
        MetricKey.RECOMMENDATION -> latest?.recommendations?.sleepOpportunity
        else -> null
    }
    val provenance: Provenance? = duration?.provenance ?: when (metric) {
        MetricKey.REGULARITY -> latest?.regularity?.sri7?.provenance
        MetricKey.CHRONOTYPE -> latest?.regularity?.chronotype?.msfscMinutes?.provenance
        MetricKey.CIRCADIAN -> latest?.circadian?.dlmoMinutes?.provenance
        MetricKey.ENERGY -> latest?.energyCurve?.days?.firstOrNull()?.provenance
        else -> null
    }
    return WhyUiModel(metric, variant, explanations, provenance, duration)
}

