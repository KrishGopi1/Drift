package app.drift.engine

import app.drift.model.CheckIn
import app.drift.model.DataQuality
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.QualityTier
import app.drift.model.SleepEpisode
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.engine.EngineInput
import app.drift.model.engine.Goals
import app.drift.model.engine.Schedule
import app.drift.model.engine.SignalWindow
import app.drift.model.engine.UserProfile
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Random

/** A complete engine input recipe that can be re-materialised in another zone with identical local times. */
class Scenario(
    val name: String,
    val zone: ZoneId,
    val profile: UserProfile,
    val nowLocal: LocalDateTime,
    val nights: List<LocalNight>,
    val annotations: List<DayAnnotation> = emptyList(),
    val checkIns: List<Pair<LocalDateTime, Int>> = emptyList(),
) {
    /** One sleep episode defined by local clock times in the zone it happened in. */
    class LocalNight(
        val start: LocalDateTime,
        val end: LocalDateTime,
        val zone: ZoneId? = null,
        val kind: EpisodeKind = EpisodeKind.MAIN,
        val tier: QualityTier = QualityTier.WEARABLE_WRIST,
        val id: String,
    )

    fun input(
        zoneOverride: ZoneId? = null,
        prior: app.drift.model.engine.EngineState? = null,
        nowOverride: LocalDateTime? = null,
        dropAfterNow: Boolean = true,
    ): EngineInput {
        val z = zoneOverride ?: zone
        val nowL = nowOverride ?: nowLocal
        val now = nowL.atZone(z).toInstant()
        val eps = nights.mapNotNull { n ->
            val ez = if (zoneOverride != null) z else (n.zone ?: zone)
            val s = n.start.atZone(ez).toInstant()
            val e = n.end.atZone(ez).toInstant()
            if (!e.isAfter(s)) return@mapNotNull null
            if (dropAfterNow && e.isAfter(now)) return@mapNotNull null
            SleepEpisode(
                id = EpisodeId(n.id), start = s, end = e, zone = ez, kind = n.kind, stages = null, inBed = null,
                origin = SourceRef(SourceId.HEALTH_CONNECT, n.id, n.tier, "synthetic"),
                quality = DataQuality.RECORDED,
            )
        }
        return EngineInput(
            now = now,
            zone = z,
            episodes = eps,
            signals = SignalWindow.empty(now),
            profile = profile,
            priorState = prior,
            annotations = annotations,
            checkIns = checkIns.map { (t, r) -> CheckIn.EnergyRating(t.atZone(z).toInstant(), r) },
        )
    }
}

/** Deterministic synthetic histories (seeded, no clock). */
object Synth {
    val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val UTC_START: LocalDate = LocalDate.of(2026, 8, 3) // a Monday

    fun profile(
        age: AgeBand = AgeBand.AGE_26_35,
        wake: LocalTime? = LocalTime.of(6, 45),
        workdays: Set<DayOfWeek> = WORKDAYS,
        chrono: ChronotypeAnswer? = ChronotypeAnswer.NEITHER,
        goals: Goals = Goals(),
    ) = UserProfile(age, chrono, Schedule(workdays, wake, null, null), goals)

    private fun jitter(r: Random, minutes: Int): Long = if (minutes == 0) 0 else (r.nextInt(2 * minutes + 1) - minutes).toLong()

    /**
     * Builds nights day by day: [plan] maps a wake date to (bedtime clock, wake clock); null skips the night.
     * Bedtimes after 12:00 belong to the evening before the wake date.
     */
    fun nights(
        zone: ZoneId,
        from: LocalDate,
        days: Int,
        seed: Long = 7,
        jitterMin: Int = 12,
        tier: QualityTier = QualityTier.WEARABLE_WRIST,
        plan: (LocalDate, Int) -> Pair<LocalTime, LocalTime>?,
    ): List<Scenario.LocalNight> {
        val r = Random(seed)
        val out = ArrayList<Scenario.LocalNight>()
        for (i in 0 until days) {
            val date = from.plusDays(i.toLong())
            val (bed, wake) = plan(date, i) ?: continue
            val j1 = jitter(r, jitterMin)
            val j2 = jitter(r, jitterMin)
            val wakeDt = date.atTime(wake).plusMinutes(j2)
            val bedDt = (if (bed.hour >= 12) date.minusDays(1).atTime(bed) else date.atTime(bed)).plusMinutes(j1)
            out += Scenario.LocalNight(bedDt, wakeDt, zone = zone, tier = tier, id = "n-$date")
        }
        return out
    }

