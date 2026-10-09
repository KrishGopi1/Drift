package app.drift.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.data.Fixtures.episode
import app.drift.data.db.toRawEntity
import app.drift.data.merge.episodeKey
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.SourceId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SleepRepositoryTest {
    private lateinit var h: Harness
    private val start = Instant.parse("2026-10-08T22:00:00Z")
    private val end = Instant.parse("2026-10-09T06:00:00Z")
    private val range = Interval(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-10T00:00:00Z"))

    @Before fun setUp() { h = Harness(ApplicationProvider.getApplicationContext<Context>()) }
    @After fun tearDown() = h.close()

    private suspend fun seed(vararg eps: app.drift.model.SleepEpisode) {
        h.db.sourceEpisodes().upsert(eps.map { it.toRawEntity(h.clock.now) })
    }

    @Test fun mergesAcrossSourcesWithoutDuplicates() = runTest {
        seed(
            episode("hc", start, end, SourceId.HEALTH_CONNECT, QualityTier.PHONE_APP, origin = "com.urbandroid.sleep"),
            episode("csv", start.plusSeconds(60), end.minusSeconds(60), SourceId.SLEEP_AS_ANDROID, QualityTier.PHONE_APP, stages = true),
        )
        val merged = h.sleep.observeEpisodes(range).first()
        assertThat(merged).hasSize(1)
        assertThat(h.sleep.observeSourceEpisodes(range).first()).hasSize(2)
    }

    @Test fun userEditWinsAndKeepsId() = runTest {
        seed(episode("w", start, end, tier = QualityTier.WEARABLE_RING))
        val original = h.sleep.observeEpisodes(range).first().single()
        h.sleep.saveUserEpisode(original.copy(start = start.plusSeconds(1800)))
        val after = h.sleep.observeEpisodes(range).first()
        assertThat(after).hasSize(1)
        assertThat(after.single().id).isEqualTo(original.id)
        assertThat(after.single().userEdited).isTrue()
        assertThat(after.single().start).isEqualTo(start.plusSeconds(1800))
        assertThat(h.sleep.observeEpisode(original.id).first()).isEqualTo(after.single())
    }

    @Test fun manualNapIsAdded() = runTest {
        seed(episode("w", start, end))
        val nap = episode("nap", Instant.parse("2026-10-09T13:00:00Z"), Instant.parse("2026-10-09T13:30:00Z"), SourceId.USER, QualityTier.PHONE_INFERRED, EpisodeKind.NAP)
        h.sleep.saveUserEpisode(nap)
        assertThat(h.sleep.observeEpisodes(range).first()).hasSize(2)
    }

    @Test fun deleteTombstonesSourceDataAcrossResyncs() = runTest {
        val w = episode("w", start, end)
        seed(w)
        val id = h.sleep.observeEpisodes(range).first().single().id
        h.sleep.deleteEpisode(id)
        assertThat(h.sleep.observeEpisodes(range).first()).isEmpty()
        // The same record re-synced, plus a different source reporting the same night, stay hidden.
        seed(w, episode("csv", start, end, SourceId.SLEEP_AS_ANDROID, QualityTier.PHONE_APP))
        assertThat(h.sleep.observeEpisodes(range).first()).isEmpty()
    }

    @Test fun deletingUserOnlyEpisodeRemovesIt() = runTest {
        val nap = episode("nap", Instant.parse("2026-10-09T13:00:00Z"), Instant.parse("2026-10-09T13:30:00Z"), SourceId.USER, kind = EpisodeKind.NAP)
        h.sleep.saveUserEpisode(nap)
        h.sleep.deleteEpisode(nap.id)
        assertThat(h.sleep.observeEpisodes(range).first()).isEmpty()
        assertThat(h.db.userEpisodes().all()).isEmpty()
    }

    @Test fun confirmMarksUserEditedAndConfirmed() = runTest {
        seed(episode("p", start, end, SourceId.PHONE, QualityTier.PHONE_INFERRED))
        h.clock.now = Instant.parse("2026-10-09T07:00:00Z")
        val pending = h.sleep.observePendingConfirmation().first()
        assertThat(pending).isNotNull()
        h.sleep.confirmEpisode(pending!!.id)
        val after = h.sleep.observeEpisodes(range).first().single()
        assertThat(after.userEdited).isTrue()
        assertThat(after.quality.flags).contains(QualityFlag.USER_CONFIRMED)
        assertThat(h.sleep.observePendingConfirmation().first()).isNull()
    }

    @Test fun annotationsRoundTrip() = runTest {
        val a = DayAnnotation(LocalDate.parse("2026-10-09"), freeDay = true, alarmSet = false, alarmDismissedAt = Instant.parse("2026-10-09T06:30:00.123456Z"))
        h.sleep.setAnnotation(a)
        assertThat(h.sleep.observeAnnotations(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")).first()).containsExactly(a)
        assertThat(h.sleep.observeAnnotations(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30")).first()).isEmpty()
        assertThat(episodeKey(episode("x", start, end).origin)).isEqualTo("healthconnect:x")
    }
}
