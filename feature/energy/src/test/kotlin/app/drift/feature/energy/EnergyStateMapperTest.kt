package app.drift.feature.energy

import app.drift.model.engine.EnergyCurve
import app.drift.model.engine.SufficiencyTier
import app.drift.model.ui.InsufficientReason
import app.drift.model.ui.ScreenState
import app.drift.testing.fixtures.FixtureContext
import app.drift.testing.fixtures.SnapshotFixtures
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.repository.AppSettings
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class EnergyStateMapperTest {
    private val ctx = FixtureContext.IST

    private fun inputs(snapshot: AnalysisSnapshot?, analysisFailed: Boolean = false, refreshFailed: Boolean = false) = EnergyInputs(
        snapshot, analysisFailed, refreshFailed, null, AppSettings(), emptyList(), ctx.now, ctx.zone, null, emptySet(),
    )

    private fun ready(snapshot: AnalysisSnapshot): EnergyUiModel =
        (mapEnergyState(inputs(snapshot)) as ScreenState.Ready).model

    @Test fun noSnapshotYetIsLoadingUntilSomethingFails() {
        assertThat(mapEnergyState(inputs(null))).isEqualTo(ScreenState.Loading)
        assertThat(mapEnergyState(inputs(null, refreshFailed = true))).isEqualTo(ScreenState.Error("", true))
        assertThat(mapEnergyState(inputs(null, analysisFailed = true))).isEqualTo(ScreenState.Error("", true))
    }

    @Test fun everyTierWithACurveIsReady() {
        val byTier = mapOf(
            SufficiencyTier.PRIORS_ONLY to SnapshotFixtures.priorsOnlyDay0(ctx),
            SufficiencyTier.EARLY to SnapshotFixtures.early(ctx),
            SufficiencyTier.PERSONALIZING to SnapshotFixtures.personalizing(ctx),
            SufficiencyTier.FULL to SnapshotFixtures.full(ctx),
        )
        byTier.forEach { (tier, snap) ->
            val model = ready(snap)
            assertThat(model.tier).isEqualTo(tier)
            assertThat(model.priorsOnly).isEqualTo(tier == SufficiencyTier.PRIORS_ONLY)
            assertThat(model.days).isNotEmpty()
        }
    }

    @Test fun unavailableDebtDoesNotHideTheCurve() {
        val model = ready(SnapshotFixtures.debtUnavailable(ctx))
        assertThat(model.days).isNotEmpty()
    }

    @Test fun noCurveAtAllWithNoHistoryIsFirstUse() {
        val snap = SnapshotFixtures.priorsOnlyDay0(ctx).let { it.copy(energyCurve = EnergyCurve(emptyList(), 0)) }
        assertThat(mapEnergyState(inputs(snap))).isEqualTo(ScreenState.FirstUse(1))
    }

    @Test fun noCurveWithHistoryIsInsufficientWithTheRightReason() {
        val early = SnapshotFixtures.early(ctx)
        val few = early.copy(energyCurve = EnergyCurve(emptyList(), 0))
        val state = mapEnergyState(inputs(few)) as ScreenState.Insufficient
        assertThat(state.reason).isEqualTo(InsufficientReason.TOO_FEW_NIGHTS)
        assertThat(state.daysNeeded).isAtLeast(1)

        val missing = few.copy(dataSufficiency = few.dataSufficiency.copy(debtAvailable = false, daysOfHistory = 20))
        assertThat((mapEnergyState(inputs(missing)) as ScreenState.Insufficient).reason)
            .isEqualTo(InsufficientReason.TOO_MANY_MISSING_NIGHTS)
    }

    @Test fun shiftWorkAndTravelFlagsCarryThrough() {
        assertThat(ready(SnapshotFixtures.shiftWork(ctx)).shiftWorkMode).isTrue()
        val travel = ready(SnapshotFixtures.travelEastward(ctx))
        assertThat(travel.reentrainment?.eastward).isTrue()
        assertThat(travel.reentrainment?.estimatedDaysLeft).isEqualTo(3)
    }

    @Test fun dismissedPendingEpisodesAreHidden() {
        val pending = pendingEpisode(ctx)
        val base = inputs(SnapshotFixtures.full(ctx)).copy(pending = pending)
        assertThat((mapEnergyState(base) as ScreenState.Ready).model.pending).isEqualTo(pending)
        val hidden = mapEnergyState(base.copy(dismissedEpisodes = setOf(pending.id))) as ScreenState.Ready
        assertThat(hidden.model.pending).isNull()
    }

    @Test fun clockSettingAndZoneAreCarried() {
        val s = mapEnergyState(inputs(SnapshotFixtures.full(ctx)).copy(settings = AppSettings(use24HourClock = false)))
        val model = (s as ScreenState.Ready).model
        assertThat(model.use24h).isFalse()
        assertThat(model.zone).isEqualTo(ctx.zone)
        assertThat(model.today).isEqualTo(ctx.today)
    }
}
