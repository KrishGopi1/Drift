package app.drift.source.phone.detect

import java.time.Duration
import java.time.Instant

/** Result of decoding one night. [confidence] is 0..1; [completeness] is the share of bins backed by real data. */
internal data class DetectedSleep(
    val onset: Instant,
    val wake: Instant,
    val confidence: Double,
    val completeness: Double,
)

internal object SleepDetector {
    val MIN_SLEEP: Duration = Duration.ofMinutes(90)
    /** Typical gap between last phone use and falling asleep; applied when the screen-off time is known. */
    val SLEEP_LATENCY: Duration = Duration.ofMinutes(10)
    private val REFINE_WINDOW: Duration = Duration.ofMinutes(20)

    private const val TAIL_BINS = 6
    private const val CORROB_TIME = 1
    private const val CORROB_API = 2
    private const val CORROB_CHARGING = 4
    private val BRACKET: Duration = Duration.ofHours(4)
    private const val NIGHT_START = 21.0
    private const val NIGHT_END = 10.0

    fun detect(bins: List<Bin>, screen: ScreenTimeline?, params: HmmParams = HmmParams()): DetectedSleep? {
        if (bins.size < 12) return null
        // Without screen or Sleep API data the only evidence is the clock, and that must never be enough.
        if (bins.none { it.screenOnMinutes != null || it.sleepApi != null }) return null

        val result = TwoStateHmm(params).decode(bins)
        val run = longestRun(result.path, bins) ?: return null
        val first = run.first
        var last = run.second // inclusive
        // The model cannot look ahead at the edge of the data, so one or two bins of morning phone use
        // right at the end are read as the wake itself.
        if (last == bins.lastIndex) last = trimTail(bins, first, last)
        if (first == 0 || last == bins.lastIndex) return null // no observed start or no observed wake yet

        val slice = bins.subList(first, last + 1)
        if (Duration.between(slice.first().start, slice.last().end) < MIN_SLEEP) return null
        val corroboration = corroboration(slice)
        if (corroboration == 0) return null

        val runStart = bins[first].start
        val runEnd = bins[last].end
        // Silence plus the clock is not enough: the phone must have been in use just before and just after,
        // otherwise it may simply have been off or left behind.
        if (corroboration and (CORROB_API or CORROB_CHARGING) == 0) {
            val before = screen?.lastScreenOffIn(runStart.minus(BRACKET), runStart) != null
            val after = screen?.firstScreenOnIn(runEnd.minus(BIN), runEnd.plus(BRACKET)) != null
            if (!before || !after) return null
        }
        val onset = screen?.lastScreenOffIn(runStart.minus(REFINE_WINDOW), runStart.plus(BIN))
            ?.plus(SLEEP_LATENCY)?.coerceIn(runStart.minus(BIN), runStart.plus(BIN)) ?: runStart
        val wake = screen?.firstScreenOnIn(runEnd.minus(BIN), runEnd.plus(REFINE_WINDOW)) ?: runEnd
        if (!wake.isAfter(onset)) return null

        val post = result.posterior
        val inner = (first..last).map { post[it] }.average()
        val edgeBefore = 1 - post[first - 1] // chance the bin before is awake
        val edgeAfter = 1 - post[last + 1]
        val edge = (post[first] + edgeBefore + post[last] + edgeAfter) / 4.0
        val evidence = evidenceScore(slice, corroboration)
        val confidence = (inner * edge * (0.4 + 0.6 * evidence)).coerceIn(0.0, 1.0)
        val completeness = slice.count { hasData(it) }.toDouble() / slice.size
        return DetectedSleep(onset, wake, confidence, completeness)
    }

    private fun awakeEvidence(b: Bin): Boolean =
        (b.screenOnMinutes ?: 0.0) >= 0.5 || (b.sleepApi != null && b.sleepApi < 0.3) || (b.steps ?: 0.0) >= 20.0

    private fun trimTail(bins: List<Bin>, first: Int, last: Int): Int {
        val from = maxOf(first + 1, last - TAIL_BINS + 1)
        val j = (from..last).firstOrNull { awakeEvidence(bins[it]) } ?: return last
        return j - 1
    }

    private fun hasData(b: Bin) = b.screenOnMinutes != null || b.sleepApi != null || b.steps != null || b.charging != null

    private fun longestRun(path: List<Hidden>, bins: List<Bin>): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var i = 0
        while (i < path.size) {
            if (path[i] != Hidden.ASLEEP) { i++; continue }
            var j = i
            while (j + 1 < path.size && path[j + 1] == Hidden.ASLEEP) j++
            val candidate = i to j
            val ok = corroboration(bins.subList(i, j + 1)) > 0
            if (ok && (best == null || (j - i) > (best.second - best.first))) best = candidate
            i = j + 1
        }
        return best
    }

    /** Bit mask: 1 = time of day, 2 = Sleep API, 4 = charging. Zero means uncorroborated. */
    private fun corroboration(run: List<Bin>): Int {
        var mask = 0
        val night = run.count { it.localHour >= NIGHT_START || it.localHour < NIGHT_END }
        if (night.toDouble() / run.size >= 0.6) mask = mask or CORROB_TIME
        val api = run.mapNotNull { it.sleepApi }
        if (api.isNotEmpty() && api.average() >= 0.6) mask = mask or CORROB_API
        val known = run.mapNotNull { it.charging }
        if (known.size >= run.size / 2 && known.isNotEmpty() && known.count { it } >= 0.7 * run.size) mask = mask or CORROB_CHARGING
        return mask
    }

    private fun evidenceScore(run: List<Bin>, corroboration: Int): Double {
        var score = 0.0
        if (run.count { it.screenOnMinutes != null } > run.size / 2) score += 0.45
        if (corroboration and CORROB_API != 0) score += 0.3
        if (run.count { it.sleepApi != null && it.sleepApi != Binner.API_SILENT } > run.size / 4) score += 0.1
        if (corroboration and CORROB_CHARGING != 0) score += 0.1
        if (run.any { it.steps != null }) score += 0.05
        return score.coerceAtMost(1.0)
    }

    private fun Instant.coerceIn(lo: Instant, hi: Instant): Instant = if (this < lo) lo else if (this > hi) hi else this
}
