package app.drift.engine

import app.drift.model.Provenance
import app.drift.model.engine.SufficiencyTier
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneId

class SufficiencyAndParamsTest {
    private val utc = ZoneId.of("UTC")

    private fun tier(days: Int) = Kit.engine.analyze(Synth.steady(utc, days).input()).dataSufficiency

    @Test fun `zero days shows priors only`() {
        val snap = Kit.engine.analyze(Kit.input(emptyList(), "2026-09-14T12:00"))
        val s = snap.dataSufficiency
        assertThat(s.tier).isEqualTo(SufficiencyTier.PRIORS_ONLY)
        assertThat(s.daysOfHistory).isEqualTo(0)
        assertThat(s.nightsUntilPersonalNeed).isEqualTo(5)
        assertThat(s.debtAvailable).isFalse()
        assertThat(s.regularityAvailable).isFalse()
        assertThat(snap.sleepNeed.provenance).isEqualTo(Provenance.ESTIMATED)
        assertThat(snap.sleepNeed.confidence!!.level).isEqualTo(app.drift.model.ConfidenceLevel.LOW)
        assertThat(snap.sleepDebt).isNull()
        assertThat(snap.energyCurve.days).hasSize(3)
        assertThat(snap.recommendations.habits).isNotEmpty()
    }

    @Test fun `tiers follow the days of history`() {
        assertThat(tier(1).tier).isEqualTo(SufficiencyTier.EARLY)
        assertThat(tier(4).tier).isEqualTo(SufficiencyTier.EARLY)
        assertThat(tier(5).tier).isEqualTo(SufficiencyTier.PERSONALIZING)
        assertThat(tier(13).tier).isEqualTo(SufficiencyTier.PERSONALIZING)
        assertThat(tier(14).tier).isEqualTo(SufficiencyTier.FULL)
        assertThat(tier(40).tier).isEqualTo(SufficiencyTier.FULL)
    }

    @Test fun `early tier has debt with a wide band while need is still the prior`() {
        val snap = Kit.engine.analyze(Synth.steady(utc, 3).input())
        val s = snap.dataSufficiency
        assertThat(s.debtAvailable).isTrue()
        assertThat(snap.sleepNeed.value).isEqualTo(java.time.Duration.ofMinutes(480))
        val band = snap.sleepDebt!!.confidence!!
        assertThat((band.upper - band.lower).toMinutes()).isGreaterThan(120)
    }

    @Test fun `regularity becomes available from five paired days`() {
        assertThat(tier(5).regularityAvailable).isFalse()
        assertThat(tier(8).regularityAvailable).isTrue()
    }

    @Test fun `every parameter has a citation and unverified ones say so`() {
        val entries = ParameterSet.DEFAULT.entries
        assertThat(entries.size).isAtLeast(100)
        assertThat(entries.map { it.key }.toSet()).hasSize(entries.size)
        for (e in entries) {
            assertThat(e.citation.authors).isNotEmpty()
            assertThat(e.citation.title).isNotEmpty()
            assertThat(e.description).isNotEmpty()
            if (e.citation.doi == null) assertThat(e.citation.verificationNote).isNotNull()
        }
        assertThat(ParameterSet.DEFAULT.version).isEqualTo(ENGINE_VERSION.toString())
    }

    @Test fun `plan constants have their plan values`() {
        val p = ParameterSet.DEFAULT
        assertThat(p.reboundBeta.v).isEqualTo(0.3)
        assertThat(p.debtEta.v).isEqualTo(0.6)
        assertThat(p.debtHalfLifeDays.v).isEqualTo(7.0)
        assertThat(p.sleepEfficiencyDefault.v).isEqualTo(0.88)
        assertThat(p.mergeGapMin.v).isEqualTo(45.0)
        assertThat(p.debtLowMax.v).isEqualTo(300.0)
        assertThat(p.debtHighMin.v).isEqualTo(600.0)
        assertThat(p.processNoise.v).isEqualTo(10.0)
        assertThat(p.advanceRate.v).isEqualTo(60.0)
        assertThat(p.delayRate.v).isEqualTo(90.0)
    }

    @Test fun `overrides change values and reject unknown keys`() {
        val q = ParameterSet.DEFAULT.withOverrides("need.reboundBeta" to 0.1)
        assertThat(q.reboundBeta.v).isEqualTo(0.1)
        assertThat(ParameterSet.DEFAULT.reboundBeta.v).isEqualTo(0.3)
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> { ParameterSet.DEFAULT.withOverrides("nope" to 1.0) }
    }

    @Test fun `engine reports its version`() {
        assertThat(DefaultSleepEngine().version).isEqualTo(ENGINE_VERSION)
        assertThat(ENGINE_VERSION.toString()).isEqualTo("1.0.0")
        val snap = Kit.engine.analyze(Synth.steady(utc).input())
        assertThat(snap.engineVersion).isEqualTo(ENGINE_VERSION)
        assertThat(snap.inputHash).matches("[0-9a-f]{64}")
    }
}
