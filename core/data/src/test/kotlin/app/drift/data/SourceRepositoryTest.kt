package app.drift.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.data.Fixtures.episode
import app.drift.data.db.toRawEntity
import app.drift.data.repo.BackupImporter
import app.drift.data.repo.ImportBatch
import app.drift.model.Checkpoint
import app.drift.model.Interval
import app.drift.model.QualityTier
import app.drift.model.Signal
import app.drift.model.SleepEpisode
import app.drift.model.SourceId
import app.drift.model.repository.SourceFix
import app.drift.model.repository.SyncReason
import app.drift.model.source.Capability
import app.drift.model.source.PullResult
import app.drift.model.source.SourceAdapter
import app.drift.model.source.SourceStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant

class FakeAdapter(
    override val id: SourceId,
    var status: SourceStatus = SourceStatus.Available,
    override val qualityTier: QualityTier = QualityTier.WEARABLE_WRIST,
) : SourceAdapter {
    override val capabilities = setOf(Capability.SLEEP_SESSIONS)
    val requested = ArrayList<Checkpoint?>()
    var results = ArrayDeque<PullResult>()
    var failure: Exception? = null
    override suspend fun status() = status
    override suspend fun pull(since: Checkpoint?): PullResult {
        requested += since
        failure?.let { throw it }
        return results.removeFirstOrNull() ?: PullResult(emptyList(), emptyList(), since)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SourceRepositoryTest {
    private val hc = FakeAdapter(SourceId.HEALTH_CONNECT)
    private val other = FakeAdapter(SourceId.PHONE, qualityTier = QualityTier.PHONE_INFERRED)
    private val start = Instant.parse("2026-10-08T22:00:00Z")
    private val end = Instant.parse("2026-10-09T06:00:00Z")
    private val h = Harness(ApplicationProvider.getApplicationContext<Context>(), adapters = linkedSetOf(hc, other))

    @After fun tearDown() = h.close()

    @Test fun syncStoresDataAndCheckpointAndResumesFromIt() = runTest {
        val origin = episode("a", start, end).origin
        hc.results += PullResult(
            listOf(episode("a", start, end)),
            listOf(Signal.HeartRateSample(origin, start.plusSeconds(60), 50.0)),
            Checkpoint("tok1"),
        )
        val report = h.sources.sync(SyncReason.FIRST_RUN)
        assertThat(report.newEpisodes).isEqualTo(1)
        assertThat(report.newSignals).isEqualTo(1)
        assertThat(report.errors).isEmpty()
        h.sources.sync(SyncReason.APP_OPEN)
        assertThat(hc.requested).containsExactly(null, Checkpoint("tok1")).inOrder()
        val info = h.sources.observeSources().first().single { it.id == SourceId.HEALTH_CONNECT }
        assertThat(info.lastSync).isNotNull()
    }

    @Test fun updatedAndDeletedRecords() = runTest {
        hc.results += PullResult(listOf(episode("a", start, end), episode("b", start.minusSeconds(86400), end.minusSeconds(86400))), emptyList(), Checkpoint("1"))
        h.sources.sync(SyncReason.APP_OPEN)
        hc.results += PullResult(listOf(episode("a", start, end.plusSeconds(600))), emptyList(), Checkpoint("2"), deletedRecordIds = setOf("b"))
        val report = h.sources.sync(SyncReason.APP_OPEN)
        assertThat(report.updatedEpisodes).isEqualTo(1)
        val rows = h.db.sourceEpisodes().all()
        assertThat(rows.map { it.recordId }).containsExactly("a")
        assertThat(rows.single().endMs).isEqualTo(end.plusSeconds(600).toEpochMilli())
    }

    @Test fun fullResyncReplacesRecentWindowOnly() = runTest {
        val old = episode("old", h.clock.now.minusSeconds(60L * 86400), h.clock.now.minusSeconds(60L * 86400 - 28000))
        val stale = episode("stale", h.clock.now.minusSeconds(5L * 86400), h.clock.now.minusSeconds(5L * 86400 - 28000))
        hc.results += PullResult(listOf(old, stale), emptyList(), Checkpoint("1"))
        h.sources.sync(SyncReason.APP_OPEN)
        val fresh = episode("fresh", h.clock.now.minusSeconds(2L * 86400), h.clock.now.minusSeconds(2L * 86400 - 28000))
        hc.results += PullResult(listOf(fresh), emptyList(), Checkpoint("2"), fullResync = true)
        h.sources.sync(SyncReason.APP_OPEN)
        assertThat(h.db.sourceEpisodes().all().map { it.recordId }).containsExactly("old", "fresh")
    }

    @Test fun adapterFailureIsReportedNotThrownAndOthersStillRun() = runTest {
        hc.failure = IllegalStateException("boom")
        other.results += PullResult(listOf(episode("p", start, end, SourceId.PHONE, QualityTier.PHONE_INFERRED)), emptyList(), null)
        val report = h.sources.sync(SyncReason.PERIODIC)
        assertThat(report.errors[SourceId.HEALTH_CONNECT]).isEqualTo("boom")
        assertThat(report.newEpisodes).isEqualTo(1)
        val info = h.sources.observeSources().first().single { it.id == SourceId.HEALTH_CONNECT }
        assertThat(info.lastError).isEqualTo("boom")
    }

    @Test fun unavailableAdapterIsSkippedAndMapsToFix() = runTest {
        hc.status = SourceStatus.NeedsPermission(setOf("android.permission.health.READ_SLEEP"))
        val report = h.sources.sync(SyncReason.APP_OPEN)
        assertThat(hc.requested).isEmpty()
        assertThat(report.errors).isEmpty()
        val info = h.sources.observeSources().first().single { it.id == SourceId.HEALTH_CONNECT }
        assertThat(info.fix).isEqualTo(SourceFix.GrantHealthConnectPermissions(setOf("android.permission.health.READ_SLEEP")))
        hc.status = SourceStatus.NotInstalled
        assertThat(h.sources.observeSources().first().single { it.id == SourceId.HEALTH_CONNECT }.fix).isEqualTo(SourceFix.InstallHealthConnect)
    }

    @Test fun disabledSourcesAreNotPulled() = runTest {
        h.sources.setEnabled(SourceId.HEALTH_CONNECT, false)
        h.sources.sync(SyncReason.USER_PULL)
        assertThat(hc.requested).isEmpty()
        assertThat(other.requested).hasSize(1)
        assertThat(h.sources.observeSources().first().single { it.id == SourceId.HEALTH_CONNECT }.enabled).isFalse()
    }

    @Test fun progressIsNullWhenIdle() = runTest {
        h.sources.sync(SyncReason.APP_OPEN)
        assertThat(h.sources.observeSyncProgress().first()).isNull()
    }

    @Test fun oldSignalsAreTrimmed() = runTest {
        val o = episode("a", start, end).origin
        val oldHr = Signal.HeartRateSample(o, h.clock.now.minusSeconds(100L * 86400), 60.0)
        hc.results += PullResult(emptyList(), listOf(oldHr, Signal.HeartRateSample(o, start, 55.0)), null)
        h.sources.sync(SyncReason.APP_OPEN)
        assertThat(h.db.signals().count()).isEqualTo(1)
    }

    @Test fun ninetyDayBackfillWithDenseSignalsIsFast() = runTest {
        val eps = (0 until 90).map { n ->
            val s = h.clock.now.minusSeconds((n + 1) * 86400L)
            episode("n$n", s, s.plusSeconds(8 * 3600L), stages = true)
        }
        val origin = eps[0].origin
        val signals = (0 until 90).flatMap { n ->
            val s = h.clock.now.minusSeconds((n + 1) * 86400L)
            (0 until 288).map { i -> Signal.HeartRateSample(origin, s.plusSeconds(i * 300L), 50.0 + i % 20) }
        }
        hc.results += PullResult(eps, signals, Checkpoint("t"))
        val t0 = System.nanoTime()
        val report = h.sources.sync(SyncReason.FIRST_RUN)
        val seconds = (System.nanoTime() - t0) / 1e9
        assertThat(report.newEpisodes).isEqualTo(90)
        assertThat(report.newSignals).isEqualTo(90 * 288)
        assertThat(seconds).isLessThan(10.0)
        val refreshStart = System.nanoTime()
        h.analysis.refresh(h.clock.now)
        assertThat((System.nanoTime() - refreshStart) / 1e9).isLessThan(5.0)
    }

    // ---- import ---------------------------------------------------------------------------------

    private fun importer(vararg eps: SleepEpisode, rejected: Int = 0) = object : BackupImporter {
        override val sourceId = SourceId.SLEEP_AS_ANDROID
        override suspend fun parse(input: InputStream) = ImportBatch(eps.toList(), emptyList(), rejected, listOf("w"))
    }

    private fun repoWith(importer: BackupImporter) =
        app.drift.data.repo.SourceRepositoryImpl(h.db, emptySet(), setOf(importer), h.clock)

    @Test fun importDedupesAgainstHealthConnectCopyAndReimport() = runTest {
        val saaViaHc = episode("hc", start, end, SourceId.HEALTH_CONNECT, QualityTier.PHONE_APP, origin = "com.urbandroid.sleep")
        h.db.sourceEpisodes().upsert(listOf(saaViaHc.toRawEntity(h.clock.now)))
        val dupe = episode("csv-1", start.plusSeconds(60), end, SourceId.SLEEP_AS_ANDROID, QualityTier.PHONE_APP)
        val fresh = episode("csv-2", start.minusSeconds(86400 * 3), end.minusSeconds(86400 * 3), SourceId.SLEEP_AS_ANDROID, QualityTier.PHONE_APP)
        val repo = repoWith(importer(dupe, fresh, rejected = 2))
        val first = repo.importSleepAsAndroidBackup(ByteArrayInputStream(ByteArray(0)))
        assertThat(first.nightsImported).isEqualTo(1)
        assertThat(first.duplicatesSkipped).isEqualTo(1)
        assertThat(first.rowsRejected).isEqualTo(2)
        assertThat(first.warnings).containsExactly("w")
        val second = repo.importSleepAsAndroidBackup(ByteArrayInputStream(ByteArray(0)))
        assertThat(second.nightsImported).isEqualTo(0)
        assertThat(second.duplicatesSkipped).isEqualTo(2)
        val merged = h.timeline.merged(Interval(start.minusSeconds(86400 * 5), end))
        assertThat(merged).hasSize(2)
    }

    @Test fun importWithoutImporterReportsInsteadOfCrashing() = runTest {
        val report = h.sources.importSleepAsAndroidBackup(ByteArrayInputStream(ByteArray(0)))
        assertThat(report.nightsImported).isEqualTo(0)
        assertThat(report.warnings).isNotEmpty()
    }

}
