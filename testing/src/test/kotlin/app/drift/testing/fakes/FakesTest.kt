package app.drift.testing.fakes

import app.drift.model.CheckIn
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.SleepEpisode
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.Checkpoint
import app.drift.model.engine.AgeBand
import app.drift.model.engine.Schedule
import app.drift.model.engine.UserProfile
import app.drift.model.repository.AppSettings
import app.drift.model.repository.ReminderKind
import app.drift.model.repository.SourceInfo
import app.drift.model.repository.SyncReason
import app.drift.model.repository.ThemeMode
import app.drift.model.source.PullResult
import app.drift.model.source.SourceStatus
import app.drift.testing.fixtures.SnapshotFixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FakesTest {
    private val t0 = Instant.parse("2026-10-14T00:00:00Z")
    private fun ep(id: String, startH: Long, hours: Long, kind: EpisodeKind = EpisodeKind.MAIN) = SleepEpisode(
        EpisodeId(id), t0.plus(Duration.ofHours(startH)), t0.plus(Duration.ofHours(startH + hours)), ZoneId.of("UTC"), kind,
        origin = SourceRef(SourceId.HEALTH_CONNECT, id, QualityTier.WEARABLE_WRIST),
    )

    @Test fun `sleep repository filters by range and applies edits`() = runTest {
        val repo = FakeSleepRepository(listOf(ep("a", 0, 8), ep("b", 30, 8)))
        val day1 = Interval(t0, t0.plus(Duration.ofDays(1)))
        assertThat(repo.observeEpisodes(day1).first().map { it.id.value }).containsExactly("a")

        repo.saveUserEpisode(ep("c", 12, 1, EpisodeKind.NAP))
        assertThat(repo.observeEpisodes(day1).first().map { it.id.value }).containsExactly("a", "c").inOrder()
        assertThat(repo.observeEpisode(EpisodeId("c")).first()!!.userEdited).isTrue()

        repo.deleteEpisode(EpisodeId("a"))
        assertThat(repo.observeEpisode(EpisodeId("a")).first()).isNull()
        assertThat(repo.callsTo("saveUserEpisode")).hasSize(1)
        assertThat(repo.callsTo("deleteEpisode").single().args).containsExactly(EpisodeId("a"))
    }

    @Test fun `confirming clears the pending card and flags the episode`() = runTest {
        val pending = ep("p", 0, 7)
        val repo = FakeSleepRepository(listOf(pending), pending = pending)
        repo.confirmEpisode(EpisodeId("p"))
        assertThat(repo.observePendingConfirmation().first()).isNull()
        assertThat(repo.episodes.single().quality.flags).contains(QualityFlag.USER_CONFIRMED)
    }

    @Test fun `observers emit updates`() = runTest(UnconfinedTestDispatcher()) {
        val repo = FakeSleepRepository()
        val seen = mutableListOf<Int>()
        val flowJob = launch {
            repo.observeEpisodes(Interval(t0, t0.plus(Duration.ofDays(2)))).collect { seen += it.size }
        }
        repo.setEpisodes(listOf(ep("a", 0, 8)))
        repo.setEpisodes(listOf(ep("a", 0, 8), ep("b", 20, 6)))
        flowJob.cancel()
        assertThat(seen).containsExactly(0, 1, 2).inOrder()
    }

    @Test fun `annotations upsert by date and filter by range`() = runTest {
        val repo = FakeSleepRepository()
        val d = LocalDate.of(2026, 10, 14)
        repo.setAnnotation(DayAnnotation(d, freeDay = false))
        repo.setAnnotation(DayAnnotation(d, freeDay = true))
        repo.setAnnotation(DayAnnotation(d.plusDays(10), freeDay = true))
        assertThat(repo.observeAnnotations(d, d.plusDays(1)).first()).containsExactly(DayAnnotation(d, freeDay = true))
    }

    @Test fun `faults are thrown once and calls are still recorded`() = runTest {
        val repo = FakeProfileRepository()
        repo.failNext("saveProfile", IOException("disk full"))
        val profile = UserProfile(AgeBand.AGE_26_35, null, Schedule(emptySet(), null, null, null))
        assertThrows<IOException> { repo.saveProfile(profile) }
        assertThat(repo.profile).isNull()
        repo.saveProfile(profile)
        assertThat(repo.profile).isEqualTo(profile)
        assertThat(repo.callCount("saveProfile")).isEqualTo(2)
        repo.failAlways("setOnboardingComplete", IllegalStateException("nope"))
        assertThrows<IllegalStateException> { repo.setOnboardingComplete(true) }
        assertThrows<IllegalStateException> { repo.setOnboardingComplete(true) }
        repo.clearFaults()
        repo.setOnboardingComplete(true)
        assertThat(repo.observeOnboardingComplete().first()).isTrue()
    }

    @Test fun `observe faults surface on collection`() = runTest {
        val repo = FakeSleepRepository()
        repo.failNext("observeEpisodes", IOException("db"))
        assertThrows<IOException> { repo.observeEpisodes(Interval(t0, t0)).first() }
        assertThat(repo.observeEpisodes(Interval(t0, t0)).first()).isEmpty()
    }

    @Test fun `analysis refresh rotates previous and records the call`() = runTest {
        val a = SnapshotFixtures.full()
        val b = SnapshotFixtures.highDebt()
        val repo = FakeAnalysisRepository(latest = a)
        assertThrows<IllegalStateException> { FakeAnalysisRepository().refresh(t0) }
        repo.onRefresh = { b }
        assertThat(repo.refresh(t0)).isEqualTo(b)
        assertThat(repo.observeLatest().first()).isEqualTo(b)
        assertThat(repo.observePrevious().first()).isEqualTo(a)
        assertThat(repo.callsTo("refresh").single().args).containsExactly(t0)
    }

    @Test fun `source repository toggles sources, scripts sync and captures imports`() = runTest {
        val hc = SourceInfo(SourceId.HEALTH_CONNECT, "Health Connect", SourceStatus.Available, true, null, null, null)
        val repo = FakeSourceRepository(listOf(hc))
        repo.setEnabled(SourceId.HEALTH_CONNECT, false)
        assertThat(repo.observeSources().first().single().enabled).isFalse()
        assertThat(repo.sync(SyncReason.USER_PULL).newEpisodes).isEqualTo(0)
        repo.syncResult = { FakeSourceRepository.emptySyncReport.copy(newEpisodes = 3) }
        assertThat(repo.sync(SyncReason.APP_OPEN).newEpisodes).isEqualTo(3)
        assertThat(repo.callsTo("sync").map { it.args.single() }).containsExactly(SyncReason.USER_PULL, SyncReason.APP_OPEN).inOrder()
        repo.importSleepAsAndroidBackup(ByteArrayInputStream("abc".toByteArray()))
        assertThat(String(repo.importedPayloads.single())).isEqualTo("abc")
    }

    @Test fun `settings update applies the transform`() = runTest {
        val repo = FakeSettingsRepository()
        repo.update { it.copy(theme = ThemeMode.DARK, reminders = it.reminders + (ReminderKind.WIND_DOWN to false)) }
        val s = repo.observeSettings().first()
        assertThat(s.theme).isEqualTo(ThemeMode.DARK)
        assertThat(s.reminders[ReminderKind.WIND_DOWN]).isFalse()
        assertThat(repo.settings).isNotEqualTo(AppSettings())
    }

    @Test fun `check-ins filter by since and keep order`() = runTest {
        val repo = FakeCheckInRepository()
        repo.add(CheckIn.EnergyRating(t0.plusSeconds(100), 3))
        repo.add(CheckIn.EnergyRating(t0.minusSeconds(100), 2))
        assertThat(repo.observeCheckIns(t0).first()).hasSize(1)
        assertThat(repo.observeCheckIns(t0.minusSeconds(200)).first().map { it.at }).isInOrder()
        assertThat(repo.callCount("add")).isEqualTo(2)
    }

    @Test fun `backup export import wipe and local backups`() = runTest {
        var wiped = false
        val repo = FakeBackupRepository(onWipe = { wiped = true })
        val out = ByteArrayOutputStream()
        repo.exportAll(out)
        assertThat(out.toByteArray()).isEqualTo(repo.exportPayload)
        repo.importAll(ByteArrayInputStream(out.toByteArray()))
        assertThat(repo.importedPayloads.single()).isEqualTo(repo.exportPayload)
        val b1 = repo.createLocalBackup()
        val b2 = repo.createLocalBackup()
        assertThat(repo.listLocalBackups().map { it.id }).containsExactly(b1.id, b2.id)
        repo.restoreLocalBackup(b2.id)
        assertThat(repo.restoredId).isEqualTo(b2.id)
        assertThrows<IllegalArgumentException> { repo.restoreLocalBackup("missing") }
        repo.wipeAll()
        assertThat(wiped).isTrue()
        assertThat(repo.listLocalBackups()).isEmpty()
    }

    @Test fun `source adapter plays its script and reports problems through status`() = runTest {
        val adapter = FakeSourceAdapter()
        val r1 = PullResult(listOf(ep("x", 0, 8)), emptyList(), Checkpoint("1"))
        adapter.enqueue(r1)
        adapter.enqueueFailure(IOException("boom"))
        assertThat(adapter.pull(null)).isEqualTo(r1)
        assertThrows<IOException> { adapter.pull(Checkpoint("1")) }
        assertThat(adapter.pull(Checkpoint("1")).episodes).isEmpty() // default result once the script is empty
        assertThat(adapter.pullCheckpoints).containsExactly(null, Checkpoint("1"), Checkpoint("1")).inOrder()

        adapter.currentStatus = SourceStatus.NeedsPermission(setOf("sleep"))
        adapter.enqueue(r1)
        assertThat(adapter.status()).isEqualTo(SourceStatus.NeedsPermission(setOf("sleep")))
        assertThat(adapter.pull(Checkpoint("2")).episodes).isEmpty() // not available: no throw, empty result
    }
}
