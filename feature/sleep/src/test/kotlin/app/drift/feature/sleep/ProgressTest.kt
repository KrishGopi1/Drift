package app.drift.feature.sleep

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.feature.sleep.progress.ProgressActions
import app.drift.feature.sleep.progress.ProgressScreen
import app.drift.feature.sleep.progress.ProgressTab
import app.drift.feature.sleep.progress.ProgressUiModel
import app.drift.feature.sleep.progress.buildProgressModel
import app.drift.model.CheckIn
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.engine.MetricKey
import app.drift.model.ui.ScreenState
import app.drift.testing.fixtures.FixtureContext
import app.drift.testing.fixtures.SnapshotFixtures
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

private val c = FixtureContext.IST

private fun model(gaps: Set<Int> = setOf(3), ratings: List<CheckIn> = emptyList()): ProgressUiModel {
    val eps = Eps.history(c, 14, gaps) + Eps.night("nap", c.today.minusDays(1), c.zone, LocalTime.of(14, 0), LocalTime.of(14, 30), kind = EpisodeKind.NAP)
    return buildProgressModel(c.today, c.zone, eps, SnapshotFixtures.full(c), ratings, true)
}

class ProgressMappingTest {
    @Test fun fourteenDaysEndingTodayWithEpisodesByWakeDate() {
        val m = model()
        assertThat(m.nights).hasSize(14)
        assertThat(m.windowEnd).isEqualTo(c.today)
        assertThat(m.nights.first { it.date == c.today.minusDays(3) }.main).isNull()
        val yesterday = m.nights.first { it.date == c.today.minusDays(1) }
        assertThat(yesterday.episodes).hasSize(2)
        assertThat(yesterday.main!!.kind).isEqualTo(EpisodeKind.MAIN)
        val expected = yesterday.episodes.fold(Duration.ZERO) { acc, e -> acc + Duration.between(e.start, e.end) }
        assertThat(yesterday.asleep).isEqualTo(expected)
        assertThat(yesterday.asleep).isGreaterThan(Duration.between(yesterday.main!!.start, yesterday.main!!.end))
    }

    @Test fun spansAreMinutesAfterSixInTheEvening() {
        val n = model().nights.last()
        val main = n.main!!
        val origin = c.today.minusDays(1).atTime(18, 0).atZone(c.zone).toInstant()
        val expected = Duration.between(origin, main.start).toMinutes().toInt() to Duration.between(origin, main.end).toMinutes().toInt()
        assertThat(n.spans(c.zone)).containsExactly(expected)
        assertThat(expected.first).isLessThan(expected.second)
    }

    @Test fun latestRatingWinsAndLastNightIsPromptedUntilRated() {
        val night = c.today
        assertThat(model().unratedLastNight).isEqualTo(night)
        val rated = model(ratings = listOf(CheckIn.SleepRating(c.now.minusSeconds(60), night, 2), CheckIn.SleepRating(c.now, night, 4)))
        assertThat(rated.ratings[night]).isEqualTo(4)
        assertThat(rated.unratedLastNight).isNull()
    }

    @Test fun snapshotSuppliesDebtAndCatchUp() {
        val m = model()
        assertThat(m.debt).isNotEmpty()
        assertThat(m.catchUp).isNotNull()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h2400dp-xhdpi")
class ProgressScreenTest {
    @get:Rule val rule = createComposeRule()
    private val opened = mutableListOf<EpisodeId>()
    private val added = mutableListOf<LocalDate>()
    private val rated = mutableListOf<Pair<LocalDate, Int>>()
    private val explained = mutableListOf<MetricKey>()
    private var history = 0

    private fun show(tab: ProgressTab = ProgressTab.TIMES, m: ProgressUiModel = model()) = rule.themed {
        ProgressScreen(
            ScreenState.Ready(m),
            ProgressActions(
                onOpenNight = { opened += it }, onAddNight = { added += it }, onRate = { d, r -> rated += d to r },
                onExplain = { explained += it }, onOpenHistory = { history++ },
            ),
            initialTab = tab,
        )
    }

    @Test fun columnsOpenNightsOrAddMissingOnes() {
        show()
        rule.onNodeWithTag("sleep_column_${c.today}").performClick()
        rule.onNodeWithTag("sleep_column_${c.today.minusDays(3)}").performScrollTo().performClick()
        assertThat(opened).hasSize(1)
        assertThat(added).containsExactly(c.today.minusDays(3))
    }

    @Test fun nightRowsAndHistoryLink() {
        show()
        rule.onNodeWithTag("progress_night_${c.today}").performScrollTo().performClick()
        rule.onNodeWithText("See longer history").performScrollTo().performClick()
        assertThat(opened).hasSize(1)
        assertThat(history).isEqualTo(1)
    }

    @Test fun catchUpCardExplainsDebt() {
        show()
        rule.onNodeWithTag("progress_catchup").performScrollTo().performClick()
        assertThat(explained).containsExactly(MetricKey.SLEEP_DEBT)
    }

    @Test fun switchingTabsShowsDebtThenQuality() {
        show()
        rule.onNodeWithText("Debt").performClick()
        rule.onNodeWithText("All sleep debt").assertExists()
        rule.onNodeWithText("Quality").performClick()
        rule.onNodeWithText("How did you sleep last night?").assertExists()
    }

    @Test fun ratingLastNightCallsBack() {
        show(ProgressTab.QUALITY)
        rule.onNodeWithTag("progress_rate_4").performClick()
        assertThat(rated).containsExactly(c.today to 4)
    }

    @Test fun ratedNightsShowTheirWord() {
        show(ProgressTab.QUALITY, model(ratings = listOf(CheckIn.SleepRating(c.now, c.today, 5))))
        rule.onNodeWithText("How did you sleep last night?").assertDoesNotExist()
        rule.onNodeWithContentDescription("${c.today.minusDays(1).dayOfWeek.name.take(3)}: Great", substring = true, ignoreCase = true).assertExists()
    }
}
