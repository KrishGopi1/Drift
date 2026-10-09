package app.drift.charts

import app.drift.model.Interval
import app.drift.model.SleepStage
import app.drift.model.StageSpan
import app.drift.model.engine.Reentrainment
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZoneId

class ChartLogicTest {
    private val zone = Fx.zone

    @Test fun energyDescriptionNamesPeaksDipAndWindow() {
        val text = energyCurveDescription(Fx.energyCurve(), zone, true, now = Fx.at(Fx.today, 14, 15))
        assertThat(text).contains("Energy curve for Friday 9 October.")
        assertThat(text).contains("morning peak around 10:30")
        assertThat(text).contains("evening peak around 19:00")
        assertThat(text).contains("afternoon dip around 14:15")
        assertThat(text).contains("Wind-down starts at 21:00.")
        assertThat(text).contains("Melatonin window 21:45 to 23:30.")
        assertThat(text).contains("Now 14:15: level")
        assertThat(text).contains("afternoon dip.")
        assertThat(text).contains("Confidence medium, estimated.")
    }

    @Test fun energyDescriptionUsesTwelveHourClock() {
        assertThat(energyCurveDescription(Fx.energyCurve(), zone, false)).contains("morning peak around 10:30 AM")
    }

    @Test fun flatCurveSaysSoInsteadOfInventingADip() {
        val text = energyCurveDescription(Fx.energyCurve(flat = true), zone, true)
        assertThat(text).contains("too flat today")
        assertThat(text).doesNotContain("Dip:")
    }

    @Test fun miniDescriptionIsShort() {
        val text = miniEnergyDescription(Fx.energyCurve(), zone, true)
        assertThat(text).contains("morning peak 10:30")
        assertThat(text.length).isLessThan(200)
    }

    @Test fun levelInterpolatesBetweenPoints() {
        val c = Fx.energyCurve()
        val a = c.points[10]
        val b = c.points[11]
        val mid = EnergyGeometry.levelAt(c, a.at.plusSeconds(150))!!
        assertThat(mid).isWithin(1e-9).of((a.level + b.level) / 2)
        assertThat(EnergyGeometry.levelAt(c, Fx.at(Fx.today, 3))).isNull()
    }

    @Test fun fractionIsClamped() {
        val r = EnergyGeometry.range(Fx.energyCurve())!!
        assertThat(EnergyGeometry.fraction(r, r.start)).isEqualTo(0f)
        assertThat(EnergyGeometry.fraction(r, r.endInclusive)).isEqualTo(1f)
        assertThat(EnergyGeometry.fraction(r, r.start.minusSeconds(3600))).isEqualTo(0f)
    }

    @Test fun normalizedPointsPutHighEnergyAtTop() {
        val pts = EnergyGeometry.normalizedPoints(Fx.energyCurve(), 100f, 50f)
        assertThat(pts.first().first).isEqualTo(0f)
        assertThat(pts.last().first).isWithin(1e-3f).of(100f)
        val peak = Fx.energyCurve().points.maxByOrNull { it.level }!!
        val i = Fx.energyCurve().points.indexOf(peak)
        assertThat(pts[i].second).isLessThan(pts.maxOf { it.second })
    }

    @Test fun debtDescriptionCountsEverything() {
        val text = debtBarsDescription(Fx.debtDays())
        assertThat(text).contains("over 14 nights")
        assertThat(text).contains("Biggest increase Sun 4 Oct")
        assertThat(text).contains("1 night is estimated.")
        assertThat(text).contains("1 night has no data.")
        assertThat(text).contains("nights added debt")
    }

    @Test fun debtDescriptionEmpty() {
        assertThat(debtBarsDescription(emptyList())).contains("No nights")
    }

    @Test fun timingBarsRespectWakeDateAndGaps() {
        val bars = timingBars(Fx.episodes(days = 7, gapEvery = 3), Fx.today, TimingRange.WEEK)
        assertThat(bars).hasSize(7)
        assertThat(bars.last().date).isEqualTo(Fx.today)
        assertThat(bars.count { it.onsetMinutes == null }).isEqualTo(2)
        val b = bars.last()
        assertThat(b.onsetMinutes!!).isLessThan(0)
        assertThat(b.wakeMinutes!!).isIn(com.google.common.collect.Range.closed(400, 460))
        assertThat(b.durationMinutes!!).isGreaterThan(300)
    }

