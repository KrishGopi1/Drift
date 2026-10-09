package app.drift.feature.sleep

import app.drift.feature.sleep.bodyclock.ChronotypeSection
import app.drift.feature.sleep.bodyclock.JetlagSection
import app.drift.feature.sleep.bodyclock.toBodyClockUiModel
import app.drift.feature.sleep.history.HistoryRange
import app.drift.feature.sleep.history.buildHistoryModel
import app.drift.feature.sleep.night.CompletenessWord
import app.drift.feature.sleep.night.DetectionWord
import app.drift.feature.sleep.night.EditError
import app.drift.feature.sleep.night.EpisodeDraft
import app.drift.feature.sleep.night.adoptSource
import app.drift.feature.sleep.night.buildNightDetail
import app.drift.feature.sleep.night.snapToStep
import app.drift.feature.sleep.night.withDraft
import app.drift.feature.sleep.sleep.ContributionKind
import app.drift.feature.sleep.sleep.DebtSection
import app.drift.feature.sleep.sleep.toSleepUiModel
import app.drift.feature.sleep.why.WhyVariant
import app.drift.feature.sleep.why.buildWhyModel
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Provenance
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.SleepStage
import app.drift.model.SourceId
import app.drift.model.engine.MetricKey
import app.drift.testing.fixtures.FixtureContext
import app.drift.testing.fixtures.SnapshotFixtures
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalTime

class MappingTest {
    private val ctx = FixtureContext.IST

    @Test fun fullSnapshotHasAvailableDebtAndContributionsNewestFirst() {
        val snap = SnapshotFixtures.full(ctx)
        val m = snap.toSleepUiModel(ctx.zone, null)
        assertThat(m.debt).isInstanceOf(DebtSection.Available::class.java)
        assertThat(m.contributions).hasSize(14)
        assertThat(m.contributions.first().date).isEqualTo(ctx.today)
        assertThat(m.days.first().date).isLessThan(m.days.last().date)
        assertThat(m.need.personalized).isTrue()
        assertThat(m.regularity.showTrend).isTrue()
    }

    @Test fun contributionKindsFollowSignOfContribution() {
        val m = SnapshotFixtures.highDebt(ctx).toSleepUiModel(ctx.zone, true)
        m.contributions.forEach { r ->
            val expected = when {
                r.sleep == null -> ContributionKind.NO_DATA
                r.contribution > Duration.ZERO -> ContributionKind.BUILT
                r.contribution < Duration.ZERO -> ContributionKind.REPAID
                else -> ContributionKind.EVEN
            }
            assertThat(r.kind).isEqualTo(expected)
        }
        assertThat(m.contributions.any { it.kind == ContributionKind.BUILT }).isTrue()
    }

    @Test fun nightIdsAreAttachedByDate() {
        val id = EpisodeId("x")
        val m = SnapshotFixtures.full(ctx).toSleepUiModel(ctx.zone, null, mapOf(ctx.today to id))
        assertThat(m.contributions.first().episodeId).isEqualTo(id)
        assertThat(m.contributions[1].episodeId).isNull()
    }

    @Test fun fewerThanFiveNightsIsTooFew() {
        val m = SnapshotFixtures.early(ctx).toSleepUiModel(ctx.zone, null)
        assertThat(m.debt).isEqualTo(DebtSection.TooFewNights(3))
        assertThat(m.need.personalized).isFalse()
        assertThat(m.need.nightsUntilPersonal).isGreaterThan(0)
    }

    @Test fun missingNightsMakeDebtUnavailable() {
        val m = SnapshotFixtures.debtUnavailable(ctx).toSleepUiModel(ctx.zone, null)
        assertThat(m.debt).isEqualTo(DebtSection.Unavailable(3))
        assertThat(m.contributions.count { it.kind == ContributionKind.NO_DATA }).isEqualTo(3)
    }

    @Test fun tradeOffIsCarriedToTonight() {
        assertThat(SnapshotFixtures.withTradeOff(ctx).toSleepUiModel(ctx.zone, null).tonight.tradeOff).isNotNull()
        assertThat(SnapshotFixtures.withoutTradeOff(ctx).toSleepUiModel(ctx.zone, null).tonight.tradeOff).isNull()
    }

