package app.drift.testing.fixtures

import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.Cause
import app.drift.model.engine.CauseKind
import app.drift.model.engine.Explanation
import app.drift.model.engine.MetricKey
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object Explanations {
    private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.US)

    fun build(style: ExplanationStyle, snap: AnalysisSnapshot, spec: SnapshotSpec): List<Explanation> = when (style) {
        ExplanationStyle.NONE -> emptyList()
        ExplanationStyle.FIRST_RUN -> firstRun()
        ExplanationStyle.RICH -> rich(snap, spec)
    }

    private fun firstRun(): List<Explanation> = listOf(
        MetricKey.SLEEP_NEED to "Your sleep need starts from typical values for your age until Drift has seen more of your nights.",
        MetricKey.ENERGY to "This is your first energy curve, built from your schedule and population averages.",
        MetricKey.CIRCADIAN to "Your body-clock estimate comes from the chronotype you chose; it will sharpen as nights arrive.",
        MetricKey.RECOMMENDATION to "Bed and wake times are based on your schedule and a typical sleep need for now.",
    ).map { (metric, text) ->
        Explanation(metric, text, listOf(Cause(CauseKind.FIRST_RUN, null, null, text)))
    }

    private fun rich(snap: AnalysisSnapshot, spec: SnapshotSpec): List<Explanation> {
        val trend = snap.debtTrend
        val last = trend.last()
        val leftDate = trend.first().date.minusDays(1)
        val leftContribution = Duration.ofMinutes(20) // what the night that dropped out had contributed
        val change = last.contribution - leftContribution
        val sleepText = last.sleep?.value?.let { SnapshotBuilder.fmt(it) } ?: "no data"
        val direction = if (change.isNegative) "fell" else "rose"
        val verb = if (last.contribution.isNegative) "repaid" else "added"
        val headline = "Debt $direction ${SnapshotBuilder.fmt(change.abs())}: last night's $sleepText $verb " +
            "${SnapshotBuilder.fmt(last.contribution.abs())}, and ${DAY.format(leftDate)} dropped out of your 14-day window."
        val editDate = trend[trend.size / 2].date
        val debtCauses = listOf(
            Cause(CauseKind.NIGHT_ENTERED_WINDOW, last.date, last.contribution.toMinutes().toDouble(),
                "${DAY.format(last.date)}: $sleepText of sleep $verb ${SnapshotBuilder.fmt(last.contribution.abs())}."),
            Cause(CauseKind.NIGHT_LEFT_WINDOW, leftDate, -leftContribution.toMinutes().toDouble(),
                "${DAY.format(leftDate)} left the window and took ${SnapshotBuilder.fmt(leftContribution)} of debt with it."),
            Cause(CauseKind.USER_EDIT, editDate, 15.0, "You edited ${DAY.format(editDate)}, which added 15 min of sleep."),
            Cause(CauseKind.SOURCE_ADDED, null, null, "Sleep as Android joined Health Connect; overlapping nights were merged."),
            Cause(CauseKind.NEED_REESTIMATED, null, 5.0, "Your estimated need moved 5 min after another informative night."),
        )
        return listOf(
            Explanation(MetricKey.SLEEP_DEBT, headline, debtCauses),
            Explanation(
                MetricKey.ENERGY,
                "Your afternoon dip moved 20 min earlier as your body clock and ${spec.calibrationCheckIns} check-ins were folded in.",
                listOf(
                    Cause(CauseKind.CIRCADIAN_SHIFT, last.date, -20.0, "Your estimated melatonin onset moved 18 min earlier."),
                    Cause(CauseKind.CHECK_IN_CALIBRATION, null, spec.calibrationCheckIns.toDouble(),
                        "${spec.calibrationCheckIns} check-ins adjusted the strength and timing of the curve."),
                ),
            ),
        )
    }

    private fun Duration.abs(): Duration = if (isNegative) negated() else this
}
