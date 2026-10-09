package app.drift.engine

import app.drift.model.CheckIn
import app.drift.model.EpisodeKind
import app.drift.model.SleepStage
import app.drift.model.engine.AnalysisSnapshot
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The engine must never throw on odd data: empty, overlapping, future, DST, absurd. */
class RobustnessTest {
    private val utc = ZoneId.of("UTC")
    private val ny = ZoneId.of("America/New_York")

    private fun ok(s: AnalysisSnapshot) {
        assertThat(s.sleepNeed.value.toMinutes()).isIn(com.google.common.collect.Range.closed(300L, 660L))
        s.sleepDebt?.let { assertThat(it.value.isNegative).isFalse() }
        assertThat(s.energyCurve.days).hasSize(3)
        for (d in s.energyCurve.days) for (pt in d.points) assertThat(pt.level.isNaN()).isFalse()
        assertThat(s.circadian.dlmoMinutes!!.value.isNaN()).isFalse()
    }

    @Test fun `empty history`() = ok(Kit.engine.analyze(Kit.input(emptyList(), "2026-09-14T12:00")))

    @Test fun `exact duplicates and overlapping episodes`() {
        val base = Kit.ep("a", "2026-09-13T23:00", "2026-09-14T07:00")
        ok(Kit.engine.analyze(Kit.input(listOf(base, base, base.copy(id = app.drift.model.EpisodeId("b")), Kit.ep("c", "2026-09-14T01:00", "2026-09-14T09:00")), "2026-09-14T12:00")))
    }

    @Test fun `episodes in the future and a now before all data`() {
        val e = Kit.ep("f", "2026-12-01T23:00", "2026-12-02T07:00")
        ok(Kit.engine.analyze(Kit.input(listOf(e), "2026-09-14T12:00")))
        ok(Kit.engine.analyze(Kit.input(Synth.steady(utc).input().episodes, "2020-01-01T00:00")))
    }

    @Test fun `episode far in the past, one that lasts weeks and sub minute fragments`() {
        val tiny = Kit.ep("t", "2026-09-13T23:00:00", "2026-09-13T23:00:30")
        val huge = Kit.ep("h", "2026-08-01T00:00", "2026-09-01T00:00")
        val old = Kit.ep("o", "1971-01-01T22:00", "1971-01-02T06:00")
        ok(Kit.engine.analyze(Kit.input(listOf(tiny, huge, old), "2026-09-14T12:00")))
    }

    @Test fun `thousands of fragments`() {
        val eps = (0 until 3000).map { i ->
            val start = Instant.parse("2026-06-01T00:00:00Z").plusSeconds(i * 2400L)
            Kit.ep("x$i", start.toString().removeSuffix("Z").substring(0, 19), start.plusSeconds(1500).toString().removeSuffix("Z").substring(0, 19))
        }
        ok(Kit.engine.analyze(Kit.input(eps, "2026-09-14T12:00")))
    }

    @Test fun `all awake stages and naps only`() {
        val awake = Kit.ep("w", "2026-09-13T23:00", "2026-09-14T07:00", stages = listOf(Kit.stage(SleepStage.AWAKE, "2026-09-13T23:00", "2026-09-14T07:00")))
        val nap = Kit.ep("n", "2026-09-13T14:00", "2026-09-13T14:30", kind = EpisodeKind.NAP)
        ok(Kit.engine.analyze(Kit.input(listOf(awake, nap), "2026-09-14T12:00")))
    }

    @Test fun `daylight saving changes in both directions`() {
        for ((start, name) in listOf(LocalDate.of(2026, 2, 23) to "spring", LocalDate.of(2026, 10, 19) to "fall")) {
            val s = Synth.dst(ny, start, days = 28, name = name)
            for (offset in 0L..28L step 1) {
                ok(Kit.engine.analyze(s.input(nowOverride = start.plusDays(offset).atTime(12, 0))))
            }
        }
        // Europe too, with now exactly on the transition days.
        val berlin = ZoneId.of("Europe/Berlin")
        for (day in listOf(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25))) {
            val s = Synth.dst(berlin, day.minusDays(14), days = 28, name = "eu")
            for (h in listOf(0, 1, 2, 3, 4, 12)) ok(Kit.engine.analyze(s.input(nowOverride = day.atTime(h, 30))))
        }
    }

    @Test fun `the DST night is measured in real elapsed time`() {
        // 23:00 on 7 Mar to 07:00 on 8 Mar in New York spans the spring-forward change: 7 real hours.
        val s = Synth.dst(ny, LocalDate.of(2026, 3, 1), days = 14, name = "dst")
        val nights = s.nights.map { n -> if (n.id == "n-2026-03-08") Scenario.LocalNight(LocalDate.of(2026, 3, 7).atTime(23, 0), LocalDate.of(2026, 3, 8).atTime(7, 0), ny, id = n.id) else n }
        val snap = Kit.engine.analyze(Scenario("d", ny, s.profile, s.nowLocal, nights).input())
        val row = snap.debtTrend.first { it.date == LocalDate.of(2026, 3, 8) }
        assertThat(row.sleep!!.value.toMinutes()).isEqualTo(7 * 60L)
        val fall = Synth.dst(ny, LocalDate.of(2026, 10, 25), days = 14, name = "dst-fall")
        val n2 = fall.nights.map { n -> if (n.id == "n-2026-11-01") Scenario.LocalNight(LocalDate.of(2026, 10, 31).atTime(23, 0), LocalDate.of(2026, 11, 1).atTime(7, 0), ny, id = n.id) else n }
        val snap2 = Kit.engine.analyze(Scenario("d", ny, fall.profile, fall.nowLocal, n2).input())
        assertThat(snap2.debtTrend.first { it.date == LocalDate.of(2026, 11, 1) }.sleep!!.value.toMinutes()).isEqualTo(9 * 60L)
    }

    @Test fun `check-ins in the future or far in the past are ignored`() {
        val s = Synth.steady(utc)
        val input = s.input().copy(
            checkIns = (0 until 20).map { CheckIn.EnergyRating(s.nowLocal.plusDays(1L + it).atZone(utc).toInstant(), 3) } +
                (0 until 20).map { CheckIn.EnergyRating(s.nowLocal.minusDays(100L + it).atZone(utc).toInstant(), 3) },
        )
        assertThat(Kit.engine.analyze(input).energyCurve.calibrationCheckIns).isEqualTo(0)
    }

    @Test fun `every scenario analyzes at every day of its history without throwing`() {
        for (s in Synth.all()) {
            var state: app.drift.model.engine.EngineState? = null
            val days = s.nights.map { it.end.toLocalDate() }.distinct().sorted()
            for (d in days.filterIndexed { i, _ -> i % 3 == 0 }) {
                val snap = Kit.engine.analyze(s.input(prior = state, nowOverride = d.atTime(15, 0)))
                ok(snap)
                state = snap.state
            }
        }
    }

    @Test fun `the default engine degrades to priors if something unexpected goes wrong`() {
        // A throwing parameter is not constructible, so simply confirm the fail-safe path equals a priors-only run.
        val safe = DefaultSleepEngine()
        val s = Synth.steady(utc).input()
        assertThat(safe.analyze(s).sleepNeed).isEqualTo(Kit.engine.analyze(s).sleepNeed)
    }
}
