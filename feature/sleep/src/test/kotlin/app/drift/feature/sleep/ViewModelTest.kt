package app.drift.feature.sleep

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.drift.feature.sleep.bodyclock.BodyClockViewModel
import app.drift.feature.sleep.history.HistoryRange
import app.drift.feature.sleep.history.HistoryViewModel
import app.drift.feature.sleep.night.AddEntryViewModel
import app.drift.feature.sleep.night.NightDetailViewModel
import app.drift.feature.sleep.night.NightEvent
import app.drift.feature.sleep.sleep.SleepViewModel
import app.drift.feature.sleep.why.WhyUiModel
import app.drift.feature.sleep.why.WhyVariant
import app.drift.feature.sleep.why.WhyViewModel
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.SourceId
import app.drift.model.engine.MetricKey
import app.drift.model.repository.SourceFix
import app.drift.model.repository.SourceInfo
import app.drift.model.source.SourceStatus
import app.drift.model.ui.InsufficientReason
import app.drift.model.ui.ScreenState
import app.drift.testing.fakes.FakeAnalysisRepository
import app.drift.testing.fakes.FakeSettingsRepository
import app.drift.testing.fakes.FakeSleepRepository
import app.drift.testing.fakes.FakeSourceRepository
import app.drift.testing.fixtures.FixtureContext
import app.drift.testing.fixtures.SnapshotFixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    private val ctx = FixtureContext.IST
    private val analysis = FakeAnalysisRepository(SnapshotFixtures.full(ctx))
    private val sources = FakeSourceRepository()
    private val settings = FakeSettingsRepository()
    private val sleep = FakeSleepRepository(Eps.history(ctx, 20, gaps = setOf(4)))

    @BeforeEach fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    private fun sleepVm() = SleepViewModel(analysis, sources, settings, sleep, ctx.clock, FakeTexts)

    @Test fun sleepGoesLoadingToReadyWithNightIds() = runTest {
        sleepVm().state.test {
            var s = awaitItem()
            if (s is ScreenState.Loading) s = awaitItem()
            val model = (s as ScreenState.Ready).model
            assertThat(model.contributions.first().episodeId).isNotNull()
            assertThat(model.contributions.first { it.date == ctx.today.minusDays(4) }.episodeId).isNull()
        }
    }

    @Test fun sleepWithoutSnapshotIsInsufficientThenSourceProblem() = runTest {
        analysis.setLatest(null)
        val vm = sleepVm()
        vm.state.test {
            var s = awaitItem()
            if (s is ScreenState.Loading) s = awaitItem()
            assertThat(s).isEqualTo(ScreenState.Insufficient(InsufficientReason.NO_NIGHTS_YET, 5))
            val fix = SourceFix.InstallHealthConnect
            sources.setSources(listOf(SourceInfo(SourceId.HEALTH_CONNECT, "Health Connect", SourceStatus.NotInstalled, true, null, null, fix)))
            assertThat(awaitItem()).isEqualTo(ScreenState.SourceProblem(fix, "Health Connect needs attention"))
        }
    }

    @Test fun sleepErrorThenRetry() = runTest {
        sleep.failNext("observeEpisodes", IOException("disk"))
        val vm = sleepVm()
        vm.state.test {
            var s = awaitItem()
            if (s is ScreenState.Loading) s = awaitItem()
            assertThat(s).isEqualTo(ScreenState.Error("load failed", true))
            vm.retry()
            assertThat(awaitItem()).isInstanceOf(ScreenState.Ready::class.java)
        }
    }

    @Test fun sleepReactsToNewSnapshot() = runTest {
        sleepVm().state.test {
            var s = awaitItem()
            if (s is ScreenState.Loading) s = awaitItem()
            analysis.setLatest(SnapshotFixtures.highDebt(ctx))
            val next = awaitItem() as ScreenState.Ready
            assertThat(next.model.debt).isNotEqualTo((s as ScreenState.Ready).model.debt)
        }
    }

    @Test fun historyRangeSwitchesAndEmptyIsInsufficient() = runTest {
        val vm = HistoryViewModel(sleep, analysis, sources, settings, ctx.clock, FakeTexts)
        vm.state.test {
            var s = awaitItem()
            if (s is ScreenState.Loading) s = awaitItem()
            assertThat((s as ScreenState.Ready).model.nights).hasSize(7)
            vm.selectRange(HistoryRange.MONTH)
            assertThat((awaitItem() as ScreenState.Ready).model.nights).hasSize(30)
            sleep.setEpisodes(emptyList())
            assertThat(awaitItem()).isEqualTo(ScreenState.Insufficient(InsufficientReason.NO_NIGHTS_YET, 1))
        }
    }

    @Test fun bodyClockReady() = runTest {
        BodyClockViewModel(analysis, sources, settings, ctx.clock, FakeTexts).state.test {
            var s = awaitItem()
            if (s is ScreenState.Loading) s = awaitItem()
            assertThat(s).isInstanceOf(ScreenState.Ready::class.java)
        }
    }

    private fun nightVm(id: String): NightDetailViewModel =
        NightDetailViewModel(SavedStateHandle(mapOf(NightDetailViewModel.ARG_EPISODE_ID to id)), sleep, settings, ctx.clock, FakeTexts)

    @Test fun nightDetailMissingEpisodeIsError() = runTest {
        nightVm("nope").state.test {
            var s = awaitItem()
            if (s is ScreenState.Loading) s = awaitItem()
            assertThat(s).isEqualTo(ScreenState.Error("night unavailable", false))
        }
    }

    @Test fun editSavesThroughRepositoryAndValidates() = runTest {
        val id = "n-${ctx.today.minusDays(1)}"
        val vm = nightVm(id)
        vm.startEdit()
        assertThat(vm.editor.value).isNotNull()
        vm.setStart(7 * 60 + 5)
        assertThat(vm.editor.value!!.canSave).isFalse()
        vm.save()
        assertThat(sleep.callsTo("saveUserEpisode")).isEmpty()
        vm.setStart(22 * 60)
        vm.save()
        assertThat(sleep.callsTo("saveUserEpisode")).hasSize(1)
        assertThat(vm.editor.value).isNull()
        val saved = sleep.episodes.first { it.id == EpisodeId(id) }
        assertThat(saved.userEdited).isTrue()
        assertThat(saved.start.atZone(ctx.zone).toLocalTime()).isEqualTo(LocalTime.of(22, 0))
    }

    @Test fun saveFailureKeepsEditorOpen() = runTest {
        val vm = nightVm("n-${ctx.today}")
        vm.startEdit()
        sleep.failNext("saveUserEpisode", IOException("full"))
        vm.save()
        assertThat(vm.editor.value!!.saveFailed).isTrue()
        vm.save()
        assertThat(vm.editor.value).isNull()
    }

    @Test fun cancelEditClosesEditor() = runTest {
        val vm = nightVm("n-${ctx.today}")
        vm.startEdit(); vm.cancelEdit()
        assertThat(vm.editor.value).isNull()
        assertThat(sleep.callsTo("saveUserEpisode")).isEmpty()
    }

    @Test fun freeDayWritesAnnotationKeepingOtherFields() = runTest {
        sleep.setAnnotations(listOf(DayAnnotation(ctx.today, alarmSet = true)))
        val vm = nightVm("n-${ctx.today}")
        vm.setFreeDay(ctx.today, true)
        val call = sleep.callsTo("setAnnotation").single().args.single() as DayAnnotation
        assertThat(call).isEqualTo(DayAnnotation(ctx.today, freeDay = true, alarmSet = true))
        vm.setFreeDay(ctx.today.minusDays(1), true)
        assertThat(sleep.callsTo("setAnnotation").last().args.single()).isEqualTo(DayAnnotation(ctx.today.minusDays(1), freeDay = true))
    }

    @Test fun deleteEmitsEventAndCallsRepository() = runTest {
        val id = "n-${ctx.today}"
        val vm = nightVm(id)
        vm.events.test {
            vm.delete()
            assertThat(awaitItem()).isEqualTo(NightEvent.DELETED)
        }
        assertThat(sleep.callsTo("deleteEpisode").single().args.single()).isEqualTo(EpisodeId(id))
    }

    @Test fun useSourceSavesAdoptedEpisode() = runTest {
        val day = ctx.today
        val main = Eps.night("m", day, ctx.zone, flags = setOf(QualityFlag.SOURCE_CONFLICT))
        val other = Eps.night("o", day, ctx.zone, bed = LocalTime.of(23, 30), tier = QualityTier.PHONE_APP, sourceId = SourceId.SLEEP_AS_ANDROID)
        sleep.setEpisodes(listOf(main)); sleep.setSourceEpisodes(listOf(main, other))
        val vm = nightVm("m")
        vm.state.test {
            var s = awaitItem()
            if (s is ScreenState.Loading) s = awaitItem()
            val model = (s as ScreenState.Ready).model
            assertThat(model.conflicts).hasSize(2)
            vm.useSource(model.conflicts.first { !it.used })
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(sleep.episodes.single().start).isEqualTo(other.start)
    }

    @Test fun addNightCreatesUserEpisode() = runTest {
        val ids = CountingIds()
        val vm = AddEntryViewModel(
            SavedStateHandle(mapOf(AddEntryViewModel.ARG_DATE to ctx.today.minusDays(4).toString())), sleep, settings, ctx.clock, ids,
        )
        assertThat(vm.kind).isEqualTo(EpisodeKind.MAIN)
        vm.saved.test {
            vm.save()
            awaitItem()
        }
        val saved = sleep.callsTo("saveUserEpisode").single().args.single() as app.drift.model.SleepEpisode
        assertThat(saved.id.value).isEqualTo("manual-1")
        assertThat(saved.origin.sourceId).isEqualTo(SourceId.USER)
        assertThat(saved.origin.recordId).isEqualTo("manual-1")
        assertThat(saved.origin.qualityTier).isEqualTo(QualityTier.WEARABLE_RING)
        assertThat(saved.userEdited).isTrue()
        assertThat(saved.end.atZone(ctx.zone).toLocalDate()).isEqualTo(ctx.today.minusDays(4))
    }

    @Test fun addNapUsesNapKindAndRejectsInvalid() = runTest {
        val vm = AddEntryViewModel(
            SavedStateHandle(mapOf(AddEntryViewModel.ARG_DATE to ctx.today.minusDays(1).toString(), AddEntryViewModel.ARG_KIND to "NAP")),
            sleep, settings, ctx.clock, CountingIds(),
        )
        vm.setEnd(13 * 60 + 5)
        vm.setStart(13 * 60 + 5)
        assertThat(vm.editor.value.canSave).isFalse()
        vm.save()
        assertThat(sleep.callsTo("saveUserEpisode")).isEmpty()
        vm.setStart(13 * 60)
        vm.setEnd(14 * 60)
        vm.save()
        assertThat((sleep.callsTo("saveUserEpisode").single().args.single() as app.drift.model.SleepEpisode).kind).isEqualTo(EpisodeKind.NAP)
    }

    private fun whyVm(metric: MetricKey) = WhyViewModel(
        SavedStateHandle(mapOf(WhyViewModel.ARG_METRIC to metric.name)), analysis, sleep, ctx.clock, FakeTexts,
    )

    @Test fun whyShowsChangesFirstDayAndNoChange() = runTest {
        analysis.setLatest(SnapshotFixtures.richExplanation(ctx)); analysis.setPrevious(SnapshotFixtures.full(ctx))
        suspend fun variant(m: MetricKey): WhyVariant {
            var out: WhyUiModel? = null
            whyVm(m).state.test {
                var s = awaitItem()
                if (s is ScreenState.Loading) s = awaitItem()
                out = (s as ScreenState.Ready).model
            }
            return out!!.variant
        }
        assertThat(variant(MetricKey.SLEEP_DEBT)).isEqualTo(WhyVariant.CHANGES)
        assertThat(variant(MetricKey.SLEEP_NEED)).isEqualTo(WhyVariant.NO_CHANGE)
        analysis.setPrevious(null)
        assertThat(variant(MetricKey.SLEEP_DEBT)).isEqualTo(WhyVariant.FIRST_DAY)
    }

    @Test fun whyOpensNightOfDatedCause() = runTest {
        val vm = whyVm(MetricKey.SLEEP_DEBT)
        vm.openNight.test {
            vm.openNight(ctx.today.minusDays(2))
            assertThat(awaitItem()).isEqualTo(EpisodeId("n-${ctx.today.minusDays(2)}"))
            vm.openNight(ctx.today.minusDays(60))
            expectNoEvents()
        }
        assertThat(sleep.callsTo("observeEpisodes")).isNotEmpty()
    }

    @Test fun unusedSettingsFlowIsReadable() = runTest {
        assertThat(settings.observeSettings().first().use24HourClock).isNull()
    }
}
