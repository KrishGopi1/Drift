package app.drift.charts

import app.drift.model.Interval
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.EnergySegment
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Locale

class TimelineGeometryTest {
    @Test fun wakingRangeIsWholeCurveWithoutSleepSegments() {
        val curve = Fx.energyCurve()
        assertThat(TimelineGeometry.wakingRange(curve)).isEqualTo(EnergyGeometry.range(curve))
    }

    @Test fun wakingRangeStopsAtSleepOnBothSides() {
        val base = Fx.energyCurve()
        val curve = base.copy(
            segments = listOf(EnergySegment(EnergyPhase.SLEEP, Interval(Fx.at(Fx.today, 0, 0), Fx.at(Fx.today, 7, 0)), null)) +
                base.segments +
                EnergySegment(EnergyPhase.SLEEP, Interval(Fx.at(Fx.today, 23, 0), Fx.at(Fx.today, 23, 30)), null),
        )
        val r = TimelineGeometry.wakingRange(curve)!!
        assertThat(r.start).isEqualTo(Fx.at(Fx.today, 7, 0))
        assertThat(r.endInclusive).isEqualTo(Fx.at(Fx.today, 23, 0))
    }

    @Test fun levelMapsInsideThePlot() {
        assertThat(TimelineGeometry.x(0.0, 10f, 100f)).isEqualTo(40f)
        assertThat(TimelineGeometry.x(100.0, 10f, 100f)).isEqualTo(95f)
        assertThat(TimelineGeometry.x(250.0, 10f, 100f)).isEqualTo(95f)
    }

    @Test fun initialScrollPutsNowAThirdDownAndClamps() {
        val r = Fx.at(Fx.today, 7, 0)..Fx.at(Fx.today, 23, 0)
        // 16 h at 100 px/h plus 20 px padding each side.
        val content = 1640f
        assertThat(TimelineGeometry.initialScroll(Fx.at(Fx.today, 12, 0), r, 100f, 20f, 600f, content)).isEqualTo(320)
        assertThat(TimelineGeometry.initialScroll(Fx.at(Fx.today, 7, 30), r, 100f, 20f, 600f, content)).isEqualTo(0)
        assertThat(TimelineGeometry.initialScroll(Fx.at(Fx.today, 22, 59), r, 100f, 20f, 600f, content)).isEqualTo(1040)
        assertThat(TimelineGeometry.initialScroll(null, r, 100f, 20f, 600f, content)).isEqualTo(0)
    }

    @Test fun debtGaugeScalesToTwelveHoursAndFormats() {
        assertThat(DebtGaugeMath.fraction(Duration.ofHours(6))).isEqualTo(0.5f)
        assertThat(DebtGaugeMath.fraction(Duration.ofHours(20))).isEqualTo(1f)
        assertThat(DebtGaugeMath.fraction(Duration.ofMinutes(-30))).isEqualTo(0f)
        assertThat(DebtGaugeMath.hoursText(Duration.ofMinutes(378), Locale.ENGLISH)).isEqualTo("6.3")
        assertThat(DebtGaugeMath.hoursText(Duration.ZERO, Locale.ENGLISH)).isEqualTo("0.0")
    }
}
