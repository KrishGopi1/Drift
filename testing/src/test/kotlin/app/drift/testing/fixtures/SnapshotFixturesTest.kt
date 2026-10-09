package app.drift.testing.fixtures

import app.drift.model.ConfidenceLevel
import app.drift.model.Provenance
import app.drift.model.Quantity
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.CauseKind
import app.drift.model.engine.DebtBand
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.MetricKey
import app.drift.model.engine.SufficiencyTier
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration

class SnapshotFixturesTest {
    companion object {
        @JvmStatic
        fun everyFixture(): List<Array<Any>> = FixtureContext.ALL.flatMap { ctx ->
            SnapshotFixtures.all(ctx).map { (name, snap) -> arrayOf<Any>("${ctx.zone.id}/$name", ctx, snap) }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyFixture")
    fun `debt trend rows sum to the debt and chain correctly`(label: String, ctx: FixtureContext, snap: AnalysisSnapshot) {
        val trend = snap.debtTrend
        var running = Duration.ZERO
        trend.forEach {
            running = running.plus(it.contribution)
            assertThat(it.debtAfter).isEqualTo(running)
            assertThat(it.debtAfter.isNegative).isFalse()
        }
        if (snap.sleepDebt != null) assertThat(snap.sleepDebt!!.value).isEqualTo(trend.last().debtAfter)
        assertThat(trend.size).isAtMost(14)
        trend.zipWithNext().forEach { (a, b) -> assertThat(b.date).isEqualTo(a.date.plusDays(1)) }
        if (trend.isNotEmpty()) assertThat(trend.last().date).isEqualTo(ctx.today)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyFixture")
    fun `segments tile every day and points are 5 minutes apart`(label: String, ctx: FixtureContext, snap: AnalysisSnapshot) {
        assertThat(snap.energyCurve.days.map { it.date }).containsExactly(ctx.today.minusDays(1), ctx.today, ctx.today.plusDays(1)).inOrder()
        snap.energyCurve.days.forEach { day ->
            val start = day.date.atStartOfDay(ctx.zone).toInstant()
            val end = day.date.plusDays(1).atStartOfDay(ctx.zone).toInstant()
            assertThat(day.points.first().at).isEqualTo(start)
            day.points.zipWithNext().forEach { (a, b) -> assertThat(Duration.between(a.at, b.at)).isEqualTo(Duration.ofMinutes(5)) }
            assertThat(Duration.between(day.points.last().at, end)).isEqualTo(Duration.ofMinutes(5))
            day.points.forEach { assertThat(it.level).isIn(com.google.common.collect.Range.closed(0.0, 100.0)) }
            // Contiguous, non-overlapping, covering exactly the local day.
            assertThat(day.segments.first().interval.start).isEqualTo(start)
            assertThat(day.segments.last().interval.end).isEqualTo(end)
            day.segments.zipWithNext().forEach { (a, b) -> assertThat(a.interval.end).isEqualTo(b.interval.start) }
            day.segments.forEach { seg ->
                seg.extremumAt?.let { assertThat(seg.interval.contains(it)).isTrue() }
            }
            assertThat(day.provenance).isEqualTo(Provenance.ESTIMATED)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyFixture")
    fun `estimated values carry bands that contain the value`(label: String, ctx: FixtureContext, snap: AnalysisSnapshot) {
        fun <T : Comparable<T>> check(q: Quantity<T>?) {
            if (q == null) return
            if (q.provenance == Provenance.ESTIMATED) {
                val c = q.confidence
                assertThat(c).isNotNull()
                assertThat(q.value >= c!!.lower && q.value <= c.upper).isTrue()
            }
        }
        check(snap.sleepNeed); check(snap.sleepDebt); check(snap.circadian.dlmoMinutes)
        check(snap.recommendations.sleepOpportunity); check(snap.recommendations.bedtime); check(snap.recommendations.wakeTime)
        check(snap.regularity.chronotype?.msfscMinutes)
        snap.debtTrend.forEach { check(it.sleep) }
        // Every number shown has a provenance; priors-only values have no sources.
        if (snap.dataSufficiency.tier == SufficiencyTier.PRIORS_ONLY) assertThat(snap.sleepNeed.sourceIds).isEmpty()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyFixture")
    fun `recommendations are coherent with the curve`(label: String, ctx: FixtureContext, snap: AnalysisSnapshot) {
        val rec = snap.recommendations
        assertThat(rec.wakeTime.value).isGreaterThan(rec.bedtime.value)
        assertThat(Duration.between(rec.bedtime.value, rec.wakeTime.value)).isEqualTo(rec.sleepOpportunity.value)
        assertThat(rec.bedtime.value).isGreaterThan(ctx.now)
        val today = snap.energyCurve.forDate(ctx.today)!!
        val hasDip = today.segments.any { it.phase == EnergyPhase.AFTERNOON_DIP }
        assertThat(hasDip).isEqualTo(!today.tooFlatForDip)
        rec.nap?.let { nap ->
            assertThat(snap.debtBand).isNotEqualTo(DebtBand.LOW)
            assertThat(hasDip).isTrue()
            assertThat(nap.window.end).isAtMost(rec.bedtime.value.minus(Duration.ofHours(7)))
            assertThat(nap.maxDuration).isAtLeast(Duration.ofMinutes(20))
            assertThat(nap.maxDuration).isAtMost(Duration.ofMinutes(30))
        }
        if (rec.tradeOff != null) assertThat(rec.tradeOff!!.bedtimeIfFollowingWindow).isGreaterThan(rec.bedtime.value)
        snap.circadian.melatoninWindow?.let { assertThat(it.end).isGreaterThan(ctx.now) }
    }

    @Test fun `clock is fixed`() {
        assertThat(FixtureContext.IST.now.toString()).isEqualTo("2026-10-14T07:30:00Z")
        assertThat(FixtureContext.IST.zone.id).isEqualTo("Asia/Kolkata")
        assertThat(FixtureContext.IST.today.toString()).isEqualTo("2026-10-14")
        assertThat(FixtureContext.US_EASTERN.zone.id).isEqualTo("America/New_York")
        assertThat(FixtureContext.US_EASTERN.localNow.hour).isEqualTo(10)
    }

    @Test fun `fixtures are deterministic`() {
        assertThat(SnapshotFixtures.all()).isEqualTo(SnapshotFixtures.all())
    }

    @Test fun `priors only day 0`() {
        val s = SnapshotFixtures.priorsOnlyDay0()
        assertThat(s.dataSufficiency.tier).isEqualTo(SufficiencyTier.PRIORS_ONLY)
        assertThat(s.dataSufficiency.daysOfHistory).isEqualTo(0)
        assertThat(s.sleepDebt).isNull()
        assertThat(s.debtBand).isNull()
        assertThat(s.debtTrend).isEmpty()
        assertThat(s.regularity.sri7).isNull()
        assertThat(s.energyCurve.days.all { it.provenance == Provenance.ESTIMATED && it.confidence == ConfidenceLevel.LOW }).isTrue()
        assertThat(s.sleepNeed.confidence!!.level).isEqualTo(ConfidenceLevel.LOW)
    }

    @Test fun `early has a wide debt band and the prior need`() {
        val s = SnapshotFixtures.early()
        assertThat(s.dataSufficiency.tier).isEqualTo(SufficiencyTier.EARLY)
        assertThat(s.debtTrend).hasSize(2)
        val band = s.sleepDebt!!.confidence!!
        assertThat(band.upper.minus(s.sleepDebt!!.value)).isEqualTo(Duration.ofHours(2))
        assertThat(s.sleepNeed.confidence!!.level).isEqualTo(ConfidenceLevel.LOW)
        assertThat(s.dataSufficiency.nightsUntilPersonalNeed).isEqualTo(3)
    }

    @Test fun `personalizing has personal need and regularity but no chronotype`() {
        val s = SnapshotFixtures.personalizing()
        assertThat(s.dataSufficiency.informativeNights).isEqualTo(8)
        assertThat(s.dataSufficiency.nightsUntilPersonalNeed).isEqualTo(0)
        assertThat(s.dataSufficiency.regularityAvailable).isTrue()
        assertThat(s.regularity.sri7).isNotNull()
        assertThat(s.regularity.sri14).isNull()
        assertThat(s.regularity.chronotype).isNull()
        assertThat(s.sleepNeed.confidence!!.level).isEqualTo(ConfidenceLevel.MEDIUM)
    }

    @Test fun `full has 14 night trend, both SRI windows and a chronotype`() {
        val s = SnapshotFixtures.full()
        assertThat(s.debtTrend).hasSize(14)
        assertThat(s.regularity.sri7).isNotNull()
        assertThat(s.regularity.sri14).isNotNull()
        assertThat(s.regularity.chronotype).isNotNull()
        assertThat(s.regularity.trend).isNotEmpty()
        assertThat(s.sleepNeed.confidence!!.level).isEqualTo(ConfidenceLevel.HIGH)
        assertThat(s.dataSufficiency.tier).isEqualTo(SufficiencyTier.FULL)
    }

    @Test fun `high debt is in the HIGH band`() {
        val s = SnapshotFixtures.highDebt()
        assertThat(s.debtBand).isEqualTo(DebtBand.HIGH)
        assertThat(s.sleepDebt!!.value).isAtLeast(Duration.ofHours(5))
        assertThat(s.debtBand).isEqualTo(SnapshotBuilder.bandFor(s.sleepDebt!!.value))
    }

    @Test fun `three missing nights make debt unavailable`() {
        val s = SnapshotFixtures.debtUnavailable()
        assertThat(s.sleepDebt).isNull()
        assertThat(s.debtBand).isNull()
        assertThat(s.dataSufficiency.debtAvailable).isFalse()
        assertThat(s.debtTrend.count { it.imputed }).isEqualTo(3)
        s.debtTrend.filter { it.imputed }.forEach { assertThat(it.sleep).isNull() }
    }

    @Test fun `shift work and travel flags`() {
        val shift = SnapshotFixtures.shiftWork()
        assertThat(shift.circadian.shiftWorkMode).isTrue()
        assertThat(shift.recommendations.wakeTime.value.atZone(FixtureContext.IST.zone).hour).isEqualTo(16)
        val east = SnapshotFixtures.travelEastward().circadian.reentrainment!!
        assertThat(east.eastward).isTrue()
        assertThat(east.remainingShift).isEqualTo(Duration.ofHours(3))
        val west = SnapshotFixtures.travelWestward().circadian.reentrainment!!
        assertThat(west.eastward).isFalse()
        assertThat(SnapshotFixtures.full().circadian.reentrainment).isNull()
        assertThat(SnapshotFixtures.full().circadian.shiftWorkMode).isFalse()
    }

    @Test fun `flat curve says so and has no dip segment while normal curves do`() {
        val flat = SnapshotFixtures.flatCurve().energyCurve.forDate(FixtureContext.IST.today)!!
        assertThat(flat.tooFlatForDip).isTrue()
        assertThat(flat.segments.map { it.phase }).doesNotContain(EnergyPhase.AFTERNOON_DIP)
        val normal = SnapshotFixtures.full().energyCurve.forDate(FixtureContext.IST.today)!!
        assertThat(normal.tooFlatForDip).isFalse()
        assertThat(normal.segments.map { it.phase }).containsAtLeast(
            EnergyPhase.GROGGINESS, EnergyPhase.MORNING_PEAK, EnergyPhase.AFTERNOON_DIP, EnergyPhase.EVENING_PEAK,
            EnergyPhase.WIND_DOWN, EnergyPhase.MELATONIN_WINDOW, EnergyPhase.SLEEP,
        ).inOrder()
        // The dip really is a dip: lower than both neighbouring peaks.
        fun level(phase: EnergyPhase) = normal.segments.first { it.phase == phase }.extremumAt!!.let { t -> normal.points.first { it.at == t }.level }
        assertThat(level(EnergyPhase.AFTERNOON_DIP)).isLessThan(level(EnergyPhase.MORNING_PEAK) - 5)
        assertThat(level(EnergyPhase.AFTERNOON_DIP)).isLessThan(level(EnergyPhase.EVENING_PEAK) - 5)
    }

    @Test fun `trade-off appears only when bedtime precedes the melatonin window`() {
        assertThat(SnapshotFixtures.withTradeOff().recommendations.tradeOff).isNotNull()
        assertThat(SnapshotFixtures.withoutTradeOff().recommendations.tradeOff).isNull()
        assertThat(SnapshotFixtures.travelEastward().recommendations.tradeOff).isNotNull()
    }

    @Test fun `nap advice exists with moderate debt and a dip, and not without`() {
        val nap = SnapshotFixtures.withNap()
        assertThat(nap.debtBand).isEqualTo(DebtBand.MODERATE)
        assertThat(nap.recommendations.nap).isNotNull()
        assertThat(SnapshotFixtures.full().recommendations.nap).isNull()
        assertThat(SnapshotFixtures.flatCurve().recommendations.nap).isNull()
    }

    @Test fun `first run explanation has FIRST_RUN causes only`() {
        val s = SnapshotFixtures.firstRunExplanation()
        assertThat(s.explanations).isNotEmpty()
        assertThat(s.explanations.flatMap { it.causes }.map { it.kind }.toSet()).containsExactly(CauseKind.FIRST_RUN)
        assertThat(s.explanations.map { it.metric }).contains(MetricKey.SLEEP_NEED)
    }

    @Test fun `rich explanation has multiple causes with consistent numbers`() {
        val s = SnapshotFixtures.richExplanation()
        val debt = s.explanations.first { it.metric == MetricKey.SLEEP_DEBT }
        assertThat(debt.causes.map { it.kind }).containsAtLeast(
            CauseKind.NIGHT_ENTERED_WINDOW, CauseKind.NIGHT_LEFT_WINDOW, CauseKind.USER_EDIT, CauseKind.SOURCE_ADDED, CauseKind.NEED_REESTIMATED,
        )
        val entered = debt.causes.first { it.kind == CauseKind.NIGHT_ENTERED_WINDOW }
        assertThat(entered.date).isEqualTo(s.debtTrend.last().date)
        assertThat(entered.delta).isEqualTo(s.debtTrend.last().contribution.toMinutes().toDouble())
        assertThat(debt.causes.first { it.kind == CauseKind.NIGHT_LEFT_WINDOW }.date).isEqualTo(s.debtTrend.first().date.minusDays(1))
        val energy = s.explanations.first { it.metric == MetricKey.ENERGY }
        assertThat(energy.causes.map { it.kind }).containsAtLeast(CauseKind.CIRCADIAN_SHIFT, CauseKind.CHECK_IN_CALIBRATION)
        assertThat(s.energyCurve.calibrationCheckIns).isEqualTo(12)
    }

    @Test fun `daily debt contributions follow the repay rule`() {
        val s = SnapshotFixtures.full()
        s.debtTrend.filter { !it.imputed }.forEach { row ->
            val raw = row.need.minus(row.sleep!!.value)
            // Shortfalls add in full; surpluses repay at most half, never below zero debt.
            if (!raw.isNegative) assertThat(row.contribution).isAtMost(raw)
            else assertThat(row.contribution.abs()).isAtMost(raw.abs().dividedBy(2).plusMinutes(1))
        }
    }
}
