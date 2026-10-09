package app.drift.testing.synthetic

import app.drift.model.CheckIn
import app.drift.model.DataQuality
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.Signal
import app.drift.model.SleepEpisode
import app.drift.model.SleepStage
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.StageSpan
import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.engine.Goals
import app.drift.model.engine.Schedule
import app.drift.model.engine.UserProfile
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

/** Settings shared by every scenario. Defaults give a 60-day history ending on the fixture "today". */
data class ScenarioConfig(
    val seed: Long = 1L,
    val days: Int = 60,
    val endDate: LocalDate = LocalDate.of(2026, 10, 14),
    val zone: ZoneId = ZoneId.of("Asia/Kolkata"),
    val primary: SourceProfile = SourceProfile.WRIST,
    val checkIns: CheckInDensity = CheckInDensity.SPARSE,
    val signals: SignalDensity = SignalDensity.PHONE_ONLY,
    /** Fraction of recorded nights reported with poor [DataQuality] (completeness 0.4-0.7, low confidence). */
    val lowQualityRate: Double = 0.0,
)

/** The plan for one night, in local wall-clock terms of that night's zone. */
internal data class NightPlan(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val awakenings: Int = 0,
    val nap: Pair<LocalTime, Int>? = null,
)

internal class ScenarioSpec(
    val name: String,
    val cfg: ScenarioConfig,
    val need: Duration,
    /** Zone in force on the night that ends on a given date. */
    val zoneFor: (LocalDate) -> ZoneId = { cfg.zone },
    val plan: (date: LocalDate, index: Int, rng: Rng) -> NightPlan,
    /** DLMO (minutes after local midnight) for the day [date]; called in date order. */
    val dlmo: (date: LocalDate, index: Int, night: TrueNight) -> Double,
    val missing: (index: Int) -> Boolean = { false },
    val profile: UserProfile = defaultProfile(),
    val extraSources: List<SourceProfile> = emptyList(),
    val facts: Map<String, String> = emptyMap(),
    /** Extra episodes added to the observed set on top of the nights (e.g. implausible ones). */
    val extraEpisodes: (rng: Rng, primary: SourceProfile) -> List<SleepEpisode> = { _, _ -> emptyList() },
    val localNowTime: LocalTime = LocalTime.of(13, 0),
)

internal fun defaultProfile(wake: LocalTime = LocalTime.of(7, 0)) = UserProfile(
    ageBand = AgeBand.AGE_26_35,
    chronotype = ChronotypeAnswer.NEITHER,
    schedule = Schedule(
        workdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        wakeTime = wake,
        freeDayBedtime = LocalTime.of(23, 30),
        freeDayWakeTime = wake.plusMinutes(90),
    ),
    goals = Goals(),
)

