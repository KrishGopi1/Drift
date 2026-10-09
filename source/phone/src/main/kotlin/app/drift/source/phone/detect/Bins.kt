package app.drift.source.phone.detect

import app.drift.model.Interval
import app.drift.source.phone.ClassifySample
import app.drift.source.phone.SleepSegment
import app.drift.source.phone.StepSpan
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

internal val BIN: Duration = Duration.ofMinutes(10)

/** Evidence for one 10-minute bin. A null field means "no data of that kind", never "zero". */
internal data class Bin(
    val start: Instant,
    val localHour: Double,
    val screenOnMinutes: Double?,
    /** 0..1 sleep likelihood from the Sleep API for this bin. */
    val sleepApi: Double?,
    val charging: Boolean?,
    /** Steps taken in this bin, only when a short enough counter interval covers it. */
    val steps: Double?,
) {
    val end: Instant get() = start.plus(BIN)
}

/** Everything the phone logged, prior to binning. */
internal data class PhoneDataSet(
    val screen: ScreenTimeline?,
    val segments: List<SleepSegment>,
    val classify: List<ClassifySample>,
    val charging: ChargeTimeline,
    val steps: List<StepSpan>,
    /** True when the Sleep API is registered and delivering, so silence is itself weak evidence. */
    val sleepApiActive: Boolean,
)

internal object Binner {
    /** Value used for bins where the Sleep API is reachable but reported nothing. */
    const val API_SILENT = 0.4
    /** Counter intervals longer than this are too coarse to say anything about a single bin. */
    val MAX_STEP_SPAN: Duration = Duration.ofMinutes(30)

    fun build(window: Interval, zone: ZoneId, data: PhoneDataSet): List<Bin> {
        val bins = mutableListOf<Bin>()
        val screenOn = data.screen?.onIntervals(window.start, window.end)
        val shortSteps = data.steps.filter { Duration.between(it.start, it.end) <= MAX_STEP_SPAN }
        var t = window.start
        while (t < window.end) {
            val end = minOf(t.plus(BIN), window.end)
            val bin = Interval(t, end)
            val hourFrac = t.atZone(zone).let { it.hour + it.minute / 60.0 }
            bins += Bin(
                start = t,
                localHour = hourFrac,
                screenOnMinutes = screenOn?.let { list -> list.sumOf { overlapMinutes(it, bin) } },
                sleepApi = apiValue(bin, data),
                charging = data.charging.pluggedAt(t),
                steps = stepsIn(bin, shortSteps),
            )
            t = t.plus(BIN)
        }
        return bins
    }

    private fun overlapMinutes(a: Interval, b: Interval): Double =
        a.intersect(b)?.let { it.duration.toMillis() / 60_000.0 } ?: 0.0

    private fun apiValue(bin: Interval, data: PhoneDataSet): Double? {
        val segCover = data.segments.sumOf { s ->
            if (s.end <= s.start) 0.0 else overlapMinutes(Interval(s.start, s.end), bin)
        } / (bin.duration.toMillis() / 60_000.0)
        if (segCover > 0.0) return 0.5 + 0.45 * min(1.0, segCover)
        val inBin = data.classify.filter { bin.contains(it.at) }
        if (inBin.isNotEmpty()) return inBin.map { it.confidence }.average().coerceIn(0.0, 100.0) / 100.0
        return if (data.sleepApiActive) API_SILENT else null
    }

    private fun stepsIn(bin: Interval, spans: List<StepSpan>): Double? {
        var total = 0.0
        var covered = false
        for (s in spans) {
            val span = Interval(s.start, s.end)
            val o = span.intersect(bin) ?: continue
            covered = true
            total += s.count * (o.duration.toMillis().toDouble() / max(1L, span.duration.toMillis()))
        }
        return if (covered) total else null
    }
}
