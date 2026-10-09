package app.drift.charts

import app.drift.designsystem.DriftFormat
import app.drift.model.SleepStage
import app.drift.model.StageSpan
import app.drift.model.engine.CircadianEstimate
import app.drift.model.engine.DailyDebt
import app.drift.model.engine.DayCurve
import app.drift.model.engine.EnergyPhase
import app.drift.model.Interval
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Pure functions that turn plain model values into the text TalkBack reads for each chart. English only; the
 * strings are built here (not in resources) because they are sentences assembled from data.
 */

private fun clock(t: Instant, zone: ZoneId, use24h: Boolean) = DriftFormat.clock(t, zone, use24h)

/** Peaks, dips, the melatonin window and (when [now] is inside the day) the current level, as one paragraph. */
fun energyCurveDescription(curve: DayCurve, zone: ZoneId, use24h: Boolean, now: Instant? = null): String {
    val parts = mutableListOf("Energy curve for ${longDate(curve.date)}.")
    val peaks = curve.segments.filter { it.phase == EnergyPhase.MORNING_PEAK || it.phase == EnergyPhase.EVENING_PEAK }
    val dips = curve.segments.filter { it.phase == EnergyPhase.AFTERNOON_DIP }
    fun describe(seg: app.drift.model.engine.EnergySegment): String {
        val at = seg.extremumAt ?: seg.interval.start
        val level = EnergyGeometry.levelAt(curve, at)?.roundToInt()
        return seg.phase.displayName().lowercase() + " around " + clock(at, zone, use24h) + (level?.let { ", level $it of 100" } ?: "")
    }
    if (peaks.isEmpty() && dips.isEmpty()) parts += "No clear peaks or dips."
    else {
        if (peaks.isNotEmpty()) parts += "Peaks: " + peaks.joinToString("; ") { describe(it) } + "."
        if (dips.isNotEmpty()) parts += "Dip: " + dips.joinToString("; ") { describe(it) } + "."
    }
    if (curve.tooFlatForDip) parts += "The curve is too flat today to show a reliable afternoon dip."
    curve.segments.firstOrNull { it.phase == EnergyPhase.WIND_DOWN }?.let {
        parts += "Wind-down starts at ${clock(it.interval.start, zone, use24h)}."
    }
    curve.segments.firstOrNull { it.phase == EnergyPhase.MELATONIN_WINDOW }?.let {
        parts += "Melatonin window ${clock(it.interval.start, zone, use24h)} to ${clock(it.interval.end, zone, use24h)}."
    }
    if (now != null) {
        val level = EnergyGeometry.levelAt(curve, now)?.roundToInt()
        val phase = curve.segments.firstOrNull { it.interval.contains(now) }?.phase?.displayName()?.lowercase()
        if (level != null) parts += "Now ${clock(now, zone, use24h)}: level $level of 100" + (phase?.let { ", $it" } ?: "") + "."
    }
    parts += "Confidence ${curve.confidence.name.lowercase()}, ${curve.provenance.name.lowercase().replace('_', '-')}."
    return parts.joinToString(" ")
}

/** Short version for the mini curve and widget. */
fun miniEnergyDescription(curve: DayCurve, zone: ZoneId, use24h: Boolean, now: Instant? = null): String {
    val marks = curve.segments.filter { it.phase in setOf(EnergyPhase.MORNING_PEAK, EnergyPhase.EVENING_PEAK, EnergyPhase.AFTERNOON_DIP) && it.extremumAt != null }
        .joinToString(", ") { it.phase.displayName().lowercase() + " " + clock(it.extremumAt!!, zone, use24h) }
    val nowText = now?.let { EnergyGeometry.levelAt(curve, it)?.roundToInt()?.let { l -> " Now level $l of 100." } }.orEmpty()
    return "Energy for ${longDate(curve.date)}: ${marks.ifEmpty { "no clear peaks or dips" }}.$nowText"
}

fun debtBarsDescription(days: List<DailyDebt>): String {
    if (days.isEmpty()) return "Sleep debt chart. No nights to show."
    val real = days.filter { it.sleep != null || it.imputed }
    val missing = days.count { it.sleep == null && !it.imputed }
    val imputed = days.count { it.imputed }
    val net = days.fold(Duration.ZERO) { acc, d -> acc.plus(d.contribution) }
    val up = days.filter { it.contribution > Duration.ZERO }
    val down = days.filter { it.contribution < Duration.ZERO }
    val parts = mutableListOf("Sleep debt change over ${days.size} nights, ${shortDate(days.first().date)} to ${shortDate(days.last().date)}.")
    parts += "Net " + DriftFormat.signedDuration(net).replace("-", "minus ").replace("+", "plus ") + "."
    up.maxByOrNull { it.contribution }?.let { parts += "Biggest increase ${shortDate(it.date)}, ${DriftFormat.durationSpoken(it.contribution)} added." }
    down.minByOrNull { it.contribution }?.let { parts += "Biggest repayment ${shortDate(it.date)}, ${DriftFormat.durationSpoken(it.contribution.negated())} repaid." }
    parts += "${up.size} nights added debt, ${down.size} repaid it."
    if (imputed > 0) parts += "$imputed ${if (imputed == 1) "night is" else "nights are"} estimated."
    if (missing > 0) parts += "$missing ${if (missing == 1) "night has" else "nights have"} no data."
    if (real.isEmpty()) parts += "No recorded nights."
    return parts.joinToString(" ")
}

