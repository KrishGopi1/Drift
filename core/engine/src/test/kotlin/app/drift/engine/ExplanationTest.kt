package app.drift.engine

import app.drift.model.engine.CauseKind
import app.drift.model.engine.EngineState
import app.drift.model.engine.EngineVersion
import app.drift.model.engine.MetricKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ExplanationTest {
    private val utc = ZoneId.of("UTC")
    private val s = Synth.weekendCatchUp(utc)

    private fun run(day: Int, prior: EngineState?, scen: Scenario = s) =
        Kit.engine.analyze(scen.input(prior = prior, nowOverride = LocalDateTime.of(2026, 9, day, 12, 0)))

    @Test fun `first run reports FIRST_RUN for every headline`() {
        val a = run(10, null)
        assertThat(a.explanations).isNotEmpty()
        assertThat(a.explanations.flatMap { it.causes }.map { it.kind }.toSet()).containsExactly(CauseKind.FIRST_RUN)
        assertThat(a.explanations.map { it.metric }).containsAtLeast(MetricKey.SLEEP_NEED, MetricKey.SLEEP_DEBT, MetricKey.CIRCADIAN, MetricKey.ENERGY)
    }

    @Test fun `state stores headline numbers and per night contributions`() {
        val a = run(10, null)
        val v = a.state.values
        assertThat(v).containsKey("prev.debtMinutes")
        assertThat(v).containsKey("prev.needMinutes")
        assertThat(v.keys.count { it.startsWith("contrib.") }).isEqualTo(14)
        assertThat(v["contrib.${LocalDate.of(2026, 9, 9).toEpochDay()}"]).isNotNull()
        assertThat(a.state.engineVersion).isEqualTo(a.engineVersion)
    }

    @Test fun `next day attributes the debt change to the entering and leaving nights`() {
        val a = run(10, null)
        val b = run(11, a.state)
        val debt = b.explanations.first { it.metric == MetricKey.SLEEP_DEBT }
        val kinds = debt.causes.map { it.kind }
        assertThat(kinds).contains(CauseKind.NIGHT_ENTERED_WINDOW)
        assertThat(kinds).contains(CauseKind.NIGHT_LEFT_WINDOW)
        assertThat(debt.causes.first { it.kind == CauseKind.NIGHT_ENTERED_WINDOW && it.date != null }.date).isEqualTo(LocalDate.of(2026, 9, 11))
        assertThat(debt.causes.first { it.kind == CauseKind.NIGHT_LEFT_WINDOW && it.date != null }.date).isEqualTo(LocalDate.of(2026, 8, 28))
        assertThat(debt.headline).matches("Debt (fell|rose) .*")
        assertThat(debt.headline).contains("dropped out of your 14-day window")
        // The attributed deltas add up to the displayed change (minutes).
        val total = (b.sleepDebt!!.value.toMillis() - a.sleepDebt!!.value.toMillis()) / 60000.0
        assertThat(debt.causes.mapNotNull { it.delta }.sum()).isWithin(1.0).of(total)
    }

    @Test fun `an edited night is attributed to the user edit`() {
        val a = run(11, null)
        val night = LocalDate.of(2026, 9, 8)
        val edited = Scenario(
            "edited", utc, s.profile, s.nowLocal,
            s.nights.map { n -> if (n.id == "n-$night") Scenario.LocalNight(n.start.plusHours(2), n.end, utc, id = n.id) else n },
        )
        val input = edited.input(prior = a.state, nowOverride = LocalDateTime.of(2026, 9, 11, 12, 0))
        val marked = input.copy(episodes = input.episodes.map { e -> if (e.id.value == "n-$night") e.copy(userEdited = true) else e })
        val b = Kit.engine.analyze(marked)
        val debt = b.explanations.first { it.metric == MetricKey.SLEEP_DEBT }
        val cause = debt.causes.first { it.kind == CauseKind.USER_EDIT }
        assertThat(cause.date).isEqualTo(night)
        assertThat(cause.delta!!).isGreaterThan(0.0)
    }

    @Test fun `identical input produces no debt explanation`() {
        val a = run(11, null)
        val b = run(11, a.state)
        assertThat(b.explanations.none { it.metric == MetricKey.SLEEP_DEBT }).isTrue()
        assertThat(b.explanations.none { it.metric == MetricKey.SLEEP_NEED }).isTrue()
    }

    @Test fun `moving from the prior to a personal need is explained`() {
        val few = Synth.steady(utc, days = 14)
        val a = Kit.engine.analyze(few.input())
        val more = Synth.steady(utc, days = 21)
        val b = Kit.engine.analyze(more.input(prior = a.state))
        val need = b.explanations.first { it.metric == MetricKey.SLEEP_NEED }
        assertThat(need.causes.single().kind).isEqualTo(CauseKind.NEED_REESTIMATED)
        assertThat(need.headline).contains("population")
    }

    @Test fun `check-in calibration and circadian shifts are reported`() {
        val base = Synth.steady(utc)
        val a = Kit.engine.analyze(base.input())
        val withCheckIns = Scenario(
            base.name, base.zone, base.profile, base.nowLocal, base.nights,
            checkIns = (0 until 12).map { base.nowLocal.minusDays(1L + it).withHour(16) to 2 },
        )
        val b = Kit.engine.analyze(withCheckIns.input(prior = a.state))
        assertThat(b.explanations.first { it.metric == MetricKey.ENERGY }.causes.single().kind).isEqualTo(CauseKind.CHECK_IN_CALIBRATION)
        val late = Synth.travel(ZoneId.of("America/New_York"), ZoneId.of("Europe/Berlin"), awayDays = 3, adaptPerDayMin = 60)
        val c = Kit.engine.analyze(late.input(nowOverride = Synth.UTC_START.plusDays(28).atTime(12, 0)))
        val d = Kit.engine.analyze(late.input(prior = c.state, nowOverride = Synth.UTC_START.plusDays(30).atTime(12, 0)))
        assertThat(d.explanations.first { it.metric == MetricKey.CIRCADIAN }.causes.single().kind).isEqualTo(CauseKind.CIRCADIAN_SHIFT)
    }

    @Test fun `state from an incompatible engine version is treated as a first run`() {
        val a = run(10, null)
        val old = a.state.copy(engineVersion = EngineVersion(0, 9, 0))
        val b = run(11, old)
        assertThat(b.explanations.flatMap { it.causes }.map { it.kind }.toSet()).containsExactly(CauseKind.FIRST_RUN)
    }

    @Test fun `source added is reported`() {
        val a = run(11, null)
        val input = s.input(prior = a.state, nowOverride = LocalDateTime.of(2026, 9, 11, 12, 0))
        val extra = input.episodes.first().let { e -> e.copy(id = app.drift.model.EpisodeId("x"), origin = e.origin.copy(sourceId = app.drift.model.SourceId.PHONE)) }
        val b = Kit.engine.analyze(input.copy(episodes = input.episodes + extra))
        assertThat(b.explanations.flatMap { it.causes }.map { it.kind }).contains(CauseKind.SOURCE_ADDED)
    }
}