    @Test fun timingBarsUseTheEpisodeZone() {
        val ny = ZoneId.of("America/New_York")
        val ep = Fx.episodes(days = 1).single().let { it.copy(zone = ny, start = Fx.at(Fx.today.minusDays(1), 23, 0).plusSeconds(4 * 3600), end = Fx.at(Fx.today, 7, 0).plusSeconds(4 * 3600)) }
        val bar = timingBars(listOf(ep), Fx.today, TimingRange.WEEK).last()
        assertThat(bar.onsetMinutes).isEqualTo(-60)
        assertThat(bar.wakeMinutes).isEqualTo(7 * 60 - 0)
    }

    @Test fun timingAxisIsHourAlignedAndAtLeastSixHours() {
        val (lo, hi) = timingAxis(timingBars(Fx.episodes(days = 7), Fx.today, TimingRange.WEEK))
        assertThat(lo % 60).isEqualTo(0)
        assertThat(hi % 60).isEqualTo(0)
        assertThat(hi - lo).isAtLeast(360)
        assertThat(timingAxis(emptyList())).isEqualTo(-120 to 480)
    }

    @Test fun timingDescription() {
        val bars = timingBars(Fx.episodes(days = 30, gapEvery = 10), Fx.today, TimingRange.MONTH)
        val text = sleepTimingDescription(bars, TimingRange.MONTH, true)
        assertThat(text).contains("last month, 27 of 30 nights recorded")
        assertThat(text).contains("Average bedtime 23:")
        assertThat(text).contains("3 nights have no data.")
    }

    @Test fun durationDescriptionCountsShortNights() {
        val bars = timingBars(Fx.episodes(days = 7), Fx.today, TimingRange.WEEK)
        val text = sleepDurationDescription(bars, Duration.ofHours(9))
        assertThat(text).contains("7 of 7 nights were under your sleep need")
    }

    @Test fun stageDescriptionTotalsAndAwakenings() {
        val text = stageDescription(Fx.stages(), zone, true)
        assertThat(text).contains("Sleep stages from 23:10 to ")
        assertThat(text).contains("deep 1 hour 15 minutes")
        assertThat(text).contains("1 awakening during the night.")
        assertThat(text).contains("unknown 15 minutes")
        assertThat(stageDescription(emptyList(), zone, true)).contains("No stage data")
    }

    @Test fun regularityDescriptionDirection() {
        assertThat(regularityTrendDescription(Fx.regularity())).contains("improving")
        val flat = (0 until 10).map { Fx.today.minusDays(it.toLong()) to 80.0 }
        assertThat(regularityTrendDescription(flat)).contains("steady")
        assertThat(regularityTrendDescription(emptyList())).contains("No data")
    }

    @Test fun regularityLineBreaksOnMissingDays() {
        val segs = regularitySegments(Fx.regularity(10, gapAt = setOf(4, 5)))
        assertThat(segs.map { it.size }).containsExactly(4, 4).inOrder()
    }

    @Test fun circadianDescription() {
        val text = circadianDescription(Fx.circadian(), zone, true, Interval(Fx.at(Fx.today, 23, 15), Fx.at(Fx.today.plusDays(1), 6, 45)))
        assertThat(text).contains("Estimated melatonin onset around 21:15 give or take 30 minutes.")
        assertThat(text).contains("Wind-down starts at 20:45.")
        assertThat(text).contains("Melatonin window 21:45 to 23:45.")
        assertThat(text).contains("Planned sleep 23:15 to 06:45.")
    }

    @Test fun circadianDescriptionVariants() {
        assertThat(circadianDescription(Fx.circadianEmpty(), zone, true)).contains("No melatonin estimate yet")
        val travelling = Fx.circadian().copy(reentrainment = Reentrainment(Duration.ofHours(3), eastward = true, estimatedDaysLeft = 2), shiftWorkMode = true)
        val text = circadianDescription(travelling, zone, true)
        assertThat(text).contains("eastward travel, about 2 days left")
        assertThat(text).contains("Shift-work mode is on.")
    }

    @Test fun dialAnglesPutNoonAtTop() {
        assertThat(dialAngle(720f)).isEqualTo(-90f)
        assertThat(dialAngle(0f)).isEqualTo(-270f)
        assertThat(dialAngle(1080f)).isEqualTo(0f)
    }

    @Test fun axisStepGrowsWhenLabelsDoNotFit() {
        assertThat(axisStepMinutes(1020f, 40f, 1000f)).isEqualTo(60)
        assertThat(axisStepMinutes(1020f, 120f, 600f)).isEqualTo(360)
    }
}
