package app.drift.testing.synthetic

import app.drift.model.DataQuality
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.QualityFlag
import app.drift.model.SleepEpisode
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Plan 9.1 scenario histories. All are deterministic per [ScenarioConfig.seed].
 *
 * ```
 * val h = Scenarios.chronicShortSleeper(ScenarioConfig(seed = 7))
 * val input = h.toEngineInput()          // EngineInput for the engine
 * h.truth.sleepNeed                      // 8 h, what the engine should approach
 * ```
 *
 * Advertised properties (what tests assert):
 * - steadySleeper: asleep ~8 h every night, onset spread < 40 min; truth need 8 h.
 * - chronicShortSleeper: asleep ~6 h on workdays, ~7 h on free days; truth need 8 h, so debt accumulates.
 * - weekendCatchUp: ~6.5 h on workdays, ~9.5 h on free days; large social jetlag.
 * - newParent: 2-4 awakenings per night, bedtime varies +-1 h, naps on most days; truth need 8 h.
 * - rotatingNightShift: 7 day-shift nights (23:00-07:00), 7 night-shift sleeps (09:00-16:30), 7 off days (01:00-09:30), repeating.
 * - easternTravel / westernTravel: zone changes 20 days before the end (facts["zoneChangeDate"]); the true DLMO,
 *   expressed in the new local time, starts 6 h off and converges (1 h/day east, 1.5 h/day west).
 * - missingWeek: seven consecutive nights (indexes 28-34) with no recorded data at all.
 * - duplicateSources: every night from a WEARABLE_WRIST Health Connect source and (90%) from a PHONE_APP
 *   Sleep as Android source with slight offsets; [SyntheticHistory.episodes] has exactly one episode per night.
 * - dstSpringForward / dstFallBack: America/New_York, 23:00-07:00 local every night; the transition night is
 *   7 h / 9 h of real elapsed time (facts["dstTransitionDate"]).
 * - noisyPhoneInferred: PHONE_INFERRED tier, 30% low-quality nights and two implausible-duration episodes.
 */
object Scenarios {
    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    private val NY: ZoneId = ZoneId.of("America/New_York")
    private val H = 60.0

    private fun at(date: LocalDate, hour: Int, minute: Int = 0, plusMinutes: Double = 0.0): LocalDateTime =
        date.atTime(hour, minute).plusSeconds((plusMinutes * 60).toLong())

    /** Evening bedtime (of the previous calendar day) at [hour]:[minute]; hours < 12 mean after midnight on [wakeDate]. */
    private fun bed(wakeDate: LocalDate, hour: Int, minute: Int, jitter: Double): LocalDateTime {
        val base = if (hour >= 12) wakeDate.minusDays(1).atTime(hour, minute) else wakeDate.atTime(hour, minute)
        return base.plusSeconds((jitter * 60).toLong())
    }

    private fun dlmoFromBed(start: LocalDateTime): Double =
        ((start.toLocalTime().toSecondOfDay() / 60.0 - 120) % 1440 + 1440) % 1440

    fun steadySleeper(cfg: ScenarioConfig = ScenarioConfig()): SyntheticHistory = SyntheticGenerator.generate(
        ScenarioSpec(
            "steady", cfg, Duration.ofHours(8),
            plan = { d, _, rng ->
                val free = isFreeDay(d)
                val s = bed(d, 23, 0, rng.gauss(18.0).coerceIn(-45.0, 45.0))
                val e = at(d, 7, 0, (if (free) 60.0 else 0.0) + rng.gauss(10.0).coerceIn(-25.0, 25.0))
                NightPlan(s, e)
            },
            dlmo = { _, _, _ -> 21.0 * H },
        ),
    )

    fun chronicShortSleeper(cfg: ScenarioConfig = ScenarioConfig()): SyntheticHistory = SyntheticGenerator.generate(
        ScenarioSpec(
            "chronic-short", cfg, Duration.ofHours(8),
            plan = { d, _, rng ->
                val free = isFreeDay(d)
                val s = if (free) bed(d, 1, 0, rng.gauss(15.0)) else bed(d, 0, 30, rng.gauss(15.0))
                val e = if (free) at(d, 8, 0, rng.gauss(15.0)) else at(d, 6, 30, rng.gauss(8.0))
                NightPlan(s, e)
            },
            dlmo = { _, _, _ -> 22.5 * H },
        ),
    )

    fun weekendCatchUp(cfg: ScenarioConfig = ScenarioConfig()): SyntheticHistory = SyntheticGenerator.generate(
        ScenarioSpec(
            "weekend-catch-up", cfg, Duration.ofHours(8),
            plan = { d, _, rng ->
                val free = isFreeDay(d)
                val s = if (free) bed(d, 0, 30, rng.gauss(15.0)) else bed(d, 0, 0, rng.gauss(12.0))
                val e = if (free) at(d, 10, 0, rng.gauss(20.0)) else at(d, 6, 30, rng.gauss(6.0))
                NightPlan(s, e)
            },
            dlmo = { _, _, _ -> 22.0 * H },
        ),
    )

