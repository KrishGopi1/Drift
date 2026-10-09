package app.drift.engine

import app.drift.engine.internal.Circ
import app.drift.model.DayAnnotation
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CircadianTest {
    private val utc = ZoneId.of("UTC")

    private fun snap(s: Scenario, now: LocalDateTime? = null) =
        Kit.engine.analyze(s.input(nowOverride = now, dropAfterNow = true))

    @Test fun `DLMO sits two to three hours before habitual sleep onset`() {
        val c = snap(Synth.steady(utc)).circadian
        // Onsets are 23:00 on workdays and 23:30 at weekends, so DLMO lands about 20:30-21:00.
        val dlmo = c.dlmoMinutes!!.value
        assertThat(dlmo).isIn(com.google.common.collect.Range.closed(20 * 60.0, 21 * 60.0 + 30))
        assertThat(c.dlmoMinutes!!.confidence).isNotNull()
    }

    @Test fun `melatonin window starts two hours after DLMO and wind down one hour before it`() {
        val s = Synth.steady(utc)
        val c = snap(s).circadian
        val w = c.melatoninWindow!!
        assertThat(w.duration).isEqualTo(Duration.ofHours(1))
        assertThat(Duration.between(c.windDownStart!!, w.start)).isEqualTo(Duration.ofHours(1))
        val dlmo = c.dlmoMinutes!!.value
        val startMin = w.start.atZone(utc).let { it.hour * 60 + it.minute }
        assertThat(Circ.wrap(startMin - (dlmo + 120))).isWithin(1.0).of(0.0)
    }

    @Test fun `the window is for the coming evening and rolls to tomorrow once it has passed`() {
        val s = Synth.steady(utc)
        val noon = snap(s).circadian.melatoninWindow!!
        val late = snap(s, LocalDateTime.of(2026, 9, 14, 23, 59)).circadian.melatoninWindow!!
        assertThat(noon.start.atZone(utc).toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 14))
        val afterNoon = snap(s, LocalDateTime.of(2026, 9, 14, 12, 0))
        assertThat(afterNoon.circadian.melatoninWindow!!.end).isGreaterThan(afterNoon.computedAt)
        assertThat(late.end).isGreaterThan(LocalDateTime.of(2026, 9, 14, 23, 59).atZone(utc).toInstant())
    }

    @Test fun `an alarm night moves the estimate less than a free night with the same timing`() {
        val base = Synth.steady(utc, days = 40)
        val outlier = LocalDate.of(2026, 9, 11)
        val nights = base.nights.map { n ->
            if (n.id == "n-$outlier") Scenario.LocalNight(outlier.atTime(3, 0), outlier.atTime(11, 0), utc, id = n.id) else n
        }
        fun dlmo(ann: DayAnnotation?): Double {
            val s = Scenario("o", utc, base.profile, base.nowLocal, nights, listOfNotNull(ann))
            return Kit.engine.analyze(s.input()).circadian.dlmoMinutes!!.value
        }
        val reference = Kit.engine.analyze(base.input()).circadian.dlmoMinutes!!.value
        val asAlarm = dlmo(DayAnnotation(outlier, alarmSet = true))
        val asFree = dlmo(DayAnnotation(outlier, freeDay = true))
        assertThat(Math.abs(Circ.wrap(asAlarm - reference))).isLessThan(Math.abs(Circ.wrap(asFree - reference)))
    }

    @Test fun `shift work mode is flagged and widens the band`() {
        val shift = snap(Synth.rotatingNightShift(utc)).circadian
        val steady = snap(Synth.steady(utc)).circadian
        assertThat(shift.shiftWorkMode).isTrue()
        assertThat(steady.shiftWorkMode).isFalse()
        val ws = shift.dlmoMinutes!!.confidence!!.let { it.upper - it.lower }
        val wt = steady.dlmoMinutes!!.confidence!!.let { it.upper - it.lower }
        assertThat(ws).isGreaterThan(wt * 1.5)
    }

    @Test fun `eastward travel is re-entrained at no more than one hour per day`() {
        val ny = ZoneId.of("America/New_York")
        val berlin = ZoneId.of("Europe/Berlin")
        val flight = Synth.UTC_START.plusDays(28)
        val dlmos = (0..6).map { k ->
            val s = Synth.travel(ny, berlin, awayDays = 7, adaptPerDayMin = 60)
            val now = flight.plusDays(k.toLong()).atTime(12, 0)
            Kit.engine.analyze(s.input(nowOverride = now)).circadian
        }
        // Local-clock DLMO jumps east by 6 h on arrival, then walks back at <= 60 min/day.
        val before = Kit.engine.analyze(Synth.steady(ny).input()).circadian.dlmoMinutes!!.value
        for (k in 1..6) {
            val step = Circ.wrap(dlmos[k].dlmoMinutes!!.value - dlmos[k - 1].dlmoMinutes!!.value)
            assertThat(step).isIn(com.google.common.collect.Range.closed(-75.0, 10.0))
        }
        assertThat(dlmos[1].reentrainment).isNotNull()
        assertThat(dlmos[1].reentrainment!!.eastward).isTrue()
        assertThat(dlmos[1].reentrainment!!.remainingShift.toMinutes()).isAtMost(6 * 60L)
        assertThat(dlmos[1].reentrainment!!.estimatedDaysLeft).isIn(com.google.common.collect.Range.closed(3, 6))
        assertThat(dlmos[6].reentrainment).isNull()
        assertThat(Math.abs(Circ.wrap(dlmos[6].dlmoMinutes!!.value - before))).isLessThan(120.0)
    }

    @Test fun `westward travel re-entrains faster and is reported as westward`() {
        val ny = ZoneId.of("America/New_York")
        val berlin = ZoneId.of("Europe/Berlin")
        val flight = Synth.UTC_START.plusDays(28)
        val s = Synth.travel(berlin, ny, awayDays = 7, adaptPerDayMin = 90)
        val d1 = Kit.engine.analyze(s.input(nowOverride = flight.plusDays(1).atTime(12, 0))).circadian
        assertThat(d1.reentrainment).isNotNull()
        assertThat(d1.reentrainment!!.eastward).isFalse()
        assertThat(d1.reentrainment!!.estimatedDaysLeft).isAtMost(4)
        val d5 = Kit.engine.analyze(s.input(nowOverride = flight.plusDays(5).atTime(12, 0))).circadian
        assertThat(d5.reentrainment).isNull()
    }

    @Test fun `a zone change with no sleep recorded in the new zone is treated as a trip`() {
        val ny = ZoneId.of("America/New_York")
        val s = Synth.steady(ny)
        val home = Kit.engine.analyze(s.input()).circadian
        val abroad = Kit.engine.analyze(s.input(zoneOverride = null).copy(zone = ZoneId.of("Asia/Tokyo"))).circadian
        assertThat(home.reentrainment).isNull()
        assertThat(abroad.reentrainment).isNotNull()
    }

    @Test fun `daylight saving changes are not trips`() {
        val s = Synth.dst(ZoneId.of("America/New_York"), LocalDate.of(2026, 3, 1), days = 14, name = "dst")
        val c = Kit.engine.analyze(s.input()).circadian
        assertThat(c.reentrainment).isNull()
    }

    @Test fun `with no data the DLMO comes from the questionnaire prior with low confidence`() {
        val c = Kit.engine.analyze(Kit.input(emptyList(), "2026-09-10T12:00")).circadian
        assertThat(c.dlmoMinutes!!.confidence!!.level).isEqualTo(app.drift.model.ConfidenceLevel.LOW)
        assertThat(c.melatoninWindow).isNotNull()
    }
}
