package app.drift.feature.sleep

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.feature.sleep.bodyclock.BodyClockActions
import app.drift.feature.sleep.bodyclock.BodyClockScreen
import app.drift.feature.sleep.bodyclock.toBodyClockUiModel
import app.drift.feature.sleep.history.HistoryActions
import app.drift.feature.sleep.history.HistoryRange
import app.drift.feature.sleep.history.HistoryScreen
import app.drift.feature.sleep.history.HistoryUiModel
import app.drift.feature.sleep.history.buildHistoryModel
import app.drift.feature.sleep.night.AddEntryActions
import app.drift.feature.sleep.night.AddEntryScreen
import app.drift.feature.sleep.night.EditorUiState
import app.drift.feature.sleep.night.EpisodeDraft
import app.drift.feature.sleep.night.NightDetailActions
import app.drift.feature.sleep.night.NightDetailScreen
import app.drift.feature.sleep.night.NightDetailUiModel
import app.drift.feature.sleep.night.buildNightDetail
import app.drift.feature.sleep.sleep.SleepActions
import app.drift.feature.sleep.sleep.SleepScreen
import app.drift.feature.sleep.sleep.SleepUiModel
import app.drift.feature.sleep.sleep.toSleepUiModel
import app.drift.feature.sleep.why.WhyActions
import app.drift.feature.sleep.why.WhySheetBody
import app.drift.feature.sleep.why.buildWhyModel
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeKind
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.SourceId
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.MetricKey
import app.drift.model.ui.ScreenState
import app.drift.testing.fixtures.FixtureContext
import app.drift.testing.fixtures.ScreenStateFixtures
import app.drift.testing.fixtures.SnapshotFixtures
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.LocalTime

private val IST = FixtureContext.IST
private val US = FixtureContext.US_EASTERN

/** Every state of a screen, in light/dark at 1x and 2x font. Zone-dependent Ready cases also run in US Eastern. */
abstract class ScreenshotBase {
    @get:Rule val rule = createComposeRule()
}

private const val CFG = "w360dp-h800dp-xhdpi"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = CFG)
class SleepScreenshots : ScreenshotBase() {
    private fun states(name: String, state: ScreenState<SleepUiModel>, ctx: FixtureContext = IST, combos: List<Combo> = Combo.ALL) =
        rule.shots("sleep_$name", ctx, combos) { SleepScreen(state, SleepActions(), scrollable = false) }

    private fun ready(snap: AnalysisSnapshot, ctx: FixtureContext = IST, ids: Boolean = false) = ScreenState.Ready(
        snap.toSleepUiModel(ctx.zone, true, if (ids) snap.debtTrend.associate { it.date to app.drift.model.EpisodeId("e-${it.date}") } else emptyMap()),
    )

    @Test fun loading() = states("loading", ScreenStateFixtures.loading())
    @Test fun firstUse() = states("first_use", ScreenStateFixtures.firstUse())
    @Test fun insufficient() = states("insufficient", ScreenStateFixtures.insufficient(app.drift.model.ui.InsufficientReason.NO_NIGHTS_YET, 5))
    @Test fun sourceProblem() = states("source_problem", ScreenStateFixtures.sourceProblem())
    @Test fun error() = states("error", ScreenStateFixtures.error())
    @Test fun readyIst() = states("ready", ready(SnapshotFixtures.full(IST), ids = true))
    @Test fun readyUs() = states("ready", ready(SnapshotFixtures.full(US), US, ids = true), US, Combo.EDGES)
    @Test fun highDebt() = states("high_debt", ready(SnapshotFixtures.highDebt(IST)), combos = Combo.EDGES)
    @Test fun tooFewNights() = states("too_few_nights", ready(SnapshotFixtures.early(IST)), combos = Combo.EDGES)
    @Test fun tooFewNightsUs() = states("too_few_nights", ready(SnapshotFixtures.early(US), US), US, listOf(Combo.LIGHT_1X))
    @Test fun debtUnavailable() = states("debt_unavailable", ready(SnapshotFixtures.debtUnavailable(IST)), combos = Combo.EDGES)
    @Test fun debtUnavailableUs() = states("debt_unavailable", ready(SnapshotFixtures.debtUnavailable(US), US), US, listOf(Combo.DARK_2X))
    @Test fun tradeOff() = states("trade_off", ready(SnapshotFixtures.withTradeOff(IST)), combos = Combo.EDGES)
    @Test fun tradeOffUs() = states("trade_off", ready(SnapshotFixtures.withTradeOff(US), US), US, listOf(Combo.LIGHT_1X))
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = CFG)
class HistoryScreenshots : ScreenshotBase() {
    private fun states(name: String, state: ScreenState<HistoryUiModel>, ctx: FixtureContext = IST, combos: List<Combo> = Combo.ALL) =
        rule.shots("history_$name", ctx, combos) { HistoryScreen(state, ctx.today, HistoryActions(), scrollable = false) }