    @Test fun historyKeepsGapsAndAveragesExcludeThem() {
        val eps = Eps.history(ctx, 7, gaps = setOf(2, 5))
        val m = buildHistoryModel(HistoryRange.WEEK, eps, ctx.today, ctx.zone, Duration.ofHours(8), null)
        assertThat(m.bars).hasSize(7)
        assertThat(m.summary.recorded).isEqualTo(5)
        assertThat(m.summary.total).isEqualTo(7)
        assertThat(m.nights.first().date).isEqualTo(ctx.today)
        assertThat(m.nights.count { !it.hasData }).isEqualTo(2)
        assertThat(m.summary.averageSleep!!.toHours()).isIn(7L..8L)
        assertThat(m.nights.first { it.userEdited }.provenance).isEqualTo(Provenance.USER_ENTERED)
    }

    @Test fun historyRangesCoverTheirDays() {
        val eps = Eps.history(ctx, 90)
        HistoryRange.entries.forEach {
            val m = buildHistoryModel(it, eps, ctx.today, ctx.zone, null, null)
            assertThat(m.nights).hasSize(it.timing.days)
            assertThat(m.summary.recorded).isEqualTo(it.timing.days)
        }
    }

    @Test fun emptyHistoryHasNoAverages() {
        val m = buildHistoryModel(HistoryRange.WEEK, emptyList(), ctx.today, ctx.zone, null, null)
        assertThat(m.summary.averageSleep).isNull()
        assertThat(m.summary.averageOnsetMinutes).isNull()
    }

    @Test fun nightDetailWithStagesAndConflict() {
        val ep = Eps.night("a", ctx.today, ctx.zone, stages = true, flags = setOf(QualityFlag.SOURCE_CONFLICT))
        val other = Eps.night("b", ctx.today, ctx.zone, bed = LocalTime.of(23, 30), sourceId = SourceId.SLEEP_AS_ANDROID, tier = QualityTier.PHONE_APP)
        val m = buildNightDetail(ep, listOf(ep, other), DayAnnotation(ctx.today, freeDay = true), null)
        assertThat(m.stages).hasSize(4)
        assertThat(m.stages!!.sumOf { it.percent }).isIn(99..101)
        assertThat(m.conflicts).hasSize(2)
        assertThat(m.conflicts.filter { it.used }).hasSize(1)
        assertThat(m.conflicts.first { it.used }.episode.id).isEqualTo(ep.id)
        assertThat(m.freeDay).isTrue()
        assertThat(m.asleep).isLessThan(Duration.ofHours(8))
        assertThat(m.stages!!.first { it.stage == SleepStage.AWAKE }.duration).isGreaterThan(Duration.ZERO)
    }

    @Test fun nightDetailWithoutStagesOrConflictFlag() {
        val ep = Eps.night("a", ctx.today, ctx.zone, userEdited = true)
        val m = buildNightDetail(ep, listOf(ep), null, null)
        assertThat(m.stages).isNull()
        assertThat(m.conflicts).isEmpty()
        assertThat(m.provenance).isEqualTo(Provenance.USER_ENTERED)
        assertThat(m.asleep).isEqualTo(Duration.ofHours(8))
        assertThat(m.freeDay).isFalse()
    }

    @Test fun phoneInferredIsBestGuessAndEstimated() {
        val ep = Eps.night("a", ctx.today, ctx.zone, tier = QualityTier.PHONE_INFERRED)
        val m = buildNightDetail(ep, emptyList(), null, null)
        assertThat(m.detection).isEqualTo(DetectionWord.BEST_GUESS)
        assertThat(m.provenance).isEqualTo(Provenance.ESTIMATED)
        assertThat(m.completeness).isEqualTo(CompletenessWord.SOME_GAPS)
    }

    @Test fun adoptSourceTakesTheOtherTimesAndClearsConflict() {
        val ep = Eps.night("a", ctx.today, ctx.zone, flags = setOf(QualityFlag.SOURCE_CONFLICT))
        val other = Eps.night("b", ctx.today, ctx.zone, bed = LocalTime.of(23, 30), tier = QualityTier.PHONE_APP, flags = setOf(QualityFlag.SOURCE_CONFLICT))
        val r = ep.adoptSource(other)
        assertThat(r.id).isEqualTo(ep.id)
        assertThat(r.start).isEqualTo(other.start)
        assertThat(r.userEdited).isTrue()
        assertThat(r.quality.flags).doesNotContain(QualityFlag.SOURCE_CONFLICT)
    }

