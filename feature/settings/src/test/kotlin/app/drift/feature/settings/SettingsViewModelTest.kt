package app.drift.feature.settings

import app.cash.turbine.test
import app.drift.model.SourceId
import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.engine.Goals
import app.drift.model.engine.Schedule
import app.drift.model.engine.UserProfile
import app.drift.model.repository.ImportReport
import app.drift.model.repository.ReminderKind
import app.drift.model.repository.SyncReason
import app.drift.model.repository.SyncReport
import app.drift.model.repository.ThemeMode
import app.drift.model.ui.ScreenState
import app.drift.testing.fakes.FakeBackupRepository
import app.drift.testing.fakes.FakeProfileRepository
import app.drift.testing.fakes.FakeSettingsRepository
import app.drift.testing.fakes.FakeSourceRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val sources = FakeSourceRepository(Sources.allGood)
    private val settings = FakeSettingsRepository()
    private val backups = FakeBackupRepository()
    private val profiles = FakeProfileRepository(onboardingComplete = true)

    private fun vm() = SettingsViewModel(sources, settings, backups, profiles)
    private fun SettingsViewModel.model() = (state.value as ScreenState.Ready).model

    @Test fun exposesLoadingThenReady() = runTest {
        val vm = vm()
        vm.state.test {
            var item = awaitItem()
            if (item is ScreenState.Loading) item = awaitItem()
            assertThat((item as ScreenState.Ready).model.sources).hasSize(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun syncNowUsesUserPull() = runTest {
        sources.syncResult = { SyncReport(Instant.EPOCH, Instant.EPOCH, 3, 1, 0, emptyMap()) }
        val vm = vm()
        vm.state.test {
            skipItems(1)
            vm.syncNow()
            assertThat(sources.callsTo("sync").single().args).containsExactly(SyncReason.USER_PULL)
            assertThat(vm.model().notice).isEqualTo(Notice(NoticeKind.SYNCED, "4"))
            assertThat(vm.model().syncing).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun syncFailureAndSourceErrorsAreReported() = runTest {
        val vm = vm()
        vm.state.test {
            skipItems(1)
            sources.failNext("sync", IOException("offline"))
            vm.syncNow()
            assertThat(vm.model().notice).isEqualTo(Notice(NoticeKind.SYNC_FAILED, "offline"))
            sources.syncResult = { SyncReport(Instant.EPOCH, Instant.EPOCH, 0, 0, 0, mapOf(SourceId.HEALTH_CONNECT to "denied")) }
            vm.syncNow()
            assertThat(vm.model().notice?.kind).isEqualTo(NoticeKind.SYNC_SOURCE_ERRORS)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun sourceToggleAlsoUpdatesPhoneSetting() = runTest {
        val vm = vm()
        vm.state.test {
            skipItems(1)
            vm.setSourceEnabled(SourceId.SLEEP_AS_ANDROID, true)
            assertThat(settings.callsTo("update")).isEmpty()
            vm.setSourceEnabled(SourceId.PHONE, false)
            assertThat(settings.settings.phoneDetectionEnabled).isFalse()
            assertThat(sources.callsTo("setEnabled")).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun preferencesUpdateSettings() = runTest {
        val vm = vm()
        vm.state.test {
            skipItems(1)
            vm.setTheme(ThemeMode.DARK)
            vm.setUse24Hour(true)
            vm.setReminder(ReminderKind.WIND_DOWN, false)
            assertThat(settings.settings.theme).isEqualTo(ThemeMode.DARK)
            assertThat(settings.settings.use24HourClock).isTrue()
            assertThat(settings.settings.reminders[ReminderKind.WIND_DOWN]).isFalse()
            assertThat(settings.settings.reminders[ReminderKind.MORNING_LIGHT]).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun exportWritesStreamAndReports() = runTest {
        val vm = vm()
        vm.state.test {
            skipItems(1)
            val out = ByteArrayOutputStream()
            vm.export { out }
            assertThat(out.toString()).isEqualTo("drift-fake-export-v1")
            assertThat(vm.model().notice).isEqualTo(Notice(NoticeKind.EXPORTED))
            backups.failNext("exportAll", IOException("full"))
            vm.export { ByteArrayOutputStream() }
            assertThat(vm.model().notice).isEqualTo(Notice(NoticeKind.EXPORT_FAILED, "full"))
            vm.export { null }
            assertThat(vm.model().notice?.kind).isEqualTo(NoticeKind.EXPORT_FAILED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun importReportAndFailure() = runTest {
        val report = ImportReport(9, 2, 1, null, null, listOf("w"))
        backups.importResult = report
        val vm = vm()
        vm.state.test {
            skipItems(1)
            vm.importDriftExport { "payload".byteInputStream() }
            assertThat(String(backups.importedPayloads.single())).isEqualTo("payload")
            assertThat(vm.model().importOutcome).isEqualTo(ImportOutcome.Success(ImportKind.DRIFT_EXPORT, report))
            vm.dismissImportOutcome()
            assertThat(vm.model().importOutcome).isNull()
            backups.failNext("importAll", IOException("corrupt"))
            vm.importDriftExport { "x".byteInputStream() }
            assertThat(vm.model().importOutcome).isEqualTo(ImportOutcome.Failure(ImportKind.DRIFT_EXPORT, "corrupt"))
            vm.importDriftExport { null }
            assertThat(vm.model().importOutcome).isEqualTo(ImportOutcome.Failure(ImportKind.DRIFT_EXPORT, null))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun sleepAsAndroidImportStreams() = runTest {
        sources.importResult = ImportReport(4, 0, 0, null, null, emptyList())
        val vm = vm()
        vm.state.test {
            skipItems(1)
            vm.importSleepAsAndroid { "saa".byteInputStream() }
            assertThat(String(sources.importedPayloads.single())).isEqualTo("saa")
            assertThat((vm.model().importOutcome as ImportOutcome.Success).kind).isEqualTo(ImportKind.SLEEP_AS_ANDROID)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun backupListCreateAndRestore() = runTest {
        val vm = vm()
        vm.state.test {
            skipItems(1)
            assertThat(vm.model().backups).isEmpty()
            vm.createBackup()
            assertThat(vm.model().backups).hasSize(1)
            assertThat(vm.model().notice).isEqualTo(Notice(NoticeKind.BACKUP_CREATED))
            val id = vm.model().backups.single().id
            vm.restoreBackup(id)
            assertThat(backups.restoredId).isEqualTo(id)
            assertThat(vm.model().notice).isEqualTo(Notice(NoticeKind.RESTORED))
            vm.restoreBackup("nope")
            assertThat(vm.model().notice?.kind).isEqualTo(NoticeKind.RESTORE_FAILED)
            vm.dismissNotice()
            assertThat(vm.model().notice).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun resetWipesMarksOnboardingIncompleteAndSignals() = runTest {
        val vm = vm()
        vm.state.test {
            skipItems(1)
            vm.resetComplete.test {
                vm.resetAll()
                awaitItem()
            }
            assertThat(backups.callsTo("wipeAll")).hasSize(1)
            assertThat(profiles.onboardingComplete).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun resetFailureKeepsScreenAndReports() = runTest {
        backups.failNext("wipeAll", IOException("locked"))
        val vm = vm()
        vm.state.test {
            skipItems(1)
            vm.resetAll()
            assertThat(vm.model().notice).isEqualTo(Notice(NoticeKind.RESET_FAILED, "locked"))
            assertThat(profiles.onboardingComplete).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Schedule ----

    private val existing = UserProfile(
        AgeBand.AGE_36_50, ChronotypeAnswer.NEITHER,
        Schedule(setOf(DayOfWeek.MONDAY), LocalTime.of(6, 45), LocalTime.of(23, 0), LocalTime.of(8, 30)),
        Goals(targetSleep = Duration.ofHours(8)),
    )

    @Test fun scheduleLoadsEditsAndSavesKeepingGoals() = runTest {
        val p = FakeProfileRepository(existing)
        val svm = ScheduleViewModel(p)
        svm.state.test {
            val ready = expectMostRecentItem() as ScreenState.Ready
            assertThat(ready.model.form.wakeMinutes).isEqualTo(6 * 60 + 45)
            svm.toggleWorkday(DayOfWeek.TUESDAY)
            svm.setWake(7 * 60)
            svm.setFreeBed(-30)
            svm.setFreeWake(9 * 60)
            svm.setAgeBand(AgeBand.AGE_65_PLUS)
            svm.setChronotype(null)
            svm.saved.test {
                svm.save()
                awaitItem()
            }
            val saved = p.profile!!
            assertThat(saved.schedule.workdays).containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
            assertThat(saved.schedule.wakeTime).isEqualTo(LocalTime.of(7, 0))
            assertThat(saved.schedule.freeDayBedtime).isEqualTo(LocalTime.of(23, 30))
            assertThat(saved.ageBand).isEqualTo(AgeBand.AGE_65_PLUS)
            assertThat(saved.chronotype).isNull()
            assertThat(saved.goals.targetSleep).isEqualTo(Duration.ofHours(8))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun scheduleWithoutProfileStartsFromDefaultsAndReportsSaveError() = runTest {
        val p = FakeProfileRepository(null)
        p.failNext("saveProfile", IOException("disk"))
        val svm = ScheduleViewModel(p)
        assertThat(((svm.state.value) as ScreenState.Ready).model.form).isEqualTo(ScheduleForm.Default)
        svm.save()
        assertThat(((svm.state.value) as ScreenState.Ready).model.saveError).isEqualTo("disk")
        svm.setWake(8 * 60)
        assertThat(((svm.state.value) as ScreenState.Ready).model.saveError).isNull()
    }
}
