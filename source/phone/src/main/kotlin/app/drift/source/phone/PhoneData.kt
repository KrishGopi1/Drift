package app.drift.source.phone

import java.time.Instant

/** Screen turned interactive (true) or non-interactive (false). */
internal data class ScreenChange(val at: Instant, val interactive: Boolean)

/** One Google Sleep API segment. [status] follows SleepSegmentEvent.STATUS_*. */
internal data class SleepSegment(val start: Instant, val end: Instant, val status: Int)

/** One Sleep API classification sample. [confidence] 0..100, [motion] 0..100, [light] 0..100. */
internal data class ClassifySample(val at: Instant, val confidence: Int, val motion: Int, val light: Int)

internal data class ChargeChange(val at: Instant, val plugged: Boolean)

/** Cumulative hardware step counter reading. [bootKey] changes after a reboot (the counter resets). */
internal data class StepSample(val at: Instant, val total: Long, val bootKey: Long)

/** The next alarm clock as seen at [sampledAt]; [triggerAt] is when it will ring. */
internal data class AlarmSample(val sampledAt: Instant, val triggerAt: Instant)

/** Steps taken over [start]..[end], derived from two consecutive counter samples. */
internal data class StepSpan(val start: Instant, val end: Instant, val count: Long)

internal fun stepSpans(samples: List<StepSample>): List<StepSpan> =
    samples.sortedBy { it.at }.zipWithNext().mapNotNull { (a, b) ->
        val delta = b.total - a.total
        if (kotlin.math.abs(a.bootKey - b.bootKey) > 1 || delta < 0 || !b.at.isAfter(a.at)) null else StepSpan(a.at, b.at, delta)
    }
