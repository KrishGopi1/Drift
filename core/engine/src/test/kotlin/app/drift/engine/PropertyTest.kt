package app.drift.engine

import app.drift.engine.models.DebtMath
import app.drift.engine.models.DebtModel
import app.drift.engine.models.NeedResult
import app.drift.engine.models.Preprocessor
import app.drift.model.ConfidenceLevel
import app.drift.model.engine.AgeBand
import app.drift.model.engine.AnalysisSnapshot
import com.google.common.truth.Truth.assertThat
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Random

class PropertyTest {
    private val utc = ZoneId.of("UTC")
    private val p = ParameterSet.DEFAULT
    private val seeds = Arb.long(1L..2_000_000L)

    /** A random but deterministic sleeper: variable bedtimes and durations, skipped nights, naps. */
    private fun random(seed: Long, zone: ZoneId = utc, extreme: Boolean = false): Scenario {
        val r = Random(seed)
        val days = 45 + r.nextInt(35)
        val bedBase = 21.0 + r.nextDouble() * 5.0 // 21:00 .. 02:00
        val dur = if (extreme) 2.5 + r.nextDouble() * 12.0 else 5.5 + r.nextDouble() * 4.0
        val weekendExtra = r.nextDouble() * 2.0
        val skip = r.nextDouble() * 0.15
        val age = AgeBand.values()[r.nextInt(5)]
        val nights = ArrayList<Scenario.LocalNight>()
        for (i in 0 until days) {
            if (r.nextDouble() < skip) continue
            val date = Synth.UTC_START.plusDays(i.toLong())
            val weekend = date.dayOfWeek.value >= 6
            val bed = date.minusDays(1).atStartOfDay().plusMinutes(((bedBase + (if (weekend) 0.5 else 0.0)) * 60 + r.nextInt(61) - 30).toLong())
            val len = ((dur + (if (weekend) weekendExtra else 0.0)) * 60 + r.nextInt(61) - 30).toLong().coerceAtLeast(20)
            nights += Scenario.LocalNight(bed, bed.plusMinutes(len), zone, id = "r$i")
            if (r.nextDouble() < 0.15) {
                val ns = date.atTime(13, 30).plusMinutes(r.nextInt(120).toLong())
                nights += Scenario.LocalNight(ns, ns.plusMinutes(20L + r.nextInt(100)), zone, EpisodeKindNap, id = "nap$i")
            }
        }
        return Scenario("random-$seed", zone, Synth.profile(age = age), Synth.UTC_START.plusDays(days.toLong()).atTime(10 + r.nextInt(8), 0), nights)
    }

    private companion object { val EpisodeKindNap = app.drift.model.EpisodeKind.NAP }

    @Test fun `debt is never negative`() = runBlocking<Unit> {
        checkAll(60, seeds) { seed ->
            val snap = Kit.engine.analyze(random(seed, extreme = seed % 2 == 0L).input())
            snap.sleepDebt?.let {
                assertThat(it.value.isNegative).isFalse()
                assertThat(it.confidence!!.lower.isNegative).isFalse()
                assertThat(it.confidence!!.upper).isAtLeast(it.value)
            }
            for (row in snap.debtTrend) assertThat(row.debtAfter.isNegative).isFalse()
        }
    }

    @Test fun `accumulator with a fixed need never lowers debt when sleep is reduced`() = runBlocking<Unit> {
        val lambda = DebtMath.lambda(p)
        fun run(sleeps: List<Double>, need: Double): Double {
            var d = 0.0
            for (s in sleeps) d = DebtMath.step(d, need, s, lambda, 0.6)
            return d
        }
        checkAll(300, Arb.list(Arb.double(200.0, 720.0), 3..20), Arb.double(300.0, 660.0), Arb.long(0L..1000L), Arb.double(0.0, 240.0)) { sleeps, need, pick, cut ->
            val i = (pick % sleeps.size).toInt()
            val less = sleeps.toMutableList().also { it[i] = it[i] - cut }
            assertThat(run(less, need)).isAtLeast(run(sleeps, need) - 1e-9)
            assertThat(run(sleeps, need)).isAtLeast(0.0)
        }
    }

    @Test fun `debt model with a fixed need never lowers debt when a night is shortened`() = runBlocking<Unit> {
        checkAll(60, seeds, Arb.long(0L..1000L), Arb.long(10L..180L)) { seed, pick, cut ->
            val s = random(seed)
            val mains = s.nights.filter { it.kind == app.drift.model.EpisodeKind.MAIN && java.time.Duration.between(it.start, it.end).toMinutes() > cut + 130 }
            if (mains.isEmpty()) return@checkAll
            val victim = mains[(pick % mains.size).toInt()]
            val shorter = Scenario(s.name, s.zone, s.profile, s.nowLocal, s.nights.map { if (it === victim) Scenario.LocalNight(it.start.plusMinutes(cut), it.end, it.zone, id = it.id) else it })
            fun debt(sc: Scenario): Double? {
                val pre = Preprocessor(p).run(sc.input())
                val need = fixedNeed(480.0)
                return DebtModel(p).run(pre, need)?.minutes
            }
            val a = debt(s)
            val b = debt(shorter)
            if (a != null && b != null) assertThat(b).isAtLeast(a - 1e-6)
        }
    }

