package app.drift.feature.energy

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.drift.model.CheckIn
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.SufficiencyTier
import app.drift.model.repository.AppSettings
import app.drift.model.ui.ScreenState
import app.drift.testing.fakes.FakeAnalysisRepository
import app.drift.testing.fakes.FakeCheckInRepository
import app.drift.testing.fakes.FakeSettingsRepository
import app.drift.testing.fakes.FakeSleepRepository
import app.drift.testing.fixtures.FixtureContext
import app.drift.testing.fixtures.SnapshotFixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class EnergyViewModelTest {
    private val ctx = FixtureContext.IST
    private val clock = MutableClock(ctx.now, ctx.zone)
    private val sleep = FakeSleepRepository()
    private val settings = FakeSettingsRepository(AppSettings(use24HourClock = true))
    private val checkIns = FakeCheckInRepository()
    private lateinit var analysis: FakeAnalysisRepository

    @BeforeEach fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(snapshot: AnalysisSnapshot? = SnapshotFixtures.full(ctx)): EnergyViewModel {
        analysis = FakeAnalysisRepository(snapshot)
        return EnergyViewModel(analysis, sleep, settings, checkIns, clock)
    }

    private suspend fun ReceiveTurbine<ScreenState<EnergyUiModel>>.awaitNotLoading(): ScreenState<EnergyUiModel> {
        var item = awaitItem()
        while (item is ScreenState.Loading) item = awaitItem()
        return item
    }

    private suspend fun ReceiveTurbine<ScreenState<EnergyUiModel>>.awaitModel(): EnergyUiModel =
        (awaitNotLoading() as ScreenState.Ready).model

    @Test fun refreshesOnOpenWithTheClockTime() = runTest {
        val vm = viewModel()
        vm.state.test { awaitModel() }
        assertThat(analysis.callsTo("refresh")).hasSize(1)
        assertThat(analysis.callsTo("refresh").single().args.single()).isEqualTo(ctx.now)
    }

    @Test fun readyModelCarriesSnapshotSettingsAndZone() = runTest {
        val vm = viewModel()
        vm.state.test {
            val model = awaitModel()
            assertThat(model.zone).isEqualTo(ctx.zone)
            assertThat(model.today).isEqualTo(ctx.today)
            assertThat(model.use24h).isTrue()
            assertThat(model.tier).isEqualTo(SufficiencyTier.FULL)
            assertThat(model.days).hasSize(3)
        }
    }

    @Test fun eachSufficiencyTierMapsToReady() = runTest {
        for (snap in listOf(SnapshotFixtures.priorsOnlyDay0(ctx), SnapshotFixtures.early(ctx), SnapshotFixtures.personalizing(ctx))) {
            val vm = viewModel(snap)
            vm.state.test { assertThat(awaitModel().tier).isEqualTo(snap.dataSufficiency.tier) }
        }
    }

    @Test fun failedRefreshWithNothingCachedIsARetryableError() = runTest {
        val vm = viewModel(snapshot = null)
        vm.state.test {
            val state = awaitNotLoading()
            assertThat(state).isInstanceOf(ScreenState.Error::class.java)
            assertThat((state as ScreenState.Error).retryable).isTrue()
        }
    }

    @Test fun retryRecoversOnceASnapshotExists() = runTest {
        val vm = viewModel(snapshot = null)
        vm.state.test {
            assertThat(awaitNotLoading()).isInstanceOf(ScreenState.Error::class.java)
            analysis.setLatest(SnapshotFixtures.full(ctx))
            awaitItem() // the snapshot arrives, still flagged as failed => Ready already
            vm.retry()
            assertThat(analysis.callsTo("refresh")).hasSize(2)
        }
    }

    @Test fun failedRefreshWithACachedSnapshotKeepsShowingIt() = runTest {
        val vm = run {
            analysis = FakeAnalysisRepository(SnapshotFixtures.full(ctx))
            analysis.failNext("refresh", IOException("boom"))
            EnergyViewModel(analysis, sleep, settings, checkIns, clock)
        }
        vm.state.test { assertThat(awaitNotLoading()).isInstanceOf(ScreenState.Ready::class.java) }
        assertThat(analysis.callsTo("refresh")).hasSize(1)
    }

    @Test fun pendingNightShowsAndConfirmCallsTheRepository() = runTest {
        val pending = pendingEpisode(ctx)
        sleep.setPending(pending)
        val vm = viewModel()
        vm.state.test {
            assertThat(awaitModel().pending).isEqualTo(pending)
            vm.confirm(pending.id)
            assertThat(sleep.callsTo("confirmEpisode").single().args.single()).isEqualTo(pending.id)
            assertThat(expectMostRecentItem().let { (it as ScreenState.Ready).model.pending }).isNull()
        }
    }

    @Test fun failedConfirmRaisesAnActionErrorAndKeepsTheCard() = runTest {
        val pending = pendingEpisode(ctx)
        sleep.setPending(pending)
        sleep.failNext("confirmEpisode", IOException("disk"))
        val vm = viewModel()
        vm.state.test {
            awaitModel()
            vm.confirm(pending.id)
            val model = (expectMostRecentItem() as ScreenState.Ready).model
            assertThat(model.actionError).isEqualTo(EnergyActionError.CONFIRM_FAILED)
            assertThat(model.pending).isEqualTo(pending)
            vm.dismissActionError()
            assertThat((expectMostRecentItem() as ScreenState.Ready).model.actionError).isNull()
        }
    }

    @Test fun adjustedNightIsSavedAsAUserEdit() = runTest {
        val pending = pendingEpisode(ctx)
        sleep.setPending(pending)
        val vm = viewModel()
        vm.state.test {
            awaitModel()
            val newStart = pending.start.minus(Duration.ofMinutes(20))
            val newEnd = pending.end.plus(Duration.ofMinutes(5))
            vm.saveAdjusted(pending, newStart, newEnd)
            val saved = sleep.callsTo("saveUserEpisode").single().args.single() as app.drift.model.SleepEpisode
            assertThat(saved.start).isEqualTo(newStart)
            assertThat(saved.end).isEqualTo(newEnd)
            assertThat(saved.userEdited).isTrue()
            assertThat(saved.id).isEqualTo(pending.id)
            assertThat((expectMostRecentItem() as ScreenState.Ready).model.pending).isNull()
        }
    }

    @Test fun failedSaveRaisesAnActionError() = runTest {
        val pending = pendingEpisode(ctx)
        sleep.setPending(pending)
        sleep.failNext("saveUserEpisode", IOException("full"))
        val vm = viewModel()
        vm.state.test {
            awaitModel()
            vm.saveAdjusted(pending, pending.start, pending.end)
            assertThat((expectMostRecentItem() as ScreenState.Ready).model.actionError).isEqualTo(EnergyActionError.SAVE_FAILED)
        }
    }

    @Test fun checkInIsRecordedAtNowAndHidesThePrompt() = runTest {
        val vm = viewModel()
        vm.state.test {
            assertThat(awaitModel().checkInDue).isTrue()
            vm.checkIn(4)
            assertThat(checkIns.callsTo("add").single().args.single()).isEqualTo(CheckIn.EnergyRating(ctx.now, 4))
            assertThat((expectMostRecentItem() as ScreenState.Ready).model.checkInDue).isFalse()
        }
    }

    @Test fun failedCheckInRaisesAnActionError() = runTest {
        checkIns.failNext("add", IOException("nope"))
        val vm = viewModel()
        vm.state.test {
            awaitModel()
            vm.checkIn(2)
            assertThat((expectMostRecentItem() as ScreenState.Ready).model.actionError).isEqualTo(EnergyActionError.CHECK_IN_FAILED)
        }
    }

    @Test fun nowMarkerTicksEveryMinute() = runTest {
        val vm = viewModel()
        vm.state.test {
            assertThat(awaitModel().now).isEqualTo(ctx.now)
            clock.now = ctx.now.plusSeconds(60)
            advanceTimeBy(60_001)
            assertThat((expectMostRecentItem() as ScreenState.Ready).model.now).isEqualTo(ctx.now.plusSeconds(60))
        }
    }

    @Test fun clockSettingChangesFlowThrough() = runTest {
        val vm = viewModel()
        vm.state.test {
            assertThat(awaitModel().use24h).isTrue()
            settings.set(AppSettings(use24HourClock = false))
            assertThat((expectMostRecentItem() as ScreenState.Ready).model.use24h).isFalse()
        }
    }
}