    fun newParent(cfg: ScenarioConfig = ScenarioConfig()): SyntheticHistory = SyntheticGenerator.generate(
        ScenarioSpec(
            "new-parent", cfg, Duration.ofHours(8),
            plan = { d, _, rng ->
                val s = bed(d, 22, 30, rng.gauss(40.0).coerceIn(-75.0, 75.0))
                val e = at(d, 6, 0, rng.gauss(20.0).coerceIn(-45.0, 45.0))
                val nap = if (rng.chance(0.6)) LocalTime.of(13, 30) to rng.int(30, 90) else null
                NightPlan(s, e, awakenings = rng.int(2, 4), nap = nap)
            },
            dlmo = { _, _, _ -> 21.0 * H },
        ),
    )

    fun rotatingNightShift(cfg: ScenarioConfig = ScenarioConfig()): SyntheticHistory {
        var dlmo = 21.0 * H
        return SyntheticGenerator.generate(
            ScenarioSpec(
                "night-shift", cfg, Duration.ofHours(8),
                plan = { d, i, rng ->
                    when ((i / 7) % 3) {
                        0 -> NightPlan(bed(d, 23, 0, rng.gauss(15.0)), at(d, 7, 0, rng.gauss(10.0)))
                        1 -> NightPlan(at(d, 9, 0, rng.gauss(20.0)), at(d, 16, 30, rng.gauss(15.0)), awakenings = rng.int(0, 2))
                        else -> NightPlan(at(d, 1, 0, rng.gauss(30.0)), at(d, 9, 30, rng.gauss(30.0)))
                    }
                },
                dlmo = { _, _, night ->
                    // The clock chases sleep onset minus 2 h, slowly (8% of the gap per day), the way a rotating worker never adapts.
                    val target = dlmoFromBed(LocalDateTime.ofInstant(night.interval.start, night.zone))
                    val diff = ((target - dlmo + 720 + 1440) % 1440) - 720
                    dlmo = ((dlmo + 0.08 * diff) % 1440 + 1440) % 1440
                    dlmo
                },
            ),
        )
    }

    private fun travel(name: String, from: ZoneId, to: ZoneId, shiftHours: Double, ratePerDay: Double, cfg: ScenarioConfig): SyntheticHistory {
        val arrival = cfg.endDate.minusDays(20)
        val c = cfg.copy(zone = to, days = maxOf(cfg.days, 50))
        fun remaining(d: LocalDate): Double {
            val dayN = java.time.temporal.ChronoUnit.DAYS.between(arrival, d).toDouble()
            if (dayN < 0) return 0.0
            val r = abs(shiftHours) - ratePerDay * dayN
            return if (r <= 0) 0.0 else Math.signum(shiftHours) * r
        }
        return SyntheticGenerator.generate(
            ScenarioSpec(
                name, c, Duration.ofHours(8),
                zoneFor = { if (it < arrival) from else to },
                plan = { d, _, rng ->
                    val r = remaining(d)
                    val onsetShift = if (r >= 0) 0.6 * r else 0.4 * r
                    val wakeShift = if (r >= 0) 0.2 * r else 0.5 * r
                    val s = bed(d, 23, 0, onsetShift * H + rng.gauss(15.0))
                    val e = at(d, 7, 0, wakeShift * H + rng.gauss(10.0))
                    NightPlan(s, e, awakenings = 1 + (abs(r) / 2).toInt())
                },
                // Body clock in local time: habitual DLMO plus the unresolved part of the shift.
                dlmo = { d, _, _ -> ((21.0 * H + remaining(d) * H) % 1440 + 1440) % 1440 },
                facts = mapOf(
                    "zoneChangeDate" to arrival.toString(),
                    "fromZone" to from.id,
                    "toZone" to to.id,
                    "initialShiftHours" to shiftHours.toString(),
                ),
            ),
        )
    }

    /** New York to Paris, +6 h, re-entraining at 1 h/day. */
    fun easternTravel(cfg: ScenarioConfig = ScenarioConfig()): SyntheticHistory =
        travel("travel-east", NY, ZoneId.of("Europe/Paris"), 6.0, 1.0, cfg)

    /** Paris to New York, -6 h, re-entraining at 1.5 h/day. */
    fun westernTravel(cfg: ScenarioConfig = ScenarioConfig()): SyntheticHistory =
        travel("travel-west", ZoneId.of("Europe/Paris"), NY, -6.0, 1.5, cfg)

    fun missingWeek(cfg: ScenarioConfig = ScenarioConfig()): SyntheticHistory = SyntheticGenerator.generate(
        ScenarioSpec(
            "missing-week", cfg, Duration.ofHours(8),
            plan = { d, _, rng ->
                NightPlan(bed(d, 23, 0, rng.gauss(18.0).coerceIn(-45.0, 45.0)), at(d, 7, 0, (if (isFreeDay(d)) 60.0 else 0.0) + rng.gauss(10.0).coerceIn(-25.0, 25.0)))
            },
            missing = { it in 28..34 },
            dlmo = { _, _, _ -> 21.0 * H },
            facts = mapOf("missingFirstIndex" to "28", "missingLastIndex" to "34"),
        ),
    )