    private fun fixedNeed(minutes: Double) = NeedResult(
        priorMean = minutes, mean = minutes, sd = 20.0, informativeNights = 10, personal = true, displayed = minutes,
        lower = minutes - 30, upper = minutes + 30, level = ConfidenceLevel.MEDIUM, sources = emptySet(), fittedEta = 0.6, etaPairs = 0,
    )

    @Test fun `engine level debt does not drop when the most recent night is shortened`() = runBlocking<Unit> {
        checkAll(300, seeds, Arb.long(10L..300L)) { seed, cut ->
            val s = random(seed)
            val last = s.nights.filter { it.kind == app.drift.model.EpisodeKind.MAIN }.maxByOrNull { it.end } ?: return@checkAll
            if (java.time.Duration.between(last.start, last.end).toMinutes() < cut + 150) return@checkAll
            val shorter = Scenario(s.name, s.zone, s.profile, s.nowLocal, s.nights.map { if (it === last) Scenario.LocalNight(it.start.plusMinutes(cut), it.end, it.zone, id = it.id) else it })
            val a = Kit.engine.analyze(s.input()).sleepDebt?.value
            val b = Kit.engine.analyze(shorter.input()).sleepDebt?.value
            if (a != null && b != null) assertThat(b.toMinutes()).isAtLeast(a.toMinutes() - 1)
        }
    }

    @Test fun `need stays within physiological bounds for any history`() = runBlocking<Unit> {
        checkAll(80, seeds) { seed ->
            val snap = Kit.engine.analyze(random(seed, extreme = true).input())
            val need = snap.sleepNeed
            assertThat(need.value.toMinutes()).isIn(com.google.common.collect.Range.closed(300L, 660L))
            assertThat(need.confidence!!.lower.toMinutes()).isAtLeast(300L)
            assertThat(need.confidence!!.upper.toMinutes()).isAtMost(660L)
            assertThat(need.confidence!!.lower).isAtMost(need.value)
            assertThat(need.confidence!!.upper).isAtLeast(need.value)
        }
    }

    @Test fun `same input gives the same output`() = runBlocking<Unit> {
        checkAll(40, seeds) { seed ->
            val s = random(seed)
            val a = Kit.engine.analyze(s.input())
            val b = DefaultSleepEngine(failSafe = false).analyze(s.input())
            assertThat(b).isEqualTo(a)
            assertThat(b.inputHash).isEqualTo(a.inputHash)
        }
    }

    /** Everything that should not change when the same local life is lived in another zone. */
    private fun localView(snap: AnalysisSnapshot, zone: ZoneId): List<Any?> = listOf(
        snap.sleepNeed.value, snap.sleepNeed.confidence,
        snap.sleepDebt?.value, snap.sleepDebt?.confidence, snap.debtBand,
        snap.debtTrend.map { listOf(it.date, it.sleep?.value, it.need, it.contribution, it.debtAfter, it.imputed) },
        snap.regularity.sri7?.value, snap.regularity.sri14?.value,
        snap.regularity.onsetSpread?.value, snap.regularity.wakeSpread?.value, snap.regularity.midpointSpread?.value,
        snap.regularity.socialJetlag?.value, snap.regularity.chronotype?.msfscMinutes?.value, snap.regularity.chronotype?.label,
        snap.regularity.trend,
        snap.circadian.dlmoMinutes?.value, snap.circadian.shiftWorkMode, snap.circadian.reentrainment,
        snap.circadian.melatoninWindow?.start?.atZone(zone)?.toLocalDateTime(),
        snap.circadian.windDownStart?.atZone(zone)?.toLocalDateTime(),
        snap.energyCurve.days.map { d ->
            listOf(d.date, d.points.map { it.level }, d.tooFlatForDip, d.segments.map { Triple(it.phase, it.interval.start.atZone(zone).toLocalDateTime(), it.interval.end.atZone(zone).toLocalDateTime()) })
        },
        snap.recommendations.sleepOpportunity.value,
        snap.recommendations.bedtime.value.atZone(zone).toLocalDateTime(),
        snap.recommendations.wakeTime.value.atZone(zone).toLocalDateTime(),
        snap.recommendations.habits.map { Triple(it.kind, it.window.start.atZone(zone).toLocalDateTime(), it.window.end.atZone(zone).toLocalDateTime()) },
        snap.dataSufficiency,
    )

    @Test fun `shifting all data and now into another time zone leaves local time outputs unchanged`() = runBlocking<Unit> {
        val zones = listOf("Asia/Tokyo", "Asia/Kolkata", "America/Phoenix", "Pacific/Honolulu", "Africa/Nairobi").map { ZoneId.of(it) }
        checkAll(30, seeds) { seed ->
            val s = random(seed)
            val base = Kit.engine.analyze(s.input())
            for (z in zones) {
                val shifted = Kit.engine.analyze(s.input(zoneOverride = z))
                assertThat(localView(shifted, z)).isEqualTo(localView(base, utc))
            }
        }
        // And for the shaped scenarios, including a shift worker and a fragmented sleeper.
        for (s in listOf(Synth.steady(), Synth.rotatingNightShift(), Synth.fragmentedParent(), Synth.weekendCatchUp())) {
            val base = Kit.engine.analyze(s.input(zoneOverride = utc))
            val tokyo = Kit.engine.analyze(s.input(zoneOverride = ZoneId.of("Asia/Tokyo")))
            assertThat(localView(tokyo, ZoneId.of("Asia/Tokyo"))).isEqualTo(localView(base, utc))
        }
    }
}
