package app.drift.source.phone.detect

import app.drift.model.Interval
import app.drift.source.phone.ChargeChange
import app.drift.source.phone.ClassifySample
import app.drift.source.phone.ScreenChange
import app.drift.source.phone.SleepSegment
import app.drift.source.phone.StepSample
import app.drift.source.phone.StepSpan
import app.drift.source.phone.stepSpans
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset

class TimelinesAndBinsTest {
    private val t0 = Instant.parse("2026-03-01T22:00:00Z")
    private fun at(min: Long) = t0.plusSeconds(min * 60)

    @Test fun `screen intervals are clipped and carry state from before the window`() {
        val tl = ScreenTimeline(listOf(ScreenChange(at(-5), true), ScreenChange(at(3), false), ScreenChange(at(25), true)))
        val on = tl.onIntervals(at(0), at(40))
        assertThat(on).containsExactly(Interval(at(0), at(3)), Interval(at(25), at(40))).inOrder()
    }

    @Test fun `last screen off and first screen on lookups`() {
        val tl = ScreenTimeline(listOf(ScreenChange(at(1), true), ScreenChange(at(4), false), ScreenChange(at(60), true)))
        assertThat(tl.lastScreenOffIn(at(0), at(10))).isEqualTo(at(4))
        assertThat(tl.firstScreenOnIn(at(5), at(100))).isEqualTo(at(60))
        assertThat(tl.firstScreenOnIn(at(5), at(30))).isNull()
    }

    @Test fun `charge timeline is unknown before the first change`() {
        val tl = ChargeTimeline(listOf(ChargeChange(at(10), true), ChargeChange(at(50), false)))
        assertThat(tl.pluggedAt(at(0))).isNull()
        assertThat(tl.pluggedAt(at(20))).isTrue()
        assertThat(tl.pluggedAt(at(50))).isFalse()
    }

    @Test fun `bins combine every signal and leave missing data null`() {
        val data = PhoneDataSet(
            screen = ScreenTimeline(listOf(ScreenChange(at(0), true), ScreenChange(at(4), false))),
            segments = listOf(SleepSegment(at(10), at(30), 0)),
            classify = listOf(ClassifySample(at(31), 40, 0, 0)),
            charging = ChargeTimeline(listOf(ChargeChange(at(-10), true))),
            steps = listOf(StepSpan(at(0), at(20), 40)),
            sleepApiActive = true,
        )
        val bins = Binner.build(Interval(at(0), at(40)), ZoneOffset.UTC, data)
        assertThat(bins).hasSize(4)
        assertThat(bins[0].screenOnMinutes).isWithin(1e-9).of(4.0)
        assertThat(bins[1].screenOnMinutes).isWithin(1e-9).of(0.0)
        assertThat(bins[1].sleepApi).isWithin(1e-9).of(0.95)
        assertThat(bins[3].sleepApi).isWithin(1e-9).of(0.4)
        assertThat(bins[0].steps).isWithin(1e-9).of(20.0)
        assertThat(bins[3].steps).isNull()
        assertThat(bins[2].charging).isTrue()
        assertThat(bins[0].localHour).isWithin(1e-9).of(22.0)
    }

    @Test fun `missing sources produce null evidence not zeros`() {
        val data = PhoneDataSet(null, emptyList(), emptyList(), ChargeTimeline(emptyList()), emptyList(), sleepApiActive = false)
        val bins = Binner.build(Interval(at(0), at(20)), ZoneOffset.UTC, data)
        bins.forEach {
            assertThat(it.screenOnMinutes).isNull(); assertThat(it.sleepApi).isNull(); assertThat(it.charging).isNull(); assertThat(it.steps).isNull()
        }
    }

    @Test fun `long step spans are ignored for bins`() {
        val data = PhoneDataSet(null, emptyList(), emptyList(), ChargeTimeline(emptyList()), listOf(StepSpan(at(0), at(300), 500)), false)
        assertThat(Binner.build(Interval(at(0), at(20)), ZoneOffset.UTC, data)[0].steps).isNull()
    }

    @Test fun `step spans skip reboots and negative deltas`() {
        val spans = stepSpans(listOf(StepSample(at(0), 100, 5), StepSample(at(20), 160, 5), StepSample(at(40), 20, 9), StepSample(at(60), 50, 9)))
        assertThat(spans.map { it.count }).containsExactly(60L, 30L).inOrder()
    }
}