    /** HC wrist tier plus Sleep as Android phone-app tier for the same nights. */
    fun duplicateSources(cfg: ScenarioConfig = ScenarioConfig()): SyntheticHistory = SyntheticGenerator.generate(
        ScenarioSpec(
            "duplicate-sources", cfg.copy(primary = SourceProfile.WRIST), Duration.ofHours(8),
            plan = { d, _, rng ->
                NightPlan(bed(d, 23, 0, rng.gauss(18.0).coerceIn(-45.0, 45.0)), at(d, 7, 0, (if (isFreeDay(d)) 60.0 else 0.0) + rng.gauss(10.0).coerceIn(-25.0, 25.0)))
            },
            extraSources = listOf(SourceProfile.PHONE_APP),
            dlmo = { _, _, _ -> 21.0 * H },
        ),
    )

    fun dstSpringForward(cfg: ScenarioConfig = ScenarioConfig()): SyntheticHistory =
        dst("dst-spring", LocalDate.of(2026, 3, 8), cfg)

    fun dstFallBack(cfg: ScenarioConfig = ScenarioConfig()): SyntheticHistory =
        dst("dst-fall", LocalDate.of(2026, 11, 1), cfg)

    private fun dst(name: String, transition: LocalDate, cfg: ScenarioConfig): SyntheticHistory {
        val c = cfg.copy(zone = NY, endDate = transition.plusDays(14), days = maxOf(cfg.days, 35))
        return SyntheticGenerator.generate(
            ScenarioSpec(
                name, c, Duration.ofHours(8),
                plan = { d, _, _ -> NightPlan(d.minusDays(1).atTime(23, 0), d.atTime(7, 0)) },
                dlmo = { _, _, _ -> 21.0 * H },
                facts = mapOf("dstTransitionDate" to transition.toString()),
            ),
        )
    }

    /** Phone-inferred tier only, with poor-quality nights and two implausible episodes. */
    fun noisyPhoneInferred(cfg: ScenarioConfig = ScenarioConfig()): SyntheticHistory = SyntheticGenerator.generate(
        ScenarioSpec(
            "noisy-phone", cfg.copy(primary = SourceProfile.PHONE_INFERRED, lowQualityRate = 0.3), Duration.ofHours(8),
            plan = { d, _, rng ->
                NightPlan(bed(d, 23, 0, rng.gauss(25.0).coerceIn(-60.0, 60.0)), at(d, 7, 0, (if (isFreeDay(d)) 60.0 else 0.0) + rng.gauss(15.0).coerceIn(-35.0, 35.0)))
            },
            dlmo = { _, _, _ -> 21.0 * H },
            extraEpisodes = { _, src ->
                // Two daytime blips that the plan says to exclude: 1.5 h flagged short and 17 h flagged long.
                val zone = cfg.zone
                val shortStart = cfg.endDate.minusDays(40).atTime(14, 0).atZone(zone).toInstant()
                val longStart = cfg.endDate.minusDays(30).minusDays(1).atTime(20, 0).atZone(zone).toInstant()
                listOf(
                    SleepEpisode(
                        EpisodeId("noisy-short"), shortStart, shortStart.plus(Duration.ofMinutes(90)), zone, EpisodeKind.MAIN,
                        origin = src.ref("noisy-short"), quality = DataQuality(0.7, 0.4, setOf(QualityFlag.IMPLAUSIBLE_DURATION)),
                    ),
                    SleepEpisode(
                        EpisodeId("noisy-long"), longStart, longStart.plus(Duration.ofHours(17)), zone, EpisodeKind.MAIN,
                        origin = src.ref("noisy-long"), quality = DataQuality(0.6, 0.35, setOf(QualityFlag.IMPLAUSIBLE_DURATION)),
                    ),
                )
            },
        ),
    )

    /** Every scenario, with stable names, for a given seed. */
    fun all(seed: Long = 1L): Map<String, SyntheticHistory> {
        val cfg = ScenarioConfig(seed = seed)
        return linkedMapOf(
            "steady" to steadySleeper(cfg),
            "chronic_short" to chronicShortSleeper(cfg),
            "weekend_catch_up" to weekendCatchUp(cfg),
            "new_parent" to newParent(cfg),
            "night_shift" to rotatingNightShift(cfg),
            "travel_east" to easternTravel(cfg),
            "travel_west" to westernTravel(cfg),
            "missing_week" to missingWeek(cfg),
            "duplicate_sources" to duplicateSources(cfg),
            "dst_spring" to dstSpringForward(cfg),
            "dst_fall" to dstFallBack(cfg),
            "noisy_phone" to noisyPhoneInferred(cfg),
        )
    }
}