    private fun isWorkday(d: LocalDate) = d.dayOfWeek in WORKDAYS

    fun steady(zone: ZoneId = ZoneId.of("Asia/Kolkata"), days: Int = 42): Scenario {
        val ns = nights(zone, UTC_START, days) { d, _ ->
            if (isWorkday(d)) LocalTime.of(23, 0) to LocalTime.of(6, 45) else LocalTime.of(23, 30) to LocalTime.of(8, 0)
        }
        return Scenario("steady", zone, profile(), UTC_START.plusDays(days.toLong()).atTime(12, 0), ns)
    }

    fun chronicShort(zone: ZoneId = ZoneId.of("Asia/Kolkata"), days: Int = 42): Scenario {
        val ns = nights(zone, UTC_START, days) { d, _ ->
            if (isWorkday(d)) LocalTime.of(0, 30) to LocalTime.of(6, 0) else LocalTime.of(0, 30) to LocalTime.of(7, 30)
        }
        return Scenario("chronic-short", zone, profile(wake = LocalTime.of(6, 0)), UTC_START.plusDays(days.toLong()).atTime(12, 0), ns)
    }

    fun weekendCatchUp(zone: ZoneId = ZoneId.of("Asia/Kolkata"), days: Int = 42): Scenario {
        val ns = nights(zone, UTC_START, days) { d, _ ->
            when (d.dayOfWeek) {
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> LocalTime.of(0, 0) to LocalTime.of(9, 45)
                else -> LocalTime.of(23, 45) to LocalTime.of(6, 30)
            }
        }
        return Scenario("weekend-catch-up", zone, profile(wake = LocalTime.of(6, 30)), UTC_START.plusDays(days.toLong()).atTime(12, 0), ns)
    }

    fun fragmentedParent(zone: ZoneId = ZoneId.of("Asia/Kolkata"), days: Int = 42): Scenario {
        val r = Random(11)
        val out = ArrayList<Scenario.LocalNight>()
        for (i in 0 until days) {
            val date = UTC_START.plusDays(i.toLong())
            val a = date.minusDays(1).atTime(22, 30).plusMinutes(jitter(r, 25))
            val cuts = listOf(
                a to date.atTime(1, 20).plusMinutes(jitter(r, 12)),
                date.atTime(1, 55).plusMinutes(jitter(r, 12)) to date.atTime(4, 10).plusMinutes(jitter(r, 12)),
                date.atTime(4, 55).plusMinutes(jitter(r, 12)) to date.atTime(7, 0).plusMinutes(jitter(r, 12)),
            )
            cuts.forEachIndexed { k, (s, e) -> out += Scenario.LocalNight(s, e, zone, tier = QualityTier.WEARABLE_WRIST, id = "f-$date-$k") }
            if (i % 2 == 0) out += Scenario.LocalNight(date.atTime(14, 0), date.atTime(14, 40), zone, EpisodeKind.NAP, id = "nap-$date")
        }
        return Scenario(
            "fragmented-parent", zone, profile(wake = null, workdays = emptySet()), UTC_START.plusDays(days.toLong()).atTime(12, 0), out,
        )
    }

    fun rotatingNightShift(zone: ZoneId = ZoneId.of("Asia/Kolkata"), days: Int = 42): Scenario {
        // 4 nights on (sleep 08:30-15:30), 3 off (sleep 00:00-08:00), repeating.
        val ns = nights(zone, UTC_START, days) { _, i ->
            if (i % 7 < 4) LocalTime.of(8, 30) to LocalTime.of(15, 30) else LocalTime.of(0, 0) to LocalTime.of(8, 0)
        }
        return Scenario("night-shift", zone, profile(wake = null, workdays = emptySet()), UTC_START.plusDays(days.toLong()).atTime(12, 0), ns)
    }

