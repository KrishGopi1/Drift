package app.drift.feature.energy.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.designsystem.DriftTheme
import app.drift.feature.energy.EnergyUiModel
import app.drift.feature.energy.EnergyViewModel
import app.drift.feature.energy.MutableClock
import app.drift.feature.energy.pendingEpisode
import app.drift.feature.energy.readyModel
import app.drift.model.CheckIn
import app.drift.model.SleepEpisode
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.MetricKey
import app.drift.model.repository.AppSettings
import app.drift.model.repository.SourceFix
import app.drift.model.ui.ScreenState
import app.drift.testing.fakes.FakeAnalysisRepository
import app.drift.testing.fakes.FakeCheckInRepository
import app.drift.testing.fakes.FakeSettingsRepository
import app.drift.testing.fakes.FakeSleepRepository
import app.drift.testing.fixtures.FixtureContext
import app.drift.testing.fixtures.SnapshotFixtures
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration

/** Every clickable element on the Energy screen, asserting the effect it causes. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h3000dp-xhdpi")
class EnergyInteractionTest {
    @get:Rule val rule = createComposeRule()

    private val ctx = FixtureContext.IST
    private val sleep = FakeSleepRepository()
    private val checkIns = FakeCheckInRepository()
    private val explained = mutableListOf<MetricKey>()
    private val opened = mutableListOf<app.drift.model.EpisodeId>()
    private val fixed = mutableListOf<SourceFix>()
    private var setups = 0
    private var sources = 0
    private var retries = 0

    private fun actions(openSources: Boolean = false) = EnergyActions(
        onExplain = { explained += it },
        onOpenNight = { opened += it },
        onFix = { fixed += it },
        onStartSetup = { setups++ },
        onOpenSources = if (openSources) ({ sources++ }) else null,
        onRetry = { retries++ },
    )

    private fun show(state: ScreenState<EnergyUiModel>, a: EnergyActions = actions(), expanded: Boolean = true) {
        rule.setContent { DriftTheme(reducedMotion = true) { EnergyScreen(state, a, sheetExpanded = expanded) } }
    }

    private fun showReady(
        snap: AnalysisSnapshot = SnapshotFixtures.full(ctx),
        pending: SleepEpisode? = null,
        due: Boolean = false,
        a: EnergyActions = actions(),
        expanded: Boolean = true,
        episodes: List<SleepEpisode> = emptyList(),
    ) = show(ScreenState.Ready(readyModel(snap, ctx, pending, due).copy(episodes = episodes)), a, expanded)

    private var analysis = FakeAnalysisRepository()

    /** Screen backed by the real view model and fakes, to assert repository effects. */
    private fun showWired(pending: SleepEpisode? = null, snap: AnalysisSnapshot = SnapshotFixtures.full(ctx), episodes: List<SleepEpisode> = emptyList()) {
        sleep.setPending(pending)
        sleep.setEpisodes(episodes)
        analysis = FakeAnalysisRepository(snap)
        val vm = EnergyViewModel(
            analysis, sleep, FakeSettingsRepository(AppSettings(use24HourClock = true)),
            checkIns, MutableClock(ctx.now, ctx.zone),
        )
        rule.setContent { DriftTheme(reducedMotion = true) { EnergyRoute(actions(), viewModel = vm) } }
        rule.waitForIdle()
    }

    /** Scrolls a node into view when it sits in a scrolling container (the header and banners do not scroll). */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.reveal() = apply {
        try { performScrollTo() } catch (_: AssertionError) { }
    }

    private fun click(tag: String) = rule.onNodeWithTag(tag).reveal().performClick()
    private fun clickText(text: String) = rule.onNodeWithText(text).reveal().performClick()

    @Test fun confirmCallsConfirmEpisodeAndHidesTheCard() {
        val pending = pendingEpisode(ctx)
        showWired(pending)
        rule.onNodeWithText("Looks like you slept 23:40 to 07:05").assertIsDisplayed()
        clickText("Confirm")
        rule.waitForIdle()
        assertThat(sleep.callsTo("confirmEpisode").map { it.args.single() }).containsExactly(pending.id)
        rule.onNodeWithTag("energy_confirm_card").assertDoesNotExist()
    }

    @Test fun adjustStepButtonThenSaveStoresAUserEdit() {
        val pending = pendingEpisode(ctx)
        showWired(pending)
        clickText("Adjust")
        rule.waitForIdle()
        rule.onNodeWithText("Adjust your sleep").assertIsDisplayed()
        clickText("Start -5 min")
        clickText("End +5 min")
        clickText("Save")
        rule.waitForIdle()
        val saved = sleep.callsTo("saveUserEpisode").single().args.single() as SleepEpisode
        assertThat(saved.start).isEqualTo(pending.start.minus(Duration.ofMinutes(5)))
        assertThat(saved.end).isEqualTo(pending.end.plus(Duration.ofMinutes(5)))
        assertThat(saved.userEdited).isTrue()
        rule.onNodeWithTag("energy_confirm_card").assertDoesNotExist()
    }

    @Test fun draggingTheEndHandleMovesItInFiveMinuteSteps() {
        val pending = pendingEpisode(ctx)
        showWired(pending)
        clickText("Adjust")
        rule.waitForIdle()
        rule.onNodeWithTag("energy_handle_end").performTouchInput { swipeRight(startX = centerX, endX = centerX + 60f) }
        clickText("Save")
        rule.waitForIdle()
        val saved = sleep.callsTo("saveUserEpisode").single().args.single() as SleepEpisode
        assertThat(saved.end).isGreaterThan(pending.end)
        assertThat(saved.end.epochSecond % 300).isEqualTo(0)
        assertThat(saved.start).isEqualTo(pending.start)
    }

    @Test fun cancelInTheSheetSavesNothing() {
        showWired(pendingEpisode(ctx))
        clickText("Adjust")
        rule.waitForIdle()
        clickText("Cancel")
        rule.waitForIdle()
        assertThat(sleep.callsTo("saveUserEpisode")).isEmpty()
        rule.onNodeWithTag("energy_confirm_card").assertIsDisplayed()
    }

    @Test fun notRightOpensTheNight() {
        val pending = pendingEpisode(ctx)
        showReady(pending = pending)
        clickText("Not right")
        assertThat(opened).containsExactly(pending.id)
    }

    @Test fun ratingRecordsAnEnergyCheckIn() {
        showWired()
        click("energy_sheet_header")
        click("energy_rating_4")
        rule.waitForIdle()
        val added = checkIns.callsTo("add").single().args.single() as CheckIn.EnergyRating
        assertThat(added.rating).isEqualTo(4)
        assertThat(added.at).isEqualTo(ctx.now)
        rule.onNodeWithText("Thanks, logged").assertIsDisplayed()
    }

    @Test fun checkInNowRevealsTheRatingRowWhenNotDue() {
        showReady(due = false)
        rule.onNodeWithTag("energy_rating_3").assertDoesNotExist()
        clickText("Check in now")
        rule.onNodeWithTag("energy_rating_3").assertIsDisplayed()
    }

    @Test fun calibrationCountIsShown() {
        showReady(SnapshotFixtures.richExplanation(ctx))
        rule.onNodeWithText("12 check-ins shaped your curve").assertExists()
    }

    @Test fun sheetOpensAndClosesFromItsHeader() {
        showReady(expanded = false)
        rule.onNodeWithTag("energy_bedtime_card").assertDoesNotExist()
        click("energy_sheet_header")
        rule.onNodeWithTag("energy_bedtime_card").assertExists()
        click("energy_sheet_header")
        rule.waitForIdle()
        rule.onNodeWithTag("energy_bedtime_card").assertDoesNotExist()
    }

    @Test fun potentialHeaderAndNextUpRowsExplainEnergy() {
        showReady()
        click("energy_potential")
        click("energy_next_peak")
        click("energy_next_dip")
        assertThat(explained).containsExactly(MetricKey.ENERGY, MetricKey.ENERGY, MetricKey.ENERGY)
    }

    @Test fun draggingLastNightPreviewsThenSavesTheEdit() {
        val night = pendingEpisode(ctx, id = "night-1")
        showWired(episodes = listOf(night))
        rule.onNodeWithTag("sleep_handle_night-1_end").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 120f))
            moveBy(androidx.compose.ui.geometry.Offset(0f, 120f))
            up()
        }
        rule.waitForIdle()
        val previews = analysis.callsTo("preview")
        assertThat(previews).isNotEmpty()
        val saved = sleep.callsTo("saveUserEpisode").single().args.single() as SleepEpisode
        assertThat(saved.id).isEqualTo(night.id)
        assertThat(saved.start).isEqualTo(night.start)
        assertThat(saved.end).isGreaterThan(night.end)
        assertThat(saved.end.epochSecond % 300).isEqualTo(0)
        assertThat(saved.userEdited).isTrue()
        assertThat(analysis.callsTo("refresh")).hasSize(2)
    }

    @Test fun handleAccessibilityActionsMoveFiveMinutesAndSave() {
        val night = pendingEpisode(ctx, id = "night-1")
        val committed = mutableListOf<Pair<java.time.Instant, java.time.Instant>>()
        showReady(episodes = listOf(night), a = EnergyActions(onCommitSleep = { _, s, e -> committed += s to e }))
        val node = rule.onNodeWithTag("sleep_handle_night-1_start").fetchSemanticsNode()
        rule.runOnIdle { node.config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].first { it.label == "5 minutes earlier" }.action() }
        rule.waitForIdle()
        assertThat(committed.single().first).isEqualTo(night.start.minus(Duration.ofMinutes(5)))
    }

    @Test fun flatCurveRowExplainsEnergy() {
        showReady(SnapshotFixtures.flatCurve(ctx))
        rule.onNodeWithText("The curve is too flat to find a dip today").assertExists()
        click("energy_flat_dip")
        assertThat(explained).containsExactly(MetricKey.ENERGY)
    }

    @Test fun bedtimeHabitAndNapCardsExplainTheRecommendation() {
        showReady(SnapshotFixtures.withNap(ctx))
        click("energy_bedtime_card")
        click("energy_nap_card")
        click("energy_habit_CAFFEINE_CUTOFF")
        assertThat(explained).containsExactly(MetricKey.RECOMMENDATION, MetricKey.RECOMMENDATION, MetricKey.RECOMMENDATION)
    }

    @Test fun showAllExpandsTheHabitList() {
        showReady()
        rule.onNodeWithTag("energy_habit_ALCOHOL_CUTOFF").assertDoesNotExist()
        clickText("Show all")
        rule.onNodeWithTag("energy_habit_ALCOHOL_CUTOFF").assertExists()
        clickText("Show fewer")
        rule.onNodeWithTag("energy_habit_ALCOHOL_CUTOFF").assertDoesNotExist()
    }

    @Test fun travelBannerExplainsTheCircadianShift() {
        showReady(SnapshotFixtures.travelEastward(ctx))
        click("energy_travel_banner")
        assertThat(explained).containsExactly(MetricKey.CIRCADIAN)
    }

    @Test fun shiftWorkBannerIsShown() {
        showReady(SnapshotFixtures.shiftWork(ctx))
        rule.onNodeWithText("Shift-work mode: schedule follows your last sleep").assertExists()
    }

    @Test fun tradeOffButtonRevealsTheLaterBedtime() {
        showReady(SnapshotFixtures.withTradeOff(ctx))
        clickText("Show me the later bedtime")
        rule.onNodeWithText("Show me the later bedtime").assertDoesNotExist()
        rule.onNodeWithText("Following the melatonin window", substring = true).assertExists()
    }

    @Test fun improveItOpensSourcesWhenProvidedElseExplainsEnergy() {
        showReady(SnapshotFixtures.priorsOnlyDay0(ctx), a = actions(openSources = true))
        clickText("Improve it")
        assertThat(sources).isEqualTo(1)
    }

    @Test fun improveItFallsBackToTheEnergyExplanation() {
        showReady(SnapshotFixtures.priorsOnlyDay0(ctx))
        clickText("Improve it")
        assertThat(explained).containsExactly(MetricKey.ENERGY)
    }

    @Test fun fixButtonCallsOnFix() {
        val fix = SourceFix.GrantUsageAccess
        show(ScreenState.SourceProblem(fix, "Drift cannot read sleep yet."))
        rule.onNodeWithText("Drift cannot read sleep yet.").assertIsDisplayed()
        rule.onNodeWithText("Allow usage access").performClick()
        assertThat(fixed).containsExactly(fix)
    }

    @Test fun tryAgainCallsRetryAndBlankErrorGetsDefaultText() {
        show(ScreenState.Error("", retryable = true))
        rule.onNodeWithText("Drift could not load your energy curve.").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()
        assertThat(retries).isEqualTo(1)
    }

    @Test fun firstUseContinueStartsSetup() {
        show(ScreenState.FirstUse(2))
        rule.onNodeWithText("Continue setup").performClick()
        assertThat(setups).isEqualTo(1)
    }

    @Test fun failedConfirmShowsADismissibleError() {
        sleep.failNext("confirmEpisode", java.io.IOException("x"))
        showWired(pendingEpisode(ctx))
        clickText("Confirm")
        rule.waitForIdle()
        rule.onNodeWithText("Could not confirm that night. Try again.").assertIsDisplayed()
        clickText("Dismiss")
        rule.waitForIdle()
        rule.onNodeWithTag("energy_action_error").assertDoesNotExist()
    }
}
