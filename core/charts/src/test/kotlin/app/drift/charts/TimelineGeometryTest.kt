package app.drift.charts

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.Locale

class TimelineGeometryTest {
    @Test fun spanCoversLastNightTodayAndTonight() {
        val r = TimelineGeometry.span(Fx.today, Fx.zone)
        assertThat(r.start).isEqualTo(Fx.at(Fx.today.minusDays(1), 18, 0))
        assertThat(r.endInclusive).isEqualTo(Fx.at(Fx.today.plusDays(1), 12, 0))
    }

    @Test fun levelMapsInsideThePlot() {
        assertThat(TimelineGeometry.x(0.0, 0f, 100f)).isWithin(1e-4f).of(36f)
        assertThat(TimelineGeometry.x(100.0, 0f, 100f)).isWithin(1e-4f).of(80f)
        assertThat(TimelineGeometry.x(250.0, 0f, 100f)).isWithin(1e-4f).of(80f)
    }

    @Test fun initialScrollPutsNowAThirdDownAndClamps() {
        val r = Fx.at(Fx.today, 7, 0)..Fx.at(Fx.today, 23, 0)
        val content = 1640f
        assertThat(TimelineGeometry.initialScroll(Fx.at(Fx.today, 12, 0), r, 100f, 20f, 600f, content)).isEqualTo(320)
        assertThat(TimelineGeometry.initialScroll(Fx.at(Fx.today, 7, 30), r, 100f, 20f, 600f, content)).isEqualTo(0)
        assertThat(TimelineGeometry.initialScroll(Fx.at(Fx.today, 22, 59), r, 100f, 20f, 600f, content)).isEqualTo(1040)
        assertThat(TimelineGeometry.initialScroll(null, r, 100f, 20f, 600f, content)).isEqualTo(0)
    }

    @Test fun snapRoundsToFiveMinutes() {
        assertThat(TimelineGeometry.snap(Instant.parse("2026-10-09T01:37:10Z"))).isEqualTo(Instant.parse("2026-10-09T01:35:00Z"))
        assertThat(TimelineGeometry.snap(Instant.parse("2026-10-09T01:37:40Z"))).isEqualTo(Instant.parse("2026-10-09T01:40:00Z"))
    }

    @Test fun movingAnEdgeKeepsTheBlockSaneAndInThePast() {
        val s = Instant.parse("2026-10-09T00:00:00Z")
        val e = Instant.parse("2026-10-09T07:00:00Z")
        val now = Instant.parse("2026-10-09T08:00:00Z")
        // Start moves freely but stays 30 min before the end.
        assertThat(TimelineGeometry.moveEdge(s, e, true, s.minus(Duration.ofMinutes(42)), now)).isEqualTo(s.minus(Duration.ofMinutes(40)) to e)
        assertThat(TimelineGeometry.moveEdge(s, e, true, e, now).first).isEqualTo(e.minus(Duration.ofMinutes(30)))
        // End cannot pass now.
        assertThat(TimelineGeometry.moveEdge(s, e, false, now.plus(Duration.ofHours(2)), now).second).isEqualTo(now)
        // Nor make the night longer than 16 h.
        assertThat(TimelineGeometry.moveEdge(s, e, true, s.minus(Duration.ofHours(20)), now).first).isEqualTo(e.minus(Duration.ofHours(16)))
    }

    @Test fun awakeRunsSkipSleepAndBlocks() {
        val curve = Fx.energyCurve()
        val r = curve.points.first().at..curve.points.last().at
        val block = Fx.at(Fx.today, 13, 0) to Fx.at(Fx.today, 14, 0)
        val runs = TimelineGeometry.awakeRuns(listOf(curve), r, listOf(block))
        assertThat(runs).hasSize(2)
        assertThat(runs[0].last().at).isLessThan(block.first)
        assertThat(runs[1].first().at).isAtLeast(block.second)
    }

    @Test fun debtGaugeScalesToTwelveHoursAndFormats() {
        assertThat(DebtGaugeMath.fraction(Duration.ofHours(6))).isEqualTo(0.5f)
        assertThat(DebtGaugeMath.fraction(Duration.ofHours(20))).isEqualTo(1f)
        assertThat(DebtGaugeMath.fraction(Duration.ofMinutes(-30))).isEqualTo(0f)
        assertThat(DebtGaugeMath.hoursText(Duration.ofMinutes(378), Locale.ENGLISH)).isEqualTo("6.3")
        assertThat(DebtGaugeMath.hoursText(Duration.ZERO, Locale.ENGLISH)).isEqualTo("0.0")
    }

    @Test fun phasePanelsMergeSamePhaseAndAreCutBySleepBlocks() {
        val curve = Fx.energyCurve()
        val r = curve.points.first().at..curve.points.last().at
        val plain = TimelineGeometry.phasePanels(listOf(curve), r, emptyList())
        assertThat(plain.map { it.phase }).containsExactlyElementsIn(curve.segments.map { it.phase }).inOrder()
        // A nap inside the dip splits the dip block in two.
        val nap = Fx.at(Fx.today, 14, 0) to Fx.at(Fx.today, 14, 30)
        val cut = TimelineGeometry.phasePanels(listOf(curve), r, listOf(nap))
        assertThat(cut.count { it.phase == app.drift.model.engine.EnergyPhase.AFTERNOON_DIP }).isEqualTo(2)
        assertThat(cut.none { it.start < nap.second && it.end > nap.first }).isTrue()
    }

    @Test fun smoothingKeepsLengthAndFlattensSpikes() {
        val levels = listOf(10.0, 10.0, 10.0, 90.0, 10.0, 10.0, 10.0)
        val sm = TimelineGeometry.smooth(levels, half = 1)
        assertThat(sm).hasSize(levels.size)
        assertThat(sm[3]).isLessThan(50.0)
        assertThat(TimelineGeometry.smooth(listOf(5.0, 5.0, 5.0))).containsExactly(5.0, 5.0, 5.0)
    }
}
