package app.drift.charts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.designsystem.DriftTheme
import app.drift.model.repository.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChartSemanticsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun energyChartExposesItsDescription() {
        rule.setContent { DriftTheme(ThemeMode.DARK, reducedMotion = true) { EnergyCurveChart(Fx.energyCurve(), now = null, zone = Fx.zone) } }
        rule.onNodeWithContentDescription("morning peak around 10:30", substring = true).assertExists()
    }

    @Test fun chartsDrawInAndPulseWithAnimationsOn() {
        rule.mainClock.autoAdvance = false
        rule.setContent { DriftTheme(ThemeMode.DARK, reducedMotion = false) { EnergyCurveChart(Fx.energyCurve(), now = Fx.at(Fx.today, 12), zone = Fx.zone) } }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithContentDescription("Energy curve for", substring = true).assertExists()
        rule.mainClock.advanceTimeBy(2000)
        rule.onNodeWithContentDescription("Now 12:00", substring = true).assertExists()
    }

    @Test fun timingChartReportsTappedNight() {
        val bars = timingBars(Fx.episodes(days = 7), Fx.today, TimingRange.WEEK)
        var tapped: LocalDate? = null
        rule.setContent {
            DriftTheme(ThemeMode.DARK, reducedMotion = true) {
                SleepTimingChart(bars, TimingRange.WEEK, modifier = Modifier.fillMaxWidth(), onNightClick = { tapped = it })
            }
        }
        rule.onNodeWithContentDescription("Sleep timing over the last week", substring = true).performTouchInput { click(Offset(width - 5f, 20f)) }
        assertThat(tapped).isEqualTo(Fx.today)
    }

    @Test fun everyChartHasADescriptionEvenWithNoData() {
        rule.setContent {
            DriftTheme(ThemeMode.DARK, reducedMotion = true) {
                DebtBarsChart(emptyList(), showLegend = false)
                RegularityTrendChart(emptyList())
                StageChart(emptyList())
                CircadianRhythmChart(Fx.circadianEmpty(), zone = Fx.zone)
            }
        }
        rule.onNodeWithContentDescription("Sleep debt chart. No nights to show.").assertExists()
        rule.onNodeWithContentDescription("Sleep regularity chart. No data yet.").assertExists()
        rule.onNodeWithContentDescription("Sleep stage chart. No stage data for this night.").assertExists()
        rule.onNodeWithContentDescription("Body clock dial. No melatonin estimate yet.").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChartSmallHeightTest {
    @get:Rule val rule = createComposeRule()

    @Test fun everyChartSurvivesTinyHeightsAtDoubleFontScale() {
        rule.setContent {
            val base = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, 2f)) {
                DriftTheme(ThemeMode.DARK, reducedMotion = true) {
                    androidx.compose.foundation.layout.Column {
                        for (h in listOf(1, 40, 80, 120)) {
                            val dp = androidx.compose.ui.unit.Dp(h.toFloat())
                            val bars = timingBars(Fx.episodes(days = 7, gapEvery = 3), Fx.today, TimingRange.WEEK)
                            EnergyCurveChart(Fx.energyCurve(), Fx.at(Fx.today, 12), zone = Fx.zone, height = dp)
                            MiniEnergyCurve(Fx.energyCurve(), Fx.at(Fx.today, 12), zone = Fx.zone, height = dp)
                            DebtBarsChart(Fx.debtDays(), height = dp)
                            SleepTimingChart(bars, TimingRange.WEEK, height = dp)
                            SleepDurationChart(bars, TimingRange.WEEK, need = java.time.Duration.ofHours(8), height = dp)
                            StageChart(Fx.stages(), zone = Fx.zone, height = dp)
                            RegularityTrendChart(Fx.regularity(), height = dp)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }
}
