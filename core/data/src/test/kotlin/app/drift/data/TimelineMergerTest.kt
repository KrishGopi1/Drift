package app.drift.data

import app.drift.data.Fixtures.episode
import app.drift.data.merge.RawEpisode
import app.drift.data.merge.TimelineMerger
import app.drift.data.merge.UserEdit
import app.drift.data.merge.episodeKey
import app.drift.model.EpisodeKind
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.SourceId
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class TimelineMergerTest {
    private val base = Instant.parse("2026-09-01T21:00:00Z")
    private fun at(hours: Double): Instant = base.plusSeconds((hours * 3600).toLong())
    private val t0 = Instant.parse("2026-10-01T00:00:00Z")
    private fun raw(e: app.drift.model.SleepEpisode, ingested: Instant = t0) = RawEpisode(e, ingested)

    @Test fun nonOverlappingEpisodesAreAllKept() {
        val out = TimelineMerger.merge(listOf(raw(episode("a", at(0.0), at(8.0))), raw(episode("b", at(17.0), at(18.0), kind = EpisodeKind.NAP))))
        assertThat(out).hasSize(2)
    }

    @Test fun higherTierWinsOverlap() {
        val phone = episode("p", at(0.0), at(8.0), SourceId.PHONE, QualityTier.PHONE_INFERRED)
        val watch = episode("w", at(0.2), at(7.9), SourceId.HEALTH_CONNECT, QualityTier.WEARABLE_WRIST)
        val out = TimelineMerger.merge(listOf(raw(phone), raw(watch)))
        assertThat(out).hasSize(1)
        assertThat(out.single().origin.recordId).isEqualTo("w")
    }

    @Test fun equalTierPrefersMoreRecentlyIngested() {
        val a = episode("hc", at(0.0), at(8.0), SourceId.HEALTH_CONNECT, QualityTier.PHONE_APP)
        val b = episode("csv", at(0.1), at(8.1), SourceId.SLEEP_AS_ANDROID, QualityTier.PHONE_APP)
        val out = TimelineMerger.merge(listOf(raw(a, t0), raw(b, t0.plusSeconds(60))))
        assertThat(out.single().origin.recordId).isEqualTo("csv")
        val out2 = TimelineMerger.merge(listOf(raw(a, t0.plusSeconds(60)), raw(b, t0)))
        assertThat(out2.single().origin.recordId).isEqualTo("hc")
    }

    @Test fun tierBeatsRecency() {
        val old = episode("ring", at(0.0), at(8.0), tier = QualityTier.WEARABLE_RING)
        val fresh = episode("phone", at(0.0), at(8.0), SourceId.PHONE, QualityTier.PHONE_INFERRED)
        val out = TimelineMerger.merge(listOf(raw(old, t0), raw(fresh, t0.plusSeconds(999))))
        assertThat(out.single().origin.recordId).isEqualTo("ring")
    }

    @Test fun lowerTierRemainderOverThresholdIsKeptTrimmed() {
        val phone = episode("p", at(0.0), at(9.0), SourceId.PHONE, QualityTier.PHONE_INFERRED, stages = true)
        val watch = episode("w", at(2.0), at(9.0), tier = QualityTier.WEARABLE_WRIST)
        val out = TimelineMerger.merge(listOf(raw(phone), raw(watch)))
        assertThat(out.map { it.origin.recordId }).containsExactly("p", "w").inOrder()
        val head = out.first()
        assertThat(head.start).isEqualTo(at(0.0))
        assertThat(head.end).isEqualTo(at(2.0))
        assertThat(head.stages!!.all { it.interval.end <= at(2.0) }).isTrue()
    }

    @Test fun smallRemaindersAreDropped() {
        val phone = episode("p", at(0.0), at(8.2), SourceId.PHONE, QualityTier.PHONE_INFERRED)
        val watch = episode("w", at(0.1), at(8.0))
        assertThat(TimelineMerger.merge(listOf(raw(phone), raw(watch)))).hasSize(1)
    }

    @Test fun conflictFlagOnMaterialDisagreement() {
        val phone = episode("p", at(0.0), at(9.0), SourceId.PHONE, QualityTier.PHONE_INFERRED)
        val watch = episode("w", at(1.0), at(8.0))
        val out = TimelineMerger.merge(listOf(raw(phone), raw(watch)))
        assertThat(out.single { it.origin.recordId == "w" }.quality.flags).contains(QualityFlag.SOURCE_CONFLICT)
        val close = episode("w2", at(0.05), at(8.95))
        val out2 = TimelineMerger.merge(listOf(raw(phone), raw(close)))
        assertThat(out2.single().quality.flags).doesNotContain(QualityFlag.SOURCE_CONFLICT)
    }

    @Test fun userEditAlwaysWinsAndSuppressesOverlap() {
        val watch = episode("w", at(0.0), at(8.0), tier = QualityTier.WEARABLE_RING)
        val user = episode("u", at(0.5), at(7.5), SourceId.USER, QualityTier.PHONE_INFERRED)
        val out = TimelineMerger.merge(listOf(raw(watch)), listOf(UserEdit(user)))
        assertThat(out).hasSize(1)
        assertThat(out.single().userEdited).isTrue()
        assertThat(out.single().id).isEqualTo(user.id)
    }

    @Test fun suppressedKeyHidesRawEvenWhenEditMovedAway() {
        val watch = episode("w", at(0.0), at(8.0))
        val user = episode("u", at(20.0), at(21.0), SourceId.USER, kind = EpisodeKind.NAP)
        val out = TimelineMerger.merge(listOf(raw(watch)), listOf(UserEdit(user, suppressedKeys = setOf(episodeKey(watch.origin)))))
        assertThat(out.map { it.id }).containsExactly(user.id)
    }

    @Test fun tombstoneBlanksSpanAndEmitsNothing() {
        val watch = episode("w", at(0.0), at(8.0))
        val later = episode("late", at(1.0), at(7.0), SourceId.SLEEP_AS_ANDROID, QualityTier.PHONE_APP)
        val tomb = UserEdit(watch.copy(id = app.drift.model.EpisodeId("x")), deleted = true, suppressedKeys = setOf(episodeKey(watch.origin)))
        assertThat(TimelineMerger.merge(listOf(raw(watch), raw(later)), listOf(tomb))).isEmpty()
    }

    @Test fun idsAreStableAcrossRunsAndInputOrder() {
        val a = episode("a", at(0.0), at(8.0))
        val b = episode("b", at(24.0), at(32.0))
        val one = TimelineMerger.merge(listOf(raw(a), raw(b)))
        val two = TimelineMerger.merge(listOf(raw(b), raw(a)))
        assertThat(one.map { it.id }).isEqualTo(two.map { it.id })
        assertThat(one.map { it.id }.toSet()).hasSize(2)
    }

    @Test fun adding_losingSourceDoesNotChangeWinnerId() {
        val w = episode("w", at(0.0), at(8.0))
        val before = TimelineMerger.merge(listOf(raw(w))).single().id
        val after = TimelineMerger.merge(listOf(raw(w), raw(episode("p", at(0.1), at(8.1), SourceId.PHONE, QualityTier.PHONE_INFERRED)))).single().id
        assertThat(after).isEqualTo(before)
    }

    /** Plan section 7, A2 done-when: zero duplicate nights on mixed-source fixtures. */
    @Test fun mixedSourceFixtureProducesZeroDuplicateNights() {
        val rnd = Random(42)
        val raws = ArrayList<RawEpisode>()
        val nights = 30
        for (n in 0 until nights) {
            val onset = base.plus(Duration.ofDays(n.toLong())).plus(Duration.ofMinutes(rnd.nextLong(-40, 40)))
            val wake = onset.plus(Duration.ofMinutes(420 + rnd.nextLong(-60, 60)))
            // Sleep as Android via Health Connect (PHONE_APP), its CSV copy with jitter, a watch, and the phone detector.
            raws += raw(episode("hc-saa-$n", onset, wake, SourceId.HEALTH_CONNECT, QualityTier.PHONE_APP, origin = "com.urbandroid.sleep"), t0)
            raws += raw(
                episode("csv-$n", onset.plusSeconds(rnd.nextLong(-120, 120)), wake.plusSeconds(rnd.nextLong(-120, 120)), SourceId.SLEEP_AS_ANDROID, QualityTier.PHONE_APP, stages = true),
                t0.plusSeconds(86400),
            )
            if (n % 2 == 0) raws += raw(episode("watch-$n", onset.plus(Duration.ofMinutes(rnd.nextLong(0, 25))), wake.minus(Duration.ofMinutes(rnd.nextLong(0, 25)))), t0)
            if (n % 3 == 0) raws += raw(episode("ph-$n", onset.minus(Duration.ofMinutes(30)), wake.plus(Duration.ofMinutes(10)), SourceId.PHONE, QualityTier.PHONE_INFERRED), t0)
        }
        val out = TimelineMerger.merge(raws.shuffled(rnd))
        assertThat(out).hasSize(nights)
        out.zipWithNext().forEach { (a, b) -> assertThat(a.interval.overlaps(b.interval)).isFalse() }
        assertThat(out.map { it.id }.toSet()).hasSize(nights)
    }

    @Test fun mergedOutputNeverOverlaps_randomised() {
        val rnd = Random(7)
        repeat(200) {
            val eps = (0 until rnd.nextInt(1, 12)).map { i ->
                val s = base.plusSeconds(rnd.nextLong(0, 3 * 86400))
                val e = s.plusSeconds(rnd.nextLong(600, 12 * 3600))
                val tier = QualityTier.entries[rnd.nextInt(QualityTier.entries.size)]
                raw(episode("r$i", s, e, SourceId("s${rnd.nextInt(3)}"), tier), t0.plusSeconds(rnd.nextLong(0, 1000)))
            }
            val out = TimelineMerger.merge(eps)
            out.zipWithNext().forEach { (a, b) -> assertThat(a.end <= b.start).isTrue() }
        }
    }
}