    private fun ready(range: HistoryRange, ctx: FixtureContext = IST, gaps: Set<Int> = setOf(2, 3, 9)) = ScreenState.Ready(
        buildHistoryModel(range, Eps.history(ctx, 90, gaps), ctx.today, ctx.zone, Duration.ofMinutes(465), true),
    )

    @Test fun loading() = states("loading", ScreenStateFixtures.loading())
    @Test fun firstUse() = states("first_use", ScreenStateFixtures.firstUse())
    @Test fun insufficient() = states("insufficient", ScreenStateFixtures.insufficient(app.drift.model.ui.InsufficientReason.NO_NIGHTS_YET, 1))
    @Test fun sourceProblem() = states("source_problem", ScreenStateFixtures.sourceProblem())
    @Test fun error() = states("error", ScreenStateFixtures.error())
    @Test fun weekIst() = states("week", ready(HistoryRange.WEEK))
    @Test fun weekUs() = states("week", ready(HistoryRange.WEEK, US), US, Combo.EDGES)
    @Test fun month() = states("month", ready(HistoryRange.MONTH), combos = Combo.EDGES)
    @Test fun quarterUs() = states("quarter", ready(HistoryRange.QUARTER, US), US, listOf(Combo.LIGHT_1X))
    @Test fun sparseHistory() = states("sparse", ScreenState.Ready(
        buildHistoryModel(HistoryRange.MONTH, Eps.history(IST, 90, gaps = (3..89).toSet()), IST.today, IST.zone, null, false),
    ), combos = Combo.EDGES)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = CFG)
class NightDetailScreenshots : ScreenshotBase() {
    private fun model(ctx: FixtureContext, userEdited: Boolean = false, stages: Boolean = true, conflict: Boolean = false, tier: QualityTier = QualityTier.WEARABLE_WRIST): NightDetailUiModel {
        val flags = if (conflict) setOf(QualityFlag.SOURCE_CONFLICT) else emptySet()
        val ep = Eps.night("night-1", ctx.today, ctx.zone, stages = stages, userEdited = userEdited, flags = flags, tier = tier)
        val other = Eps.night("o", ctx.today, ctx.zone, bed = LocalTime.of(23, 35), wake = LocalTime.of(6, 50), tier = QualityTier.PHONE_APP, sourceId = SourceId.SLEEP_AS_ANDROID)
        return buildNightDetail(ep, listOf(ep, other), DayAnnotation(ctx.today, freeDay = true), true)
    }

    private fun states(name: String, state: ScreenState<NightDetailUiModel>, ctx: FixtureContext = IST, editor: EditorUiState? = null, combos: List<Combo> = Combo.ALL) =
        rule.shots("night_$name", ctx, combos) { NightDetailScreen(state, editor, NightDetailActions(), scrollable = false) }

    @Test fun loading() = states("loading", ScreenStateFixtures.loading())
    @Test fun error() = states("error", ScreenStateFixtures.error("This night is no longer available.", false))
    @Test fun withStagesIst() = states("stages", ScreenState.Ready(model(IST)))
    @Test fun withStagesUs() = states("stages", ScreenState.Ready(model(US)), US, combos = Combo.EDGES)
    @Test fun noStagesUserEdited() = states("user_edited", ScreenState.Ready(model(IST, userEdited = true, stages = false)), combos = Combo.EDGES)
    @Test fun phoneInferred() = states("phone_inferred", ScreenState.Ready(model(US, stages = false, tier = QualityTier.PHONE_INFERRED)), US, combos = listOf(Combo.LIGHT_1X))
    @Test fun conflict() = states("conflict", ScreenState.Ready(model(IST, conflict = true)), combos = Combo.EDGES)
    @Test fun conflictUs() = states("conflict", ScreenState.Ready(model(US, conflict = true)), US, combos = listOf(Combo.DARK_2X))
    @Test fun editing() {
        val m = model(IST)
        states("editing", ScreenState.Ready(m), editor = EditorUiState(EpisodeDraft.of(m.episode)), combos = Combo.EDGES)
    }
    @Test fun editingInvalid() {
        val m = model(IST)
        val draft = EpisodeDraft.of(m.episode).withStart(7 * 60)
        states("editing_invalid", ScreenState.Ready(m), editor = EditorUiState(draft, draft.errors(IST.now)), combos = Combo.EDGES)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = CFG)
class AddEntryScreenshots : ScreenshotBase() {
    @Test fun addNight() {
        val d = EpisodeDraft.blank(EpisodeKind.MAIN, IST.today.minusDays(2), IST.zone)
        rule.shots("add_night", IST) { AddEntryScreen(EpisodeKind.MAIN, EditorUiState(d, d.errors(IST.now)), true, AddEntryActions(), scrollable = false) }
    }
    @Test fun addNapInvalid() {
        val d = EpisodeDraft.blank(EpisodeKind.NAP, IST.today, IST.zone).withEnd(13 * 60 + 5)
        rule.shots("add_nap_invalid", US, Combo.EDGES) { AddEntryScreen(EpisodeKind.NAP, EditorUiState(d, d.errors(US.now)), false, AddEntryActions(), scrollable = false) }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = CFG)
class BodyClockScreenshots : ScreenshotBase() {
    private fun states(name: String, state: ScreenState<app.drift.feature.sleep.bodyclock.BodyClockUiModel>, ctx: FixtureContext = IST, combos: List<Combo> = Combo.ALL) =
        rule.shots("body_$name", ctx, combos) { BodyClockScreen(state, BodyClockActions(), scrollable = false) }

