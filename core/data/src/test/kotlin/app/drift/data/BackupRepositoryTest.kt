package app.drift.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.data.Fixtures.episode
import app.drift.data.db.toEntity
import app.drift.data.db.toRawEntity
import app.drift.model.ActivityType
import app.drift.model.AlarmKind
import app.drift.model.CheckIn
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.QualityTier
import app.drift.model.Signal
import app.drift.model.SourceId
import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.engine.Goals
import app.drift.model.engine.Schedule
import app.drift.model.engine.UserProfile
import app.drift.model.repository.ReminderKind
import app.drift.model.repository.ThemeMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupRepositoryTest {
    private lateinit var h: Harness
    private val t = Instant.parse("2026-10-08T22:00:00.123456789Z")

    @Before fun setUp() { h = Harness(ApplicationProvider.getApplicationContext<Context>()) }
    @After fun tearDown() = h.close()

    private suspend fun populate() {
        val a = episode("a", t, t.plus(Duration.ofHours(8)), stages = true, origin = "com.google.android.apps.fitness")
        val csv = episode("c", t.plusSeconds(100), t.plus(Duration.ofHours(8)), SourceId.SLEEP_AS_ANDROID, QualityTier.PHONE_APP)
        h.db.sourceEpisodes().upsert(listOf(a, csv, episode("n", t.plus(Duration.ofHours(15)), t.plus(Duration.ofHours(15)).plusSeconds(1800), kind = EpisodeKind.NAP)).map { it.toRawEntity(t) })
        val o = a.origin
        h.db.signals().upsert(
            listOf(
                Signal.HeartRateSample(o, t, 51.5), Signal.RestingHeartRate(o, t, 49.0), Signal.HrvSample(o, t, 62.125),
                Signal.StepBucket(o, Interval(t, t.plusSeconds(600)), 1234), Signal.ActivityBucket(o, Interval(t, t.plusSeconds(60)), ActivityType.RUNNING),
                Signal.ScreenEvent(o, t, true), Signal.MotionBucket(o, Interval(t, t.plusSeconds(60)), 0.3, 0.8), Signal.MotionBucket(o, Interval(t.plusSeconds(60), t.plusSeconds(120)), 0.1, null),
                Signal.LightBucket(o, Interval(t, t.plusSeconds(60)), 12.5), Signal.ChargingEvent(o, t, false), Signal.AlarmEvent(o, t, AlarmKind.SNOOZED),
            ).map { it.toEntity() },
        )
        h.sleep.saveUserEpisode(episode("u", t.plus(Duration.ofHours(30)), t.plus(Duration.ofHours(31)), SourceId.USER, kind = EpisodeKind.NAP))
        val merged = h.sleep.observeEpisodes(Interval(t.minusSeconds(86400), t.plus(Duration.ofHours(20)))).first()
        h.sleep.deleteEpisode(merged.last().id)
        h.sleep.setAnnotation(DayAnnotation(LocalDate.parse("2026-10-09"), true, null, t))
        h.checkIns.add(CheckIn.EnergyRating(t, 3))
        h.checkIns.add(CheckIn.ReactionTest(t.plusSeconds(5), 312.5, 2, 20))
        h.sources.setEnabled(SourceId.PHONE, false)
        h.analysis.refresh(t.plus(Duration.ofHours(10)))
        h.profile.saveProfile(
            UserProfile(AgeBand.AGE_36_50, ChronotypeAnswer.NEITHER, Schedule(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(6, 45), LocalTime.of(0, 30), null), Goals(Duration.ofMinutes(480), false)),
        )
        h.profile.setOnboardingComplete(true)
        h.settings.update { it.copy(theme = ThemeMode.DARK, use24HourClock = true, phoneDetectionEnabled = false, reminders = it.reminders + (ReminderKind.ENERGY_CHECK_IN to true)) }
    }

    private suspend fun export(): ByteArray = ByteArrayOutputStream().also { h.backups.exportAll(it) }.toByteArray()

    @Test fun exportWipeImportRoundTripsEverything() = runTest {
        populate()
        val before = export()
        val profileBefore = h.profile.observeProfile().first()
        val settingsBefore = h.settings.observeSettings().first()
        val latestBefore = h.analysis.observeLatest().first()
        val range = Interval(t.minusSeconds(86400), t.plus(Duration.ofDays(3)))
        val mergedBefore = h.sleep.observeEpisodes(range).first()

        h.backups.wipeAll()
        assertThat(h.db.sourceEpisodes().all()).isEmpty()
        assertThat(h.profile.observeProfile().first()).isNull()
        assertThat(h.profile.observeOnboardingComplete().first()).isFalse()
        assertThat(h.analysis.observeLatest().first()).isNull()
        assertThat(h.checkIns.observeCheckIns(Instant.EPOCH).first()).isEmpty()

        h.backups.importAll(ByteArrayInputStream(before))

        assertThat(String(export())).isEqualTo(String(before))
        assertThat(h.profile.observeProfile().first()).isEqualTo(profileBefore)
        assertThat(h.settings.observeSettings().first()).isEqualTo(settingsBefore)
        assertThat(h.profile.observeOnboardingComplete().first()).isTrue()
        assertThat(h.analysis.observeLatest().first()).isEqualTo(latestBefore)
        assertThat(h.sleep.observeEpisodes(range).first()).isEqualTo(mergedBefore)
        assertThat(h.db.signals().count()).isEqualTo(11)
        assertThat(h.checkIns.observeCheckIns(Instant.EPOCH).first()).hasSize(2)
    }

    @Test fun importReplacesExistingDataRatherThanMerging() = runTest {
        populate()
        val snapshot = export()
        h.db.sourceEpisodes().upsert(listOf(episode("extra", t.plus(Duration.ofDays(5)), t.plus(Duration.ofDays(5)).plusSeconds(9000)).toRawEntity(t)))
        h.backups.importAll(ByteArrayInputStream(snapshot))
        assertThat(h.db.sourceEpisodes().all().map { it.recordId }).doesNotContain("extra")
    }

    @Test fun rejectsGarbageAndNewerFormats() = runTest {
        populate()
        val before = export()
        for (bad in listOf("not json", "{}", String(before).replaceFirst("\"formatVersion\":1", "\"formatVersion\":99"))) {
            val failed = runCatching { h.backups.importAll(ByteArrayInputStream(bad.toByteArray())) }.exceptionOrNull()
            assertThat(failed).isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThat(String(export())).isEqualTo(String(before)) // failed imports left data untouched
    }

    @Test fun localBackupsAreEncryptedAndRolledToSeven() = runTest {
        populate()
        repeat(9) { h.clock.now = h.clock.now.plusSeconds(60); h.backups.createLocalBackup() }
        val list = h.backups.listLocalBackups()
        assertThat(list).hasSize(7)
        assertThat(list.map { it.createdAt }).isInOrder(Comparator.reverseOrder<Instant>())
        val raw = java.io.File(h.dir, "backups").listFiles()!!.first { it.name.endsWith(".drb") }.readBytes()
        assertThat(String(raw, Charsets.ISO_8859_1)).doesNotContain("formatVersion")
        assertThat(String(raw, Charsets.ISO_8859_1)).doesNotContain("healthconnect")
    }

    @Test fun restoreLocalBackupBringsBackWipedData() = runTest {
        populate()
        val before = export()
        val backup = h.backups.createLocalBackup()
        h.backups.wipeAll()
        // wipeAll also removes local backups (they contain user data), so create one, then wipe tables only.
        h.backups.importAll(ByteArrayInputStream(before))
        val again = h.backups.createLocalBackup()
        h.db.sourceEpisodes().clear(); h.db.userEpisodes().clear()
        h.backups.restoreLocalBackup(again.id)
        assertThat(String(export())).isEqualTo(String(before))
        assertThat(backup.sizeBytes).isGreaterThan(0)
    }

    @Test fun restoreOfTamperedBackupFailsAndKeepsData() = runTest {
        populate()
        val b = h.backups.createLocalBackup()
        val f = java.io.File(h.dir, "backups/${b.id}.drb")
        val bytes = f.readBytes(); bytes[bytes.size - 3] = (bytes[bytes.size - 3] + 1).toByte(); f.writeBytes(bytes)
        val before = export()
        assertThat(runCatching { h.backups.restoreLocalBackup(b.id) }.isFailure).isTrue()
        assertThat(String(export())).isEqualTo(String(before))
    }

}