    @Test fun draftWrapsStartToEveningBeforeAndValidates() {
        val d = EpisodeDraft(ctx.today, ctx.zone, 23 * 60 + 5, 7 * 60)
        assertThat(d.startLocal.toLocalDate()).isEqualTo(ctx.today.minusDays(1))
        assertThat(d.duration).isEqualTo(Duration.ofMinutes(475))
        assertThat(d.errors()).isEmpty()
        assertThat(d.withStart(7 * 60).errors()).containsExactly(EditError.TOO_LONG)
        assertThat(d.withStart(6 * 60 + 55).errors()).containsExactly(EditError.TOO_SHORT)
        assertThat(d.copy(wakeDate = ctx.today.plusDays(5)).errors(ctx.now)).containsExactly(EditError.ENDS_IN_FUTURE)
    }

    @Test fun napDraftStaysOnSameDay() {
        val d = EpisodeDraft.blank(EpisodeKind.NAP, ctx.today, ctx.zone)
        assertThat(d.startLocal.toLocalDate()).isEqualTo(ctx.today)
        assertThat(d.duration).isEqualTo(Duration.ofMinutes(30))
    }

    @Test fun snapRoundsToFiveMinutes() {
        assertThat(snapToStep(7 * 60 + 2)).isEqualTo(7 * 60)
        assertThat(snapToStep(7 * 60 + 3)).isEqualTo(7 * 60 + 5)
        assertThat(snapToStep(1439)).isEqualTo(0)
    }

    @Test fun withDraftClipsStagesAndConfirms() {
        val ep = Eps.night("a", ctx.today, ctx.zone, stages = true, flags = setOf(QualityFlag.IMPLAUSIBLE_DURATION))
        val draft = EpisodeDraft.of(ep).withStart(1 * 60)
        val r = ep.withDraft(draft)
        assertThat(r.userEdited).isTrue()
        assertThat(r.start).isEqualTo(draft.start)
        assertThat(r.stages!!.all { it.interval.start >= r.start && it.interval.end <= r.end }).isTrue()
        assertThat(r.quality.flags).containsExactly(QualityFlag.USER_CONFIRMED)
    }

    @Test fun bodyClockStates() {
        val full = SnapshotFixtures.full(ctx).toBodyClockUiModel(ctx.zone, null, ctx.now)
        assertThat(full.chronotype).isInstanceOf(ChronotypeSection.Available::class.java)
        assertThat(full.socialJetlag).isInstanceOf(JetlagSection.Available::class.java)
        assertThat(full.plannedSleep).isNotNull()
        assertThat(full.dlmoHalfBand).isNotNull()
        val few = SnapshotFixtures.personalizing(ctx).toBodyClockUiModel(ctx.zone, null, ctx.now)
        assertThat(few.chronotype).isInstanceOf(ChronotypeSection.NotEnoughFreeNights::class.java)
        val east = SnapshotFixtures.travelEastward(ctx).toBodyClockUiModel(ctx.zone, null, ctx.now)
        assertThat(east.circadian.reentrainment).isNotNull()
        val day0 = SnapshotFixtures.priorsOnlyDay0(ctx).toBodyClockUiModel(ctx.zone, null, ctx.now)
        assertThat(day0.regularity.available).isFalse()
    }

    @Test fun whyVariants() {
        val rich = SnapshotFixtures.richExplanation(ctx)
        val prev = SnapshotFixtures.full(ctx)
        assertThat(buildWhyModel(MetricKey.SLEEP_DEBT, rich, prev).variant).isEqualTo(WhyVariant.CHANGES)
        assertThat(buildWhyModel(MetricKey.SLEEP_DEBT, rich, null).variant).isEqualTo(WhyVariant.FIRST_DAY)
        assertThat(buildWhyModel(MetricKey.REGULARITY, rich, prev).variant).isEqualTo(WhyVariant.NO_CHANGE)
        assertThat(buildWhyModel(MetricKey.SLEEP_NEED, SnapshotFixtures.firstRunExplanation(ctx), prev).variant).isEqualTo(WhyVariant.FIRST_DAY)
        assertThat(buildWhyModel(MetricKey.SLEEP_DEBT, null, null).variant).isEqualTo(WhyVariant.NO_CHANGE)
        assertThat(buildWhyModel(MetricKey.SLEEP_DEBT, rich, prev).duration).isNotNull()
    }
}