internal fun isFreeDay(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

internal object SyntheticGenerator {

    fun generate(spec: ScenarioSpec): SyntheticHistory {
        val cfg = spec.cfg
        val rng = Rng(cfg.seed)
        val dates = (0 until cfg.days).map { cfg.endDate.minusDays((cfg.days - 1 - it).toLong()) }

        // ---- truth
        val nights = dates.mapIndexed { i, date ->
            val zone = spec.zoneFor(date)
            val p = spec.plan(date, i, rng)
            val start = p.start.atZone(zone).toInstant()
            val end = p.end.atZone(zone).toInstant()
            val nap = p.nap?.let { (t, minutes) ->
                val s = date.atTime(t).atZone(zone).toInstant()
                Interval(s, s.plus(Duration.ofMinutes(minutes.toLong())))
            }
            TrueNight(date, zone, Interval(start, end), p.awakenings, nap)
        }
        val missingDates = nights.filterIndexed { i, _ -> spec.missing(i) }.map { it.date }.toSet()
        val dlmo = nights.mapIndexed { i, n -> n.date to spec.dlmo(n.date, i, n) }.toMap()
        val truth = GroundTruth(spec.need, dlmo, nights, missingDates, spec.facts)

        // ---- observed episodes per source (separate rng streams so adding a source never changes the others)
        val sources = listOf(cfg.primary) + spec.extraSources
        val rawByNight = sources.mapIndexed { si, src ->
            val srng = Rng(cfg.seed * 1_000_003L + si + 17)
            nights.mapNotNull { n ->
                if (n.date in missingDates) return@mapNotNull null
                // With duplicate sources a secondary source misses ~10% of nights.
                if (si > 0 && srng.chance(0.1)) return@mapNotNull null
                observe(spec.name, src, n, srng, cfg.lowQualityRate) to n
            }.flatMap { (eps, _) -> eps }
        }
        val extras = spec.extraEpisodes(Rng(cfg.seed + 99), cfg.primary)
        val raw = (rawByNight.flatten() + extras).sortedBy { it.start }
        val canonical = canonicalize(raw)

        // ---- annotations, check-ins, signals
        val annotations = nights.filter { it.date !in missingDates }.map { n ->
            val free = isFreeDay(n.date)
            DayAnnotation(n.date, freeDay = free, alarmSet = !free, alarmDismissedAt = if (!free) n.interval.end else null)
        }
        val checkIns = checkIns(cfg, spec, nights, dlmo, Rng(cfg.seed + 7))
        val signals = signals(cfg, nights, missingDates, Rng(cfg.seed + 13))

        val now = cfg.endDate.atTime(spec.localNowTime).atZone(spec.zoneFor(cfg.endDate)).toInstant()
        return SyntheticHistory(
            name = spec.name, seed = cfg.seed, zone = spec.zoneFor(cfg.endDate), now = now, profile = spec.profile,
            episodes = canonical, rawEpisodes = raw, annotations = annotations,
            checkIns = checkIns, signals = signals, truth = truth,
        )
    }

    /** Best tier wins per overlapping group, ties by longer episode; naps are kept separately. */
    private fun canonicalize(raw: List<SleepEpisode>): List<SleepEpisode> {
        val result = mutableListOf<SleepEpisode>()
        for (kind in EpisodeKind.entries) {
            val sorted = raw.filter { it.kind == kind }.sortedBy { it.start }
            val groups = mutableListOf<MutableList<SleepEpisode>>()
            for (e in sorted) {
                val g = groups.lastOrNull()
                if (g != null && g.any { it.interval.overlaps(e.interval) }) g += e else groups += mutableListOf(e)
            }
            groups.forEach { g ->
                val best = g.maxWith(compareBy<SleepEpisode>({ QualityFlag.IMPLAUSIBLE_DURATION !in it.quality.flags }, { it.origin.qualityTier }, { it.interval.duration }))
                result += if (g.size > 1) best.copy(quality = best.quality.copy(flags = best.quality.flags + QualityFlag.SOURCE_CONFLICT)) else best
            }
        }
        return result.sortedBy { it.start }
    }

    private fun observe(scenario: String, src: SourceProfile, n: TrueNight, rng: Rng, lowQualityRate: Double): List<SleepEpisode> {
        val out = mutableListOf<SleepEpisode>()
        val low = rng.chance(lowQualityRate)
        val noiseScale = if (low) 2.0 else 1.0
        var start = n.interval.start.plus(Duration.ofSeconds(((src.biasMinutes + rng.gauss(src.onsetNoiseMinutes * noiseScale)) * 60).toLong()))
        var end = n.interval.end.plus(Duration.ofSeconds(((src.biasMinutes + rng.gauss(src.endNoiseMinutes * noiseScale)) * 60).toLong()))
        if (Duration.between(start, end) < Duration.ofMinutes(30)) { start = n.interval.start; end = n.interval.end }
        val quality = if (low) DataQuality(rng.range(0.4, 0.7), rng.range(0.3, 0.6))
        else DataQuality(rng.range(src.completeness.start, src.completeness.endInclusive), rng.range(src.detectionConfidence.start, src.detectionConfidence.endInclusive))
        val id = "$scenario-${src.sourceId.value}-${n.date}"
        out += SleepEpisode(
            id = EpisodeId(id), start = start, end = end, zone = n.zone, kind = EpisodeKind.MAIN,
            stages = if (src.hasStages) stages(start, end, n.awakenings, rng) else null,
            inBed = if (src.hasStages) Interval(start.minus(Duration.ofMinutes(10)), end.plus(Duration.ofMinutes(5))) else null,
            origin = src.ref(id), quality = quality,
        )
        n.nap?.let { nap ->
            val napId = "$scenario-${src.sourceId.value}-nap-${n.date}"
            out += SleepEpisode(
                id = EpisodeId(napId), start = nap.start, end = nap.end, zone = n.zone, kind = EpisodeKind.NAP,
                origin = src.ref(napId), quality = DataQuality(rng.range(0.85, 1.0), 1.0),
            )
        }
        return out
    }

    /** 90-minute cycles at 5-minute resolution, with [awakenings] AWAKE spans of 10-40 minutes overlaid. */
    private fun stages(start: Instant, end: Instant, awakenings: Int, rng: Rng): List<StageSpan> {
        val slots = (Duration.between(start, end).toMinutes() / 5).toInt().coerceAtLeast(1)
        val arr = Array(slots) { SleepStage.LIGHT }
        for (i in 0 until slots) {
            val cycle = i / 18
            val inCycle = i % 18
            arr[i] = when {
                inCycle < 6 -> SleepStage.LIGHT
                inCycle < 6 + max(1, 5 - cycle) -> SleepStage.DEEP
                inCycle < 14 -> SleepStage.LIGHT
                else -> SleepStage.REM
            }
        }
        repeat(awakenings) {
            val at = rng.int(6, max(7, slots - 8))
            val len = rng.int(2, 8)
            for (j in at until minOf(slots, at + len)) arr[j] = SleepStage.AWAKE
        }
        val spans = mutableListOf<StageSpan>()
        var runStart = 0
        for (i in 1..slots) {
            if (i == slots || arr[i] != arr[runStart]) {
                val s = start.plus(Duration.ofMinutes(runStart * 5L))
                val e = if (i == slots) end else start.plus(Duration.ofMinutes(i * 5L))
                spans += StageSpan(arr[runStart], Interval(s, e))
                runStart = i
            }
        }
        return spans
    }

    private fun checkIns(cfg: ScenarioConfig, spec: ScenarioSpec, nights: List<TrueNight>, dlmo: Map<LocalDate, Double>, rng: Rng): List<CheckIn> {
        if (cfg.checkIns == CheckInDensity.NONE) return emptyList()
        val needH = spec.need.toMinutes() / 60.0
        val out = mutableListOf<CheckIn>()
        nights.forEachIndexed { i, n ->
            val every = if (cfg.checkIns == CheckInDensity.DENSE) 1 else 3
            if (i % every != 0) return@forEachIndexed
            val slept = n.interval.duration.toMinutes() / 60.0
            val deficit = max(0.0, needH - slept)
            val hours = if (cfg.checkIns == CheckInDensity.DENSE) listOf(10, 15, 20) else listOf(15)
            hours.forEach { h ->
                val at = n.date.atTime(h, 0).atZone(n.zone).toInstant()
                val sinceWake = Duration.between(n.interval.end, at).toMinutes() / 60.0
                if (sinceWake < 0.5) return@forEach
                val rel = (h - dlmo.getValue(n.date) / 60.0 + 12 + 24) % 24 - 12
                val level = 50 + 25 * cos(2 * PI * (rel + 5) / 24) + 17 * cos(2 * PI * (rel + 12.5) / 12) - 9 * deficit
                val rating = (1 + level / 25.0 + rng.gauss(0.4)).roundToInt().coerceIn(1, 5)
                out += CheckIn.EnergyRating(at, rating)
            }
            if (cfg.checkIns == CheckInDensity.DENSE && i % 2 == 0) {
                val at = n.date.atTime(14, 0).atZone(n.zone).toInstant()
                if (Duration.between(n.interval.end, at) > Duration.ofHours(1)) {
                    out += CheckIn.ReactionTest(at, 270.0 + 35 * deficit + rng.gauss(12.0), (deficit * 1.2 + rng.double() * 1.5).toInt(), 20)
                }
            }
        }
        return out.sortedBy { it.at }
    }

    private fun signals(cfg: ScenarioConfig, nights: List<TrueNight>, missing: Set<LocalDate>, rng: Rng): List<Signal> {
        if (cfg.signals == SignalDensity.NONE) return emptyList()
        val out = mutableListOf<Signal>()
        val phone = { id: String -> SourceRef(SourceId.PHONE, id, QualityTier.PHONE_INFERRED) }
        val wearable = { id: String -> cfg.primary.ref(id) }
        for (n in nights) {
            if (n.date in missing) continue
            val p = phone("phone-${n.date}")
            val bed = n.interval.start
            val wake = n.interval.end
            out += Signal.ChargingEvent(p, bed.minus(Duration.ofMinutes(10)), true)
            out += Signal.ScreenEvent(p, bed.minus(Duration.ofMinutes(5)), false)
            out += Signal.ScreenEvent(p, wake.plus(Duration.ofMinutes(3)), true)
            out += Signal.ChargingEvent(p, wake.plus(Duration.ofMinutes(10)), false)
            if (!isFreeDay(n.date)) {
                out += Signal.AlarmEvent(p, bed.minus(Duration.ofMinutes(20)), app.drift.model.AlarmKind.SCHEDULED)
                out += Signal.AlarmEvent(p, wake, app.drift.model.AlarmKind.DISMISSED)
            }
            var t = bed
            var k = 0
            while (t.isBefore(wake)) {
                val e = minOf(t.plus(Duration.ofMinutes(30)), wake)
                val restless = n.awakenings > 0 && k % max(1, 12 / n.awakenings) == 5
                out += Signal.MotionBucket(p, Interval(t, e), if (restless) rng.range(0.4, 0.8) else rng.range(0.01, 0.1), if (restless) rng.range(0.1, 0.4) else rng.range(0.7, 0.95))
                out += Signal.LightBucket(p, Interval(t, e), rng.range(0.0, 1.5))
                t = e; k++
            }
            out += Signal.LightBucket(p, Interval(bed.minus(Duration.ofHours(1)), bed.minus(Duration.ofMinutes(30))), rng.range(20.0, 80.0))
            if (cfg.signals == SignalDensity.FULL) {
                val w = wearable("sig-${n.date}")
                var h = bed.plus(Duration.ofMinutes(15))
                while (h.isBefore(wake)) {
                    out += Signal.HeartRateSample(w, h, 52 + rng.gauss(4.0))
                    h = h.plus(Duration.ofMinutes(30))
                }
                out += Signal.RestingHeartRate(w, wake.plus(Duration.ofMinutes(30)), 55 + rng.gauss(2.0))
                out += Signal.HrvSample(w, bed.plus(Duration.between(bed, wake).dividedBy(2)), 45 + rng.gauss(8.0))
                val day = wake.plus(Duration.ofHours(2))
                for (hr in 0 until 10) {
                    val s = day.plus(Duration.ofHours(hr.toLong()))
                    out += Signal.StepBucket(w, Interval(s, s.plus(Duration.ofHours(1))), (200 + rng.double() * 1000).toLong())
                }
            }
        }
        return out.sortedBy { signalTime(it) }
    }
}
