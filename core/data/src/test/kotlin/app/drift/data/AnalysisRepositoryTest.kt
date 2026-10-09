package app.drift.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.data.Fixtures.episode
import app.drift.data.db.toEntity
import app.drift.data.db.toRawEntity
import app.drift.model.CheckIn
import app.drift.model.DayAnnotation
import app.drift.model.Signal
import app.drift.model.SourceId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AnalysisRepositoryTest {
    private lateinit var h: Harness
    private val now = Instant.parse("2026-10-09T08:00:00Z")

    @Before fun setUp() { h = Harness(ApplicationProvider.getApplicationContext<Context>()) }
    @After fun tearDown() = h.close()

    @Test fun refreshBuildsNinetyDayInputAndPersists() = runTest {
        val recent = episode("r", now.minus(Duration.ofHours(10)), now.minus(Duration.ofHours(2)))
        val old = episode("o", now.minus(Duration.ofDays(100)), now.minus(Duration.ofDays(100)).plusSeconds(28000))
        val boundary = episode("b", now.minus(Duration.ofDays(89)), now.minus(Duration.ofDays(89)).plusSeconds(28000))
        h.db.sourceEpisodes().upsert(listOf(recent, old, boundary).map { it.toRawEntity(now) })
        val origin = recent.origin
        h.db.signals().upsert(listOf(Signal.HeartRateSample(origin, now.minusSeconds(3600), 52.0), Signal.StepBucket(origin, app.drift.model.Interval(now.minusSeconds(7200), now.minusSeconds(3600)), 321)).map { it.toEntity() })
        h.sleep.setAnnotation(DayAnnotation(LocalDate.parse("2026-10-08"), freeDay = true))
        h.checkIns.add(CheckIn.EnergyRating(now.minusSeconds(100), 4))

        val snap = h.analysis.refresh(now)

        val input = h.engine.inputs.single()
        assertThat(input.now).isEqualTo(now)
        assertThat(input.episodes.map { it.origin.recordId }).containsExactly("b", "r")
        assertThat(input.signals.signals).hasSize(2)
        assertThat(input.annotations.single().freeDay).isTrue()
        assertThat(input.checkIns).hasSize(1)
        assertThat(input.priorState).isNull()
        assertThat(h.analysis.observeLatest().first()).isEqualTo(snap)
    }

    @Test fun priorStateIsFedBackOnNextRun() = runTest {
        val first = h.analysis.refresh(now)
        h.analysis.refresh(now.plusSeconds(60))
        assertThat(h.engine.inputs[1].priorState).isEqualTo(first.state)
    }

    @Test fun previousSnapshotAndSameHashReplacement() = runTest {
        h.engine.hash = "a"
        h.analysis.refresh(now)
        h.engine.hash = "b"
        val second = h.analysis.refresh(now.plusSeconds(60))
        assertThat(h.analysis.observeLatest().first()).isEqualTo(second)
        assertThat(h.analysis.observePrevious().first()!!.inputHash).isEqualTo("a")
        h.analysis.refresh(now.plusSeconds(120)) // same hash "b": replaces, previous stays "a"
        assertThat(h.analysis.observePrevious().first()!!.inputHash).isEqualTo("a")
        assertThat(h.db.snapshots().all()).hasSize(2)
    }
}
