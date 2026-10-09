package app.drift.testing.synthetic

import app.drift.model.EpisodeKind
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.SleepStage
import app.drift.model.SourceId
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.sqrt

class ScenariosTest {
    companion object {
        @JvmStatic fun every(): List<Array<Any>> = Scenarios.all(1L).map { (n, h) -> arrayOf<Any>(n, h) }
    }

    private fun hours(d: Duration) = d.toMinutes() / 60.0
    private fun sd(xs: List<Double>): Double { val m = xs.average(); return sqrt(xs.sumOf { (it - m) * (it - m) } / xs.size) }
    private fun localMinutes(t: java.time.Instant, z: ZoneId) = t.atZone(z).toLocalTime().toSecondOfDay() / 60.0

    @Test fun `same seed gives identical histories and a different seed differs`() {
        assertThat(Scenarios.all(5L)).isEqualTo(Scenarios.all(5L))
        assertThat(Scenarios.steadySleeper(ScenarioConfig(seed = 1))).isNotEqualTo(Scenarios.steadySleeper(ScenarioConfig(seed = 2)))
        // Adding signals or check-ins must not change episodes of the same seed.
        val withSignals = Scenarios.steadySleeper(ScenarioConfig(seed = 3, signals = SignalDensity.FULL, checkIns = CheckInDensity.DENSE))
        val without = Scenarios.steadySleeper(ScenarioConfig(seed = 3, signals = SignalDensity.NONE, checkIns = CheckInDensity.NONE))
        assertThat(withSignals.episodes).isEqualTo(without.episodes)
        assertThat(without.signals).isEmpty()
        assertThat(without.checkIns).isEmpty()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("every")
    fun `histories are well formed`(name: String, h: SyntheticHistory) {
        assertThat(h.truth.nights).hasSize(h.annotations.size + h.truth.missingDates.size)
        val ids = h.rawEpisodes.map { it.id }
        assertThat(ids).containsNoDuplicates()
        assertThat(h.episodes.map { it.start }).isInOrder()
        // Canonical main episodes never overlap each other (implausible blips aside).
        val mains = h.episodes.filter { it.kind == EpisodeKind.MAIN && QualityFlag.IMPLAUSIBLE_DURATION !in it.quality.flags }
        mains.zipWithNext().forEach { (a, b) -> assertThat(a.end).isAtMost(b.start) }
        h.episodes.forEach { assertThat(it.end).isAtMost(h.now) }
        assertThat(h.now).isGreaterThan(h.truth.nights.last().interval.end)
        // JSON replay round-trips exactly.
        val back = ReplayJson.read(ReplayJson.write(h))
        assertThat(back.episodes).isEqualTo(h.episodes)
        assertThat(back.annotations).isEqualTo(h.annotations)
        assertThat(back.checkIns).isEqualTo(h.checkIns)
        assertThat(back.signals).isEqualTo(h.signals)
        assertThat(back.profile).isEqualTo(h.profile)
        assertThat(back.zone).isEqualTo(h.zone)
        assertThat(back.now).isEqualTo(h.now)
    }

    @Test fun `steady sleeper sleeps need every night with a tight schedule`() {
        val h = Scenarios.steadySleeper()
        assertThat(h.truth.sleepNeed).isEqualTo(Duration.ofHours(8))
        val durations = h.truth.nights.map { hours(it.interval.duration) }
        assertThat(durations.average()).isWithin(0.35).of(8.2)
        assertThat(sd(h.truth.nights.map { localMinutes(it.interval.start, it.zone).let { m -> if (m < 720) m + 1440 else m } })).isLessThan(40.0)
        assertThat(h.episodes.count { it.kind == EpisodeKind.MAIN }).isEqualTo(60)
    }

    @Test fun `chronic short sleeper is far below need`() {
        val h = Scenarios.chronicShortSleeper()
        val work = h.truth.nights.filter { !isFreeDay(it.date) }.map { hours(it.interval.duration) }
        assertThat(work.average()).isWithin(0.3).of(6.0)
        assertThat(h.truth.nights.map { hours(it.interval.duration) }.average()).isLessThan(hours(h.truth.sleepNeed) - 1.0)
    }

    @Test fun `weekend catch up has a large free-day surplus`() {
        val h = Scenarios.weekendCatchUp()
        val free = h.truth.nights.filter { isFreeDay(it.date) }.map { hours(it.interval.duration) }.average()
        val work = h.truth.nights.filter { !isFreeDay(it.date) }.map { hours(it.interval.duration) }.average()
        assertThat(free - work).isAtLeast(2.5)
        assertThat(h.annotations.filter { it.freeDay == true }.map { it.date.dayOfWeek.name }.toSet()).containsExactly("SATURDAY", "SUNDAY")
    }

    @Test fun `new parent nights are fragmented and naps are common`() {
        val h = Scenarios.newParent()
        assertThat(h.truth.nights.all { it.awakenings in 2..4 }).isTrue()
        val mains = h.episodes.filter { it.kind == EpisodeKind.MAIN }
        assertThat(mains.all { e -> e.stages!!.count { it.stage == SleepStage.AWAKE } >= 1 }).isTrue()
        assertThat(h.episodes.count { it.kind == EpisodeKind.NAP }).isAtLeast(20)
        // Stage spans tile the episode.
        mains.forEach { e ->
            assertThat(e.stages!!.first().interval.start).isEqualTo(e.start)
            assertThat(e.stages!!.last().interval.end).isEqualTo(e.end)
            e.stages!!.zipWithNext().forEach { (a, b) -> assertThat(a.interval.end).isEqualTo(b.interval.start) }
        }
        assertThat(sd(h.truth.nights.map { localMinutes(it.interval.start, it.zone).let { m -> if (m < 720) m + 1440 else m } })).isGreaterThan(20.0)
    }

    @Test fun `rotating night shift cycles through three regimes`() {
        val h = Scenarios.rotatingNightShift()
        val nights = h.truth.nights
        fun startHour(i: Int) = localMinutes(nights[i].interval.start, nights[i].zone) / 60.0
        assertThat(startHour(0)).isWithin(2.0).of(23.0)       // day-shift week: ~23:00 (index 0 is a 'day' week)
        assertThat(startHour(8)).isWithin(2.0).of(9.0)        // night-shift week: morning sleep
        assertThat(startHour(15)).isWithin(2.0).of(1.0)       // off-days: 01:00
        // DLMO truth moves but slowly (< 90 min/day).
        val d = h.truth.dlmoMinutesByDate.toSortedMap().values.toList()
        d.zipWithNext().forEach { (a, b) -> assertThat(abs(((b - a + 720 + 1440) % 1440) - 720)).isLessThan(90.0) }
        assertThat(d.max() - d.min()).isGreaterThan(30.0)
    }

    @Test fun `eastern travel changes zone and the body clock converges at one hour a day`() {
        val h = Scenarios.easternTravel()
        val change = LocalDate.parse(h.truth.facts.getValue("zoneChangeDate"))
        assertThat(h.zone.id).isEqualTo("Europe/Paris")
        assertThat(h.truth.nights.first().zone.id).isEqualTo("America/New_York")
        assertThat(h.truth.nights.filter { it.date < change }.map { it.zone.id }.toSet()).containsExactly("America/New_York")
        assertThat(h.truth.nights.filter { it.date >= change }.map { it.zone.id }.toSet()).containsExactly("Europe/Paris")
        assertThat(h.episodes.filter { it.start >= h.truth.nights.first { n -> n.date == change }.interval.start }.map { it.zone.id }.toSet()).containsExactly("Europe/Paris")
        val dlmo = h.truth.dlmoMinutesByDate
        assertThat(dlmo.getValue(change.minusDays(1))).isEqualTo(21 * 60.0)
        assertThat(dlmo.getValue(change)).isWithin(0.1).of(((21 + 6) * 60.0) % 1440)
        assertThat(dlmo.getValue(change.plusDays(3))).isWithin(0.1).of(((21 + 3) * 60.0) % 1440)
        assertThat(dlmo.getValue(change.plusDays(6))).isEqualTo(21 * 60.0)
        // Early jet-lagged nights are shorter than settled ones.
        val early = h.truth.nights.first { it.date == change }.interval.duration
        val settled = h.truth.nights.last().interval.duration
        assertThat(early).isLessThan(settled.minusMinutes(60))
    }

    @Test fun `western travel converges faster and wakes early`() {
        val h = Scenarios.westernTravel()
        val change = LocalDate.parse(h.truth.facts.getValue("zoneChangeDate"))
        val dlmo = h.truth.dlmoMinutesByDate
        assertThat(h.zone.id).isEqualTo("America/New_York")
        assertThat(dlmo.getValue(change)).isEqualTo(15 * 60.0)
        assertThat(dlmo.getValue(change.plusDays(4))).isEqualTo(21 * 60.0)
        val wake = h.truth.nights.first { it.date == change }
        assertThat(localMinutes(wake.interval.end, wake.zone) / 60.0).isLessThan(5.5)
    }

    @Test fun `missing week has seven nights with no data at all`() {
        val h = Scenarios.missingWeek()
        assertThat(h.truth.missingDates).hasSize(7)
        assertThat(h.truth.missingDates.sorted().zipWithNext().all { (a, b) -> b == a.plusDays(1) }).isTrue()
        val wakeDates = h.episodes.map { it.end.atZone(h.zone).toLocalDate() }
        assertThat(wakeDates.intersect(h.truth.missingDates)).isEmpty()
        assertThat(h.episodes).hasSize(53)
        assertThat(h.annotations.map { it.date }).containsNoneIn(h.truth.missingDates)
        val sigDates = h.signals.map { it.let { s -> signalTime(s) }.atZone(h.zone).toLocalDate() }.toSet()
        assertThat(sigDates.intersect(h.truth.missingDates.sorted().dropLast(1).toSet())).isEmpty()
    }

    @Test fun `duplicate sources agree within an hour and canonical keeps one night each`() {
        val h = Scenarios.duplicateSources()
        val hc = h.rawEpisodes.filter { it.origin.sourceId == SourceId.HEALTH_CONNECT }
        val saa = h.rawEpisodes.filter { it.origin.sourceId == SourceId.SLEEP_AS_ANDROID }
        assertThat(hc).hasSize(60)
        assertThat(saa.size).isIn(com.google.common.collect.Range.closed(45, 60))
        assertThat(h.episodes).hasSize(60)
        assertThat(h.episodes.map { it.origin.qualityTier }.toSet()).containsExactly(QualityTier.WEARABLE_WRIST)
        saa.forEach { s ->
            val match = hc.single { it.interval.overlaps(s.interval) }
            assertThat(abs(Duration.between(match.start, s.start).toMinutes())).isLessThan(60)
            assertThat(abs(Duration.between(match.end, s.end).toMinutes())).isLessThan(60)
            assertThat(match.start).isNotEqualTo(s.start)
        }
        assertThat(h.episodes.count { QualityFlag.SOURCE_CONFLICT in it.quality.flags }).isEqualTo(saa.size)
    }

    @Test fun `dst nights have 7 and 9 hours of real time at the same wall clock`() {
        val spring = Scenarios.dstSpringForward()
        val sDate = LocalDate.parse(spring.truth.facts.getValue("dstTransitionDate"))
        assertThat(sDate).isEqualTo(LocalDate.of(2026, 3, 8))
        assertThat(spring.truth.nights.first { it.date == sDate }.interval.duration).isEqualTo(Duration.ofHours(7))
        assertThat(spring.truth.nights.first { it.date == sDate.plusDays(1) }.interval.duration).isEqualTo(Duration.ofHours(8))
        val fall = Scenarios.dstFallBack()
        val fDate = LocalDate.parse(fall.truth.facts.getValue("dstTransitionDate"))
        assertThat(fall.truth.nights.first { it.date == fDate }.interval.duration).isEqualTo(Duration.ofHours(9))
        listOf(spring, fall).forEach { h ->
            h.truth.nights.forEach {
                assertThat(it.interval.start.atZone(it.zone).toLocalTime()).isEqualTo(LocalTime.of(23, 0))
                assertThat(it.interval.end.atZone(it.zone).toLocalTime()).isEqualTo(LocalTime.of(7, 0))
            }
            assertThat(h.zone.id).isEqualTo("America/New_York")
        }
    }

    @Test fun `noisy phone has the low tier, no stages, poor quality nights and implausible episodes`() {
        val h = Scenarios.noisyPhoneInferred()
        val nights = h.rawEpisodes.filter { QualityFlag.IMPLAUSIBLE_DURATION !in it.quality.flags }
        assertThat(nights.map { it.origin.qualityTier }.toSet()).containsExactly(QualityTier.PHONE_INFERRED)
        assertThat(nights.all { it.stages == null }).isTrue()
        assertThat(nights.count { it.quality.completeness < 0.7 }).isAtLeast(8)
        assertThat(nights.count { it.quality.detectionConfidence <= 0.6 }).isAtLeast(8)
        val implausible = h.rawEpisodes.filter { QualityFlag.IMPLAUSIBLE_DURATION in it.quality.flags }
        assertThat(implausible.map { hours(it.interval.duration) }).containsExactly(1.5, 17.0)
        // Quality tiers differ across sources.
        val ring = Scenarios.steadySleeper(ScenarioConfig(primary = SourceProfile.RING)).episodes
        assertThat(ring.map { it.origin.qualityTier }.toSet()).containsExactly(QualityTier.WEARABLE_RING)
        assertThat(ring.all { it.quality.completeness >= 0.97 }).isTrue()
    }

    @Test fun `check-ins follow the density setting and signals the density`() {
        val dense = Scenarios.steadySleeper(ScenarioConfig(checkIns = CheckInDensity.DENSE, signals = SignalDensity.FULL))
        val sparse = Scenarios.steadySleeper(ScenarioConfig(checkIns = CheckInDensity.SPARSE))
        assertThat(dense.checkIns.size).isGreaterThan(sparse.checkIns.size * 3)
        assertThat(dense.checkIns.filterIsInstance<app.drift.model.CheckIn.ReactionTest>()).isNotEmpty()
        assertThat(dense.signals.filterIsInstance<app.drift.model.Signal.HeartRateSample>()).isNotEmpty()
        assertThat(sparse.signals.filterIsInstance<app.drift.model.Signal.HeartRateSample>()).isEmpty()
        // Energy ratings are higher around the evening peak than in the post-lunch dip... on average, across dense data.
        val ratings = dense.checkIns.filterIsInstance<app.drift.model.CheckIn.EnergyRating>()
        val byHour = ratings.groupBy { it.at.atZone(dense.zone).hour }.mapValues { (_, v) -> v.map { it.rating }.average() }
        assertThat(byHour.getValue(10)).isGreaterThan(byHour.getValue(20) - 1.5)
    }

    @Test fun `reZoned preserves wall-clock times`() {
        val h = Scenarios.steadySleeper()
        val ny = ZoneId.of("America/New_York")
        val moved = h.reZoned(ny)
        assertThat(moved.zone).isEqualTo(ny)
        assertThat(moved.episodes).hasSize(h.episodes.size)
        h.episodes.zip(moved.episodes).forEach { (a, b) ->
            assertThat(b.start.atZone(ny).toLocalDateTime()).isEqualTo(a.start.atZone(a.zone).toLocalDateTime())
            assertThat(b.end.atZone(ny).toLocalDateTime()).isEqualTo(a.end.atZone(a.zone).toLocalDateTime())
            assertThat(b.zone).isEqualTo(ny)
        }
        assertThat(moved.now.atZone(ny).toLocalDateTime()).isEqualTo(h.now.atZone(h.zone).toLocalDateTime())
        assertThat(moved.signals).hasSize(h.signals.size)
    }

    @Test fun `engine input contains only data up to now and within 90 days`() {
        val h = Scenarios.steadySleeper(ScenarioConfig(days = 120))
        val input = h.toEngineInput()
        assertThat(input.episodes.size).isAtMost(91)
        assertThat(input.episodes.all { it.end <= input.now }).isTrue()
        val early = h.toEngineInput(at = h.truth.nights[10].interval.end.plusSeconds(3 * 3600L))
        assertThat(early.episodes).hasSize(11)
        assertThat(early.annotations.all { it.date <= early.now.atZone(h.zone).toLocalDate() }).isTrue()
    }
}