fun sleepTimingDescription(bars: List<TimingBar>, range: TimingRange, use24h: Boolean): String {
    if (bars.isEmpty()) return "Sleep timing chart. No nights to show."
    val present = bars.filter { it.onsetMinutes != null && it.wakeMinutes != null }
    val label = when (range) { TimingRange.WEEK -> "week"; TimingRange.MONTH -> "month"; TimingRange.QUARTER -> "3 months" }
    if (present.isEmpty()) return "Sleep timing over the last $label. No recorded nights."
    val onset = present.map { it.onsetMinutes!! }.average().roundToInt()
    val wake = present.map { it.wakeMinutes!! }.average().roundToInt()
    val avgDur = present.map { it.wakeMinutes!! - it.onsetMinutes!! }.average().roundToInt()
    val missing = bars.size - present.size
    val parts = mutableListOf(
        "Sleep timing over the last $label, ${present.size} of ${bars.size} nights recorded.",
        "Average bedtime ${DriftFormat.clockMinutes(onset, use24h)}, average wake time ${DriftFormat.clockMinutes(wake, use24h)}, average sleep ${DriftFormat.durationSpoken(Duration.ofMinutes(avgDur.toLong()))}.",
    )
    val spreadOnset = present.maxOf { it.onsetMinutes!! } - present.minOf { it.onsetMinutes!! }
    parts += "Bedtime varied by ${DriftFormat.durationSpoken(Duration.ofMinutes(spreadOnset.toLong()))}."
    if (missing > 0) parts += "$missing ${if (missing == 1) "night has" else "nights have"} no data."
    return parts.joinToString(" ")
}

fun stageDescription(spans: List<StageSpan>, zone: ZoneId, use24h: Boolean): String {
    if (spans.isEmpty()) return "Sleep stage chart. No stage data for this night."
    val sorted = spans.sortedBy { it.interval.start }
    fun total(stage: SleepStage) = sorted.filter { it.stage == stage }.fold(Duration.ZERO) { a, s -> a.plus(s.interval.duration) }
    val first = sorted.first().interval.start
    val last = sorted.maxOf { it.interval.end }
    val afterFirstSleep = sorted.dropWhile { it.stage == SleepStage.AWAKE }
    val wakeups = afterFirstSleep.dropLastWhile { it.stage == SleepStage.AWAKE }.count { it.stage == SleepStage.AWAKE }
    fun item(name: String, stage: SleepStage): String? = total(stage).takeIf { !it.isZero }?.let { "$name ${DriftFormat.durationSpoken(it)}" }
    val list = listOfNotNull(item("awake", SleepStage.AWAKE), item("REM", SleepStage.REM), item("light", SleepStage.LIGHT), item("deep", SleepStage.DEEP), item("unknown", SleepStage.UNKNOWN))
    return "Sleep stages from ${clock(first, zone, use24h)} to ${clock(last, zone, use24h)}: ${list.joinToString(", ")}. " +
        "$wakeups ${if (wakeups == 1) "awakening" else "awakenings"} during the night."
}

fun regularityTrendDescription(trend: List<Pair<LocalDate, Double>>): String {
    if (trend.isEmpty()) return "Sleep regularity chart. No data yet."
    val values = trend.map { it.second }
    val first = values.first()
    val last = values.last()
    val delta = last - first
    val direction = when {
        trend.size < 3 -> "not enough points for a trend"
        delta >= 3 -> "improving, up ${abs(delta).roundToInt()} points"
        delta <= -3 -> "declining, down ${abs(delta).roundToInt()} points"
        else -> "steady"
    }
    return "Sleep regularity index over ${trend.size} days, ${dayMonth(trend.first().first)} to ${dayMonth(trend.last().first)}. " +
        "Latest ${last.roundToInt()} out of 100, $direction. Range ${values.min().roundToInt()} to ${values.max().roundToInt()}."
}

fun circadianDescription(
    circadian: CircadianEstimate,
    zone: ZoneId,
    use24h: Boolean,
    sleepWindow: Interval? = null,
): String {
    val parts = mutableListOf<String>()
    val dlmo = circadian.dlmoMinutes
    if (dlmo == null && circadian.melatoninWindow == null) {
        parts += "Body clock dial. No melatonin estimate yet."
    } else {
        parts += "Body clock dial over 24 hours."
        if (dlmo != null) {
            val band = dlmo.confidence?.let { " give or take ${DriftFormat.durationSpoken(Duration.ofMinutes(((it.upper - it.lower) / 2).roundToInt().toLong()))}" }.orEmpty()
            parts += "Estimated melatonin onset around ${DriftFormat.clockMinutes(dlmo.value.roundToInt(), use24h)}$band."
        }
        circadian.windDownStart?.let { parts += "Wind-down starts at ${clock(it, zone, use24h)}." }
        circadian.melatoninWindow?.let { parts += "Melatonin window ${clock(it.start, zone, use24h)} to ${clock(it.end, zone, use24h)}." }
    }
    sleepWindow?.let { parts += "Planned sleep ${clock(it.start, zone, use24h)} to ${clock(it.end, zone, use24h)}." }
    circadian.reentrainment?.let {
        parts += "Still adjusting after ${if (it.eastward) "eastward" else "westward"} travel, about ${it.estimatedDaysLeft} ${if (it.estimatedDaysLeft == 1) "day" else "days"} left."
    }
    if (circadian.shiftWorkMode) parts += "Shift-work mode is on."
    return parts.joinToString(" ")
}
