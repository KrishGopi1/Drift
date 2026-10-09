package app.drift.engine

import app.drift.engine.models.Preprocessor
import app.drift.model.EpisodeKind
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.SleepStage
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class PreprocessorTest {
    private val pre = Preprocessor(ParameterSet.DEFAULT)

    private fun run(vararg eps: app.drift.model.SleepEpisode, now: String = "2026-09-10T12:00", zone: ZoneId = Kit.UTC) =
        pre.run(Kit.input(eps.toList(), now, zone))

    @Test fun `fragments with a short wake gap merge into one night`() {
        val r = run(
            Kit.ep("a", "2026-09-01T23:00", "2026-09-02T03:00"),
            Kit.ep("b", "2026-09-02T03:30", "2026-09-02T07:00"),
        )
        val night = r.byDay.values.single()
        assertThat(night.main!!.ids).containsExactly("a", "b").inOrder()
        assertThat(night.main!!.tst).isEqualTo(4 * 60.0 + 3.5 * 60.0) // asleep minutes exclude the 30 min gap
        assertThat(night.naps).isEmpty()
    }

    @Test fun `fragments separated by 45 minutes or more stay separate and the shorter becomes a nap`() {
        val r = run(
            Kit.ep("a", "2026-09-01T23:00", "2026-09-02T04:00"),
            Kit.ep("b", "2026-09-02T04:45", "2026-09-02T06:00"),
        )
        val night = r.byDay.values.single()
        assertThat(night.main!!.ids).containsExactly("a")
        assertThat(night.naps.single().ids).containsExactly("b")
    }

    @Test fun `sleep day is the wake date in the episode zone`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        // 23:00 on 1 Sep to 07:00 on 2 Sep Tokyo time = 14:00-22:00 UTC on 1 Sep; engine zone is UTC.
        val r = run(Kit.ep("a", "2026-09-01T23:00", "2026-09-02T07:00", tokyo))
        assertThat(r.byDay.keys.single()).isEqualTo(LocalDate.of(2026, 9, 2).toEpochDay())
    }

    @Test fun `an afternoon nap belongs to the sleep day of the preceding night`() {
        val r = run(
            Kit.ep("m", "2026-09-01T23:00", "2026-09-02T07:00"),
            Kit.ep("n", "2026-09-02T14:00", "2026-09-02T14:40", kind = EpisodeKind.MAIN),
        )
        val night = r.byDay.values.single()
        assertThat(night.main!!.ids).containsExactly("m")
        assertThat(night.naps.single().tst).isEqualTo(40.0)
    }

    @Test fun `implausibly short or long main sleep is excluded unless user confirmed`() {
        val short = run(Kit.ep("s", "2026-09-02T02:00", "2026-09-02T03:30"))
        assertThat(short.byDay.values.single().main).isNull()
        assertThat(short.byDay.values.single().excludedMain).isNotNull()
        val long = run(Kit.ep("l", "2026-09-01T10:00", "2026-09-02T03:30"))
        assertThat(long.byDay.values.single().main).isNull()
        val confirmed = run(Kit.ep("c", "2026-09-02T02:00", "2026-09-02T03:30", flags = setOf(QualityFlag.USER_CONFIRMED)))
        assertThat(confirmed.byDay.values.single().main).isNotNull()
    }

    @Test fun `episodes ending in the future are excluded`() {
        val r = run(Kit.ep("f", "2026-09-10T23:00", "2026-09-11T07:00"), now = "2026-09-10T12:00")
        assertThat(r.nights).isEmpty()
        assertThat(r.excludedCount).isEqualTo(1)
    }

    @Test fun `awake stages do not count as sleep`() {
        val r = run(
            Kit.ep(
                "a", "2026-09-01T23:00", "2026-09-02T07:00",
                stages = listOf(Kit.stage(SleepStage.AWAKE, "2026-09-02T02:00", "2026-09-02T02:30"), Kit.stage(SleepStage.DEEP, "2026-09-02T03:00", "2026-09-02T04:00")),
            ),
        )
        assertThat(r.byDay.values.single().main!!.tst).isEqualTo(7.5 * 60)
    }

    @Test fun `overlapping duplicates from two sources do not double count`() {
        val r = run(
            Kit.ep("a", "2026-09-01T23:00", "2026-09-02T07:00"),
            Kit.ep("b", "2026-09-01T23:10", "2026-09-02T06:50", tier = QualityTier.PHONE_APP),
        )
        val m = r.byDay.values.single().main!!
        assertThat(m.tst).isEqualTo(8 * 60.0)
        assertThat(m.ids).hasSize(2)
    }

    @Test fun `shift work mode needs three day sleeps in the last seven days`() {
        fun days(n: Int) = (0 until 7).map { i ->
            val d = LocalDate.of(2026, 9, 3).plusDays(i.toLong())
            if (i < n) Kit.ep("d$i", "${d}T08:30", "${d}T15:30") else Kit.ep("d$i", "${d.minusDays(1)}T23:00", "${d}T07:00")
        }
        assertThat(pre.run(Kit.input(days(3), "2026-09-10T12:00")).shiftWorkMode).isTrue()
        assertThat(pre.run(Kit.input(days(2), "2026-09-10T12:00")).shiftWorkMode).isFalse()
    }

    @Test fun `quality weight follows tier completeness and user confirmation`() {
        fun w(tier: QualityTier, completeness: Double = 1.0, edited: Boolean = false) =
            run(Kit.ep("a", "2026-09-01T23:00", "2026-09-02T07:00", tier = tier, completeness = completeness, edited = edited))
                .byDay.values.single().main!!.weight
        assertThat(w(QualityTier.WEARABLE_RING)).isGreaterThan(w(QualityTier.WEARABLE_WRIST))
        assertThat(w(QualityTier.WEARABLE_WRIST)).isGreaterThan(w(QualityTier.PHONE_INFERRED))
        assertThat(w(QualityTier.WEARABLE_WRIST, completeness = 0.4)).isLessThan(w(QualityTier.WEARABLE_WRIST))
        assertThat(w(QualityTier.PHONE_INFERRED, edited = true)).isGreaterThan(w(QualityTier.PHONE_INFERRED))
    }

    @Test fun `empty input yields no nights`() {
        val r = run()
        assertThat(r.nights).isEmpty()
        assertThat(r.shiftWorkMode).isFalse()
    }

    @Test fun `last day is yesterday before the cutoff hour when today is not recorded yet`() {
        val eps = arrayOf(Kit.ep("a", "2026-09-08T23:00", "2026-09-09T07:00"))
        assertThat(run(*eps, now = "2026-09-10T09:00").lastDay).isEqualTo(LocalDate.of(2026, 9, 9).toEpochDay())
        assertThat(run(*eps, now = "2026-09-10T15:00").lastDay).isEqualTo(LocalDate.of(2026, 9, 10).toEpochDay())
    }
}
