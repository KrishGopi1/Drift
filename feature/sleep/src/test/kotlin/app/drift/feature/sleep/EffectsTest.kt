package app.drift.feature.sleep

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.feature.sleep.bodyclock.BodyClockActions
import app.drift.feature.sleep.bodyclock.BodyClockScreen
import app.drift.feature.sleep.bodyclock.toBodyClockUiModel
import app.drift.feature.sleep.history.HistoryActions
import app.drift.feature.sleep.history.HistoryRange
import app.drift.feature.sleep.history.HistoryScreen
import app.drift.feature.sleep.history.buildHistoryModel
import app.drift.feature.sleep.night.AddEntryActions
import app.drift.feature.sleep.night.AddEntryScreen
import app.drift.feature.sleep.night.AddEntryViewModel
import app.drift.feature.sleep.night.NightDetailActions
import app.drift.feature.sleep.night.NightDetailScreen
import app.drift.feature.sleep.night.NightDetailViewModel
import app.drift.feature.sleep.sleep.SleepActions
import app.drift.feature.sleep.sleep.SleepScreen
import app.drift.feature.sleep.sleep.toSleepUiModel
import app.drift.feature.sleep.why.WhyActions
import app.drift.feature.sleep.why.WhySheetBody
import app.drift.feature.sleep.why.buildWhyModel
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.SourceId
import app.drift.model.engine.MetricKey
import app.drift.model.ui.ScreenState
import app.drift.testing.fakes.FakeSettingsRepository
import app.drift.testing.fakes.FakeSleepRepository
import app.drift.testing.fixtures.FixtureContext
import app.drift.testing.fixtures.SnapshotFixtures
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime

private val ctx = FixtureContext.IST

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class SleepScreenEffectsTest {
    @get:Rule val rule = createComposeRule()

    private val explained = mutableListOf<MetricKey>()
    private val opened = mutableListOf<EpisodeId>()
    private val added = mutableListOf<LocalDate>()
    private var history = 0
    private var bodyClock = 0
    private var retried = 0

    private fun show(state: ScreenState<app.drift.feature.sleep.sleep.SleepUiModel>) = rule.themed {
        SleepScreen(
            state,
            SleepActions(
                onExplain = { explained += it }, onOpenHistory = { history++ }, onOpenBodyClock = { bodyClock++ },
                onOpenNight = { opened += it }, onAddNight = { added += it }, onRetry = { retried++ },
            ),
        )
    }

    private fun ready(snap: app.drift.model.engine.AnalysisSnapshot = SnapshotFixtures.full(ctx), ids: Map<LocalDate, EpisodeId> = emptyMap()) =
        ScreenState.Ready(snap.toSleepUiModel(ctx.zone, true, ids))

    @Test fun debtCardOpensWhySheetForDebt() {
        show(ready())
        rule.onNode(hasClickAction() and hasContentDescription("Sleep debt:", substring = true)).performScrollTo().performClick()
        assertThat(explained).containsExactly(MetricKey.SLEEP_DEBT)
    }

    @Test fun needCardOpensWhySheetForNeed() {
        show(ready())
        rule.onNode(hasClickAction() and hasContentDescription("Sleep need:", substring = true)).performScrollTo().performClick()
        assertThat(explained).containsExactly(MetricKey.SLEEP_NEED)
    }

    @Test fun infoButtonsOpenRegularityAndRecommendation() {
        show(ready())
        rule.onNodeWithContentDescription("Why your regularity changed").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Why these times").performScrollTo().performClick()
        assertThat(explained).containsExactly(MetricKey.REGULARITY, MetricKey.RECOMMENDATION).inOrder()
    }

    @Test fun historyButtonOpensHistory() {
        show(ready())
        rule.onNodeWithText("History").performClick()
        assertThat(history).isEqualTo(1)
    }

    @Test fun regularityNumbersOpenBodyClock() {
        show(ready())
        rule.onNodeWithText("7-day regularity", useUnmergedTree = true).performScrollTo().performClick()
        assertThat(bodyClock).isEqualTo(1)
    }

    @Test fun nightRowOpensThatNight() {
        val id = EpisodeId("last-night")
        show(ready(ids = mapOf(ctx.today to id)))
        rule.onAllNodes(hasClickAction() and hasText("Slept", substring = true)).onFirst().performScrollTo().performClick()
        assertThat(opened).containsExactly(id)
    }

    @Test fun missingNightRowOffersAddANight() {
        show(ready(SnapshotFixtures.debtUnavailable(ctx)))
        rule.onAllNodes(hasClickAction() and hasText("Estimated night", substring = true)).onFirst().performScrollTo().performClick()
        assertThat(added).hasSize(1)
    }

    @Test fun errorRetryCallsBack() {
        show(ScreenState.Error("boom", true))
        rule.onNodeWithText("Try again").performClick()
        assertThat(retried).isEqualTo(1)
    }

    @Test fun tooFewNightsShowsCounterAndNoBars() {
        show(ready(SnapshotFixtures.early(ctx)))
        rule.onNodeWithText("Up adds to debt, down repays it").assertDoesNotExist()

    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class HistoryScreenEffectsTest {
    @get:Rule val rule = createComposeRule()
    private val ranges = mutableListOf<HistoryRange>()
    private val opened = mutableListOf<EpisodeId>()
    private val added = mutableListOf<LocalDate>()
    private var back = 0

    private fun show(range: HistoryRange = HistoryRange.WEEK, gaps: Set<Int> = setOf(2)) {
        val model = buildHistoryModel(range, Eps.history(ctx, 90, gaps), ctx.today, ctx.zone, java.time.Duration.ofHours(8), true)
        rule.themed {
            HistoryScreen(
                ScreenState.Ready(model), ctx.today,
                HistoryActions(onBack = { back++ }, onSelectRange = { ranges += it }, onOpenNight = { opened += it }, onAddNight = { added += it }),
            )
        }
    }

    @Test fun segmentedRangeSelects() {
        show()
        rule.onNodeWithText("Month").performClick()
        rule.onNodeWithText("3 months").performClick()
        assertThat(ranges).containsExactly(HistoryRange.MONTH, HistoryRange.QUARTER).inOrder()
    }

    @Test fun nightRowOpensNight() {
        show()
        rule.onAllNodes(hasClickAction() and hasText("to", substring = true) and hasText("min", substring = true)).onFirst().performScrollTo().performClick()
        assertThat(opened).hasSize(1)
        assertThat(opened.single().value).startsWith("n-")
    }

    @Test fun noDataRowAddsThatNight() {
        show()
        rule.onNode(hasClickAction() and hasText("No data", substring = true)).performScrollTo().performClick()
        assertThat(added).containsExactly(ctx.today.minusDays(2))
    }

    @Test fun topBarAddANightUsesToday() {
        show()
        rule.onAllNodesWithText("Add a night").onFirst().performClick()
        assertThat(added).containsExactly(ctx.today)
    }

    @Test fun backButtonGoesBack() {
        show()
        rule.onNodeWithContentDescription("Back").performClick()
        assertThat(back).isEqualTo(1)
    }

    @Test fun tappingTimingChartOpensANight() {
        show(gaps = emptySet())
        rule.onNode(hasContentDescription("Sleep timing", substring = true)).performScrollTo().performTouchInput { click(centerRight.copy(x = right - 10f)) }
        assertThat(opened.size + added.size).isEqualTo(1)
    }

    @Test fun tappingDurationChartOpensANight() {
        show(gaps = emptySet())
        rule.onNode(hasContentDescription("Sleep duration over", substring = true)).performScrollTo().performTouchInput { click(centerRight.copy(x = right - 10f)) }
        assertThat(opened.size + added.size).isEqualTo(1)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NightDetailEffectsTest {
    @get:Rule val rule = createComposeRule()
    private val sleep = FakeSleepRepository()
    private val settings = FakeSettingsRepository()
    private var back = 0
    private val naps = mutableListOf<LocalDate>()

    private fun launch(episode: app.drift.model.SleepEpisode, source: List<app.drift.model.SleepEpisode> = emptyList()): NightDetailViewModel {
        sleep.setEpisodes(listOf(episode)); sleep.setSourceEpisodes(source)
        val vm = NightDetailViewModel(SavedStateHandle(mapOf(NightDetailViewModel.ARG_EPISODE_ID to episode.id.value)), sleep, settings, ctx.clock, FakeTexts)
        rule.themed {
            val state by vm.state.collectAsState()
            val editor by vm.editor.collectAsState()
            NightDetailScreen(
                state, editor,
                NightDetailActions(
                    onBack = { back++ }, onEdit = vm::startEdit, onCancelEdit = vm::cancelEdit, onSave = vm::save,
                    onStartChange = vm::setStart, onEndChange = vm::setEnd, onAddNap = { naps += it },
                    onFreeDayChange = vm::setFreeDay, onDelete = vm::delete, onUseSource = vm::useSource, onRetry = vm::retry,
                ),
            )
        }
        return vm
    }

    private val night get() = Eps.night("night-1", ctx.today, ctx.zone, stages = true)

    @Test fun editThenSaveWritesUserEpisode() {
        launch(night)
        rule.onNodeWithText("Edit").performClick()
        rule.onNodeWithText("Save").assertIsEnabled()
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()
        assertThat(sleep.callsTo("saveUserEpisode")).hasSize(1)
        assertThat(sleep.episodes.single().userEdited).isTrue()
        rule.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test fun pickingATimeChangesTheDraft() {
        val vm = launch(night)
        rule.onNodeWithText("Edit").performClick()
        rule.onNodeWithText("Bedtime", useUnmergedTree = true).performScrollTo().performClick()
        rule.onNodeWithText("OK").performClick()
        rule.waitForIdle()
        assertThat(vm.editor.value!!.draft.startMinutes).isEqualTo(23 * 60)
        rule.onAllNodesWithText("OK").assertCountEquals(0)
    }

    @Test fun invalidDraftDisablesSave() {
        val vm = launch(night)
        rule.onNodeWithText("Edit").performClick()
        rule.runOnIdle { vm.setStart(7 * 60) }
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNodeWithText("Sleep must be under 20 hours.").assertIsDisplayed()
    }

    @Test fun cancelLeavesEditMode() {
        launch(night)
        rule.onNodeWithText("Edit").performClick()
        rule.onAllNodesWithText("Cancel").onFirst().performClick()
        rule.onNodeWithText("Edit").assertIsDisplayed()
        assertThat(sleep.callsTo("saveUserEpisode")).isEmpty()
    }

    @Test fun addNapPassesTheSleepDay() {
        launch(night)
        rule.onNodeWithText("Add a nap").performScrollTo().performClick()
        assertThat(naps).containsExactly(ctx.today)
    }

    @Test fun freeDaySwitchWritesAnnotation() {
        launch(night)
        rule.onNodeWithText("Mark as free day").performScrollTo().performClick()
        rule.waitForIdle()
        assertThat(sleep.annotations).contains(DayAnnotation(ctx.today, freeDay = true))
    }

    @Test fun deleteNeedsConfirmation() {
        launch(night)
        rule.onNodeWithText("Delete this night").performScrollTo().performClick()
        assertThat(sleep.callsTo("deleteEpisode")).isEmpty()
        rule.onNodeWithText("Delete this night?").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        assertThat(sleep.callsTo("deleteEpisode")).isEmpty()
        rule.onNodeWithText("Delete this night").performScrollTo().performClick()
        rule.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertThat(sleep.callsTo("deleteEpisode").single().args.single()).isEqualTo(EpisodeId("night-1"))
    }

    @Test fun conflictUseThisOneSavesOtherSource() {
        val main = Eps.night("night-1", ctx.today, ctx.zone, flags = setOf(QualityFlag.SOURCE_CONFLICT))
        val other = Eps.night("o", ctx.today, ctx.zone, bed = LocalTime.of(23, 40), tier = QualityTier.PHONE_APP, sourceId = SourceId.SLEEP_AS_ANDROID)
        launch(main, listOf(main, other))
        rule.onNodeWithText("Use this one instead").performScrollTo().performClick()
        rule.waitForIdle()
        assertThat(sleep.callsTo("saveUserEpisode")).hasSize(1)
        assertThat(sleep.episodes.single().start).isEqualTo(other.start)
    }

    @Test fun backGoesBack() {
        launch(night)
        rule.onNodeWithContentDescription("Back").performClick()
        assertThat(back).isEqualTo(1)
    }

    @Test fun errorStateOffersRetry() {
        sleep.setEpisodes(emptyList())
        val vm = NightDetailViewModel(SavedStateHandle(mapOf(NightDetailViewModel.ARG_EPISODE_ID to "gone")), sleep, settings, ctx.clock, FakeTexts)
        rule.themed {
            val state by vm.state.collectAsState()
            NightDetailScreen(state, null, NightDetailActions())
        }
        rule.onNodeWithText("night unavailable").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class AddEntryEffectsTest {
    @get:Rule val rule = createComposeRule()
    private val sleep = FakeSleepRepository()

    private fun launch(kind: EpisodeKind): AddEntryViewModel {
        val vm = AddEntryViewModel(
            SavedStateHandle(mapOf(AddEntryViewModel.ARG_DATE to ctx.today.toString(), AddEntryViewModel.ARG_KIND to kind.name)),
            sleep, FakeSettingsRepository(), ctx.clock, CountingIds(),
        )
        rule.themed {
            val editor by vm.editor.collectAsState()
            AddEntryScreen(kind, editor, true, AddEntryActions(onStartChange = vm::setStart, onEndChange = vm::setEnd, onSave = vm::save))
        }
        return vm
    }

    @Test fun saveAddsANight() {
        launch(EpisodeKind.MAIN)
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()
        assertThat(sleep.callsTo("saveUserEpisode")).hasSize(1)
    }

    @Test fun saveAddsANap() {
        val vm = launch(EpisodeKind.NAP)
        rule.onNodeWithText("Nap starts", useUnmergedTree = true).assertIsDisplayed()
        rule.runOnIdle { vm.setStart(10 * 60); vm.setEnd(11 * 60) }
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()
        assertThat((sleep.callsTo("saveUserEpisode").single().args.single() as app.drift.model.SleepEpisode).kind).isEqualTo(EpisodeKind.NAP)
    }

    @Test fun tooLongCanNotBeSaved() {
        val vm = launch(EpisodeKind.MAIN)
        rule.runOnIdle { vm.setStart(7 * 60) }
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNodeWithText("Save").performClick()
        assertThat(sleep.callsTo("saveUserEpisode")).isEmpty()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class BodyClockAndWhyEffectsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun bodyClockCardsOpenTheirSheets() {
        val explained = mutableListOf<MetricKey>()
        val model = SnapshotFixtures.full(ctx).toBodyClockUiModel(ctx.zone, true, ctx.now)
        rule.themed { BodyClockScreen(ScreenState.Ready(model), BodyClockActions(onExplain = { explained += it })) }
        rule.onNode(hasClickAction() and hasText("Your melatonin window", substring = true)).performScrollTo().performClick()
        rule.onNode(hasClickAction() and hasText("Chronotype", substring = true)).performScrollTo().performClick()
        rule.onNodeWithContentDescription("Why your regularity changed").performScrollTo().performClick()
        assertThat(explained).containsExactly(MetricKey.CIRCADIAN, MetricKey.CHRONOTYPE, MetricKey.REGULARITY).inOrder()
    }

    @Test fun bodyClockShowsReentrainmentBanner() {
        val model = SnapshotFixtures.travelEastward(ctx).toBodyClockUiModel(ctx.zone, true, ctx.now)
        rule.themed { BodyClockScreen(ScreenState.Ready(model), BodyClockActions()) }
        rule.onNodeWithText("Your body clock is catching up after eastward travel").assertIsDisplayed()
    }

    @Test fun whyCauseWithDateOpensNight() {
        val opened = mutableListOf<LocalDate>()
        var dismissed = 0
        val model = buildWhyModel(MetricKey.SLEEP_DEBT, SnapshotFixtures.richExplanation(ctx), SnapshotFixtures.full(ctx))
        rule.themed { WhySheetBody(MetricKey.SLEEP_DEBT, ScreenState.Ready(model), WhyActions(onDismiss = { dismissed++ }, onOpenDate = { opened += it })) }
        rule.onAllNodes(hasClickAction() and hasText("of sleep", substring = true)).onFirst().performClick()
        assertThat(opened).hasSize(1)
        rule.onNodeWithContentDescription("Close").performClick()
        assertThat(dismissed).isEqualTo(1)
    }

    @Test fun whyMethodologyButtonOnlyWhenHostProvidesIt() {
        val asked = mutableListOf<MetricKey>()
        val model = buildWhyModel(MetricKey.SLEEP_DEBT, SnapshotFixtures.richExplanation(ctx), SnapshotFixtures.full(ctx))
        rule.themed { WhySheetBody(MetricKey.SLEEP_DEBT, ScreenState.Ready(model), WhyActions(onOpenMethodology = { asked += it })) }
        rule.onNodeWithText("How this is calculated").performScrollTo().performClick()
        assertThat(asked).containsExactly(MetricKey.SLEEP_DEBT)
    }

    @Test fun whyHidesMethodologyWithoutHost() {
        val model = buildWhyModel(MetricKey.SLEEP_DEBT, SnapshotFixtures.richExplanation(ctx), SnapshotFixtures.full(ctx))
        rule.themed { WhySheetBody(MetricKey.SLEEP_DEBT, ScreenState.Ready(model), WhyActions()) }
        rule.onNodeWithText("How this is calculated").assertDoesNotExist()
    }

    @Test fun whyErrorRetries() {
        var retried = 0
        rule.themed { WhySheetBody(MetricKey.SLEEP_NEED, ScreenState.Error("bad", true), WhyActions(onRetry = { retried++ })) }
        rule.onNodeWithText("Try again").performClick()
        assertThat(retried).isEqualTo(1)
    }

    @Test fun whyFirstDayShowsStartingEstimate() {
        val model = buildWhyModel(MetricKey.SLEEP_NEED, SnapshotFixtures.firstRunExplanation(ctx), null)
        rule.themed { WhySheetBody(MetricKey.SLEEP_NEED, ScreenState.Ready(model), WhyActions()) }
        rule.onNodeWithText("This is your starting estimate", substring = true).assertIsDisplayed()
    }

    @Test fun whyNoChangeShowsNothingChangedYet() {
        val model = buildWhyModel(MetricKey.REGULARITY, SnapshotFixtures.full(ctx), SnapshotFixtures.full(ctx))
        rule.themed { WhySheetBody(MetricKey.REGULARITY, ScreenState.Ready(model), WhyActions()) }
        rule.onNodeWithText("Nothing has changed yet").assertIsDisplayed()
    }
}