    /** Steady life in [from], then a trip to [to] after [homeDays]; sleep follows the local clock more each day. */
    fun travel(from: ZoneId, to: ZoneId, homeDays: Int = 28, awayDays: Int = 14, adaptPerDayMin: Int): Scenario {
        val flightDate = UTC_START.plusDays(homeDays.toLong())
        val home = nights(from, UTC_START, homeDays) { d, _ ->
            if (isWorkday(d)) LocalTime.of(23, 0) to LocalTime.of(7, 0) else LocalTime.of(23, 30) to LocalTime.of(8, 0)
        }
        val offsetDiff = (to.rules.getOffset(flightDate.atTime(12, 0)).totalSeconds - from.rules.getOffset(flightDate.atTime(12, 0)).totalSeconds) / 60
        val r = Random(5)
        val away = ArrayList<Scenario.LocalNight>()
        for (k in 0 until awayDays) {
            val date = flightDate.plusDays(k.toLong())
            // Day 0 sleeps on the body clock expressed in the new local time, then moves toward 23:00-07:00 local.
            val remaining = (Math.abs(offsetDiff) - adaptPerDayMin * k).coerceAtLeast(0)
            val sign = if (offsetDiff > 0) 1 else -1
            val shiftMin = sign * remaining
            val bed = date.minusDays(1).atTime(23, 0).plusMinutes(shiftMin.toLong() + jitter(r, 10))
            val wake = date.atTime(7, 0).plusMinutes(shiftMin.toLong() + jitter(r, 10))
            away += Scenario.LocalNight(bed, wake, to, id = "t-$date")
        }
        val all = home + away
        return Scenario(
            "travel-${from.id}-${to.id}", to, profile(wake = LocalTime.of(7, 0)),
            flightDate.plusDays(awayDays.toLong()).atTime(12, 0), all,
        )
    }

    fun missingWeek(zone: ZoneId = ZoneId.of("Asia/Kolkata"), days: Int = 42, gapFrom: Int = 14, gapLen: Int = 7): Scenario {
        val ns = nights(zone, UTC_START, days) { d, i ->
            if (i in gapFrom until gapFrom + gapLen) null
            else if (isWorkday(d)) LocalTime.of(23, 0) to LocalTime.of(6, 45) else LocalTime.of(23, 30) to LocalTime.of(8, 0)
        }
        return Scenario("missing-week", zone, profile(), UTC_START.plusDays(days.toLong()).atTime(12, 0), ns)
    }

    /** Steady sleeper straddling a DST change in [zone]; [start] is the first wake date, [now] follows the change. */
    fun dst(zone: ZoneId, start: LocalDate, days: Int = 28, name: String): Scenario {
        val ns = nights(zone, start, days) { d, _ ->
            if (isWorkday(d)) LocalTime.of(23, 0) to LocalTime.of(6, 45) else LocalTime.of(23, 30) to LocalTime.of(8, 0)
        }
        return Scenario(name, zone, profile(), start.plusDays(days.toLong()).atTime(12, 0), ns)
    }

    fun all(): List<Scenario> = listOf(
        steady(), chronicShort(), weekendCatchUp(), fragmentedParent(), rotatingNightShift(),
        travel(ZoneId.of("America/New_York"), ZoneId.of("Europe/Berlin"), adaptPerDayMin = 60),
        travel(ZoneId.of("Europe/Berlin"), ZoneId.of("America/New_York"), adaptPerDayMin = 90),
        missingWeek(),
        dst(ZoneId.of("America/New_York"), LocalDate.of(2026, 2, 23), name = "dst-spring-ny"),
        dst(ZoneId.of("America/New_York"), LocalDate.of(2026, 10, 19), name = "dst-fall-ny"),
    )
}