    private fun ready(snap: AnalysisSnapshot, ctx: FixtureContext = IST) = ScreenState.Ready(snap.toBodyClockUiModel(ctx.zone, true, ctx.now))

    @Test fun loading() = states("loading", ScreenStateFixtures.loading())
    @Test fun firstUse() = states("first_use", ScreenStateFixtures.firstUse())
    @Test fun insufficient() = states("insufficient", ScreenStateFixtures.insufficient(app.drift.model.ui.InsufficientReason.NO_NIGHTS_YET, 5))
    @Test fun sourceProblem() = states("source_problem", ScreenStateFixtures.sourceProblem())
    @Test fun error() = states("error", ScreenStateFixtures.error())
    @Test fun readyIst() = states("ready", ready(SnapshotFixtures.full(IST)))
    @Test fun readyUs() = states("ready", ready(SnapshotFixtures.full(US), US), US, Combo.EDGES)
    @Test fun notEnoughFreeNights() = states("not_enough_free", ready(SnapshotFixtures.personalizing(IST)), combos = Combo.EDGES)
    @Test fun notEnoughFreeNightsUs() = states("not_enough_free", ready(SnapshotFixtures.personalizing(US), US), US, listOf(Combo.LIGHT_1X))
    @Test fun travelEast() = states("travel_east", ready(SnapshotFixtures.travelEastward(IST)), combos = Combo.EDGES)
    @Test fun shiftWorkUs() = states("shift_work", ready(SnapshotFixtures.shiftWork(US), US), US, listOf(Combo.DARK_2X))
    @Test fun noEstimate() {
        val snap = SnapshotFixtures.priorsOnlyDay0(IST)
        val bare = snap.copy(circadian = snap.circadian.copy(dlmoMinutes = null, melatoninWindow = null, windDownStart = null))
        states("no_estimate", ready(bare), combos = Combo.EDGES)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = CFG)
class WhyScreenshots : ScreenshotBase() {
    private fun sheet(name: String, state: ScreenState<app.drift.feature.sleep.why.WhyUiModel>, metric: MetricKey = MetricKey.SLEEP_DEBT, ctx: FixtureContext = IST, combos: List<Combo> = Combo.ALL) =
        rule.shots("why_$name", ctx, combos) { WhySheetBody(metric, state, WhyActions(onOpenMethodology = {})) }

    @Test fun loading() = sheet("loading", ScreenStateFixtures.loading())
    @Test fun error() = sheet("error", ScreenStateFixtures.error())
    @Test fun changes() = sheet("changes", ScreenState.Ready(buildWhyModel(MetricKey.SLEEP_DEBT, SnapshotFixtures.richExplanation(IST), SnapshotFixtures.full(IST))))
    @Test fun changesUs() = sheet("changes", ScreenState.Ready(buildWhyModel(MetricKey.ENERGY, SnapshotFixtures.richExplanation(US), SnapshotFixtures.full(US))), MetricKey.ENERGY, US, Combo.EDGES)
    @Test fun firstDay() = sheet("first_day", ScreenState.Ready(buildWhyModel(MetricKey.SLEEP_NEED, SnapshotFixtures.firstRunExplanation(IST), null)), MetricKey.SLEEP_NEED)
    @Test fun noPrior() = sheet("no_prior", ScreenState.Ready(buildWhyModel(MetricKey.SLEEP_DEBT, SnapshotFixtures.full(US), null)), MetricKey.SLEEP_DEBT, US, Combo.EDGES)
    @Test fun noChange() = sheet("no_change", ScreenState.Ready(buildWhyModel(MetricKey.REGULARITY, SnapshotFixtures.full(IST), SnapshotFixtures.full(IST))), MetricKey.REGULARITY, combos = Combo.EDGES)
}
