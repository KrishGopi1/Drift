package app.drift.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.drift.model.SourceId
import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.repository.AppSettings
import app.drift.model.repository.ImportReport
import app.drift.model.repository.SyncReason
import app.drift.model.source.SourceStatus
import app.drift.model.ui.ScreenState
import app.drift.testing.fakes.FakeAnalysisRepository
import app.drift.testing.fakes.FakeProfileRepository
import app.drift.testing.fakes.FakeSettingsRepository
import app.drift.testing.fakes.FakeSourceRepository
import app.drift.testing.fixtures.FixtureContext
import app.drift.testing.fixtures.SnapshotFixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime

class OnboardingViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val ctx = FixtureContext.IST
    private val profiles = FakeProfileRepository()
    private val sources = FakeSourceRepository(Sources.allGood)
    private val settings = FakeSettingsRepository()
    private val analysis = FakeAnalysisRepository(SnapshotFixtures.priorsOnlyDay0(ctx))

    private fun vm(saved: Map<String, Any?> = emptyMap(), sdk: Int = 35) =
        OnboardingViewModel(profiles, sources, settings, analysis, SavedStateHandle(saved)).also {
            it.sdkInt = sdk
            it.clock = ctx.clock
        }

    private fun OnboardingViewModel.model(): OnboardingModel = (state.value as ScreenState.Ready).model

    @Test fun startsLoadingThenReadyOnWelcome() = runTest {
        val vm = vm()
        vm.state.test {
            var item = awaitItem()
            if (item is ScreenState.Loading) item = awaitItem()
            assertThat((item as ScreenState.Ready).model.step).isEqualTo(OnboardingStep.WELCOME)
        }
    }

    @Test fun walksThroughTheFlowAndFinishes() = runTest {
        val vm = vm()
        vm.state.test {
            skipItems(1)
            repeat(4) { vm.next() }
            assertThat(vm.model().step).isEqualTo(OnboardingStep.SOURCES)
            assertThat(profiles.callsTo("saveProfile")).hasSize(1)
            vm.next()
            assertThat(vm.model().step).isEqualTo(OnboardingStep.PERMISSIONS)
            assertThat(vm.model().permissionPage).isEqualTo(PermissionPage.HEALTH_CONNECT)
            repeat(4) { vm.advancePermission() }
            assertThat(vm.model().step).isEqualTo(OnboardingStep.IMPORT)
            assertThat(sources.callsTo("sync").single().args).containsExactly(SyncReason.FIRST_RUN)
            vm.next()
            assertThat(vm.model().step).isEqualTo(OnboardingStep.REVEAL)
            assertThat(analysis.callsTo("refresh")).hasSize(1)
            assertThat(vm.model().reveal).isInstanceOf(Reveal.Ready::class.java)
            vm.finished.test {
                vm.next()
                awaitItem()
            }
            assertThat(profiles.onboardingComplete).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun savesProfileFromAnswers() = runTest {
        val vm = vm(mapOf("step" to OnboardingStep.ABOUT_YOU.ordinal))
        vm.state.test {
            skipItems(1)
            vm.toggleWorkday(DayOfWeek.FRIDAY)
            vm.setWake(6 * 60 + 30)
            vm.setFreeBed(24 * 60 + 15)
            vm.setAgeBand(AgeBand.AGE_51_64)
            vm.setChronotype(ChronotypeAnswer.DEFINITELY_EVENING)
            vm.next()
            val p = profiles.profile!!
            assertThat(p.ageBand).isEqualTo(AgeBand.AGE_51_64)
            assertThat(p.chronotype).isEqualTo(ChronotypeAnswer.DEFINITELY_EVENING)
            assertThat(DayOfWeek.FRIDAY in p.schedule.workdays).isFalse()
            assertThat(p.schedule.wakeTime).isEqualTo(LocalTime.of(6, 30))
            assertThat(p.schedule.freeDayBedtime).isEqualTo(LocalTime.of(0, 15))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun noWorkdaysMeansNoFixedWakeTime() {
        val p = OnboardingViewModel.buildProfile(ProfileForm(workdays = emptySet()))
        assertThat(p.schedule.wakeTime).isNull()
    }

    @Test fun saveFailureStaysOnStepWithError() = runTest {
        profiles.failNext("saveProfile", IOException("disk full"))
        val vm = vm(mapOf("step" to OnboardingStep.ABOUT_YOU.ordinal))
        vm.state.test {
            skipItems(1)
            vm.next()
            assertThat(vm.model().step).isEqualTo(OnboardingStep.ABOUT_YOU)
            assertThat(vm.model().error).isEqualTo(StepError(ErrorKind.SAVE, "disk full"))
            vm.next()
            assertThat(vm.model().step).isEqualTo(OnboardingStep.SOURCES)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun progressSurvivesRecreation() = runTest {
        val handle = SavedStateHandle()
        val first = OnboardingViewModel(profiles, sources, settings, analysis, handle).also { it.sdkInt = 35 }
        first.state.test {
            skipItems(1)
            first.next(); first.next()
            first.toggleWorkday(DayOfWeek.MONDAY)
            cancelAndIgnoreRemainingEvents()
        }
        val second = OnboardingViewModel(profiles, sources, settings, analysis, handle).also { it.sdkInt = 35 }
        second.state.test {
            skipItems(1)
            val m = second.model()
            assertThat(m.step).isEqualTo(OnboardingStep.SCHEDULE)
            assertThat(DayOfWeek.MONDAY in m.form.workdays).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun backWalksBackwards() = runTest {
        val vm = vm(mapOf("step" to OnboardingStep.SCHEDULE.ordinal))
        vm.state.test {
            skipItems(1)
            vm.back()
            assertThat(vm.model().step).isEqualTo(OnboardingStep.HOW_IT_WORKS)
            vm.back(); vm.back()
            assertThat(vm.model().step).isEqualTo(OnboardingStep.WELCOME)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun sourceToggleUpdatesRepositoryAndPhoneSetting() = runTest {
        val vm = vm()
        vm.state.test {
            skipItems(1)
            vm.setSourceEnabled(SourceId.PHONE, false)
            assertThat(sources.callsTo("setEnabled").single().args).containsExactly(SourceId.PHONE, false).inOrder()
            assertThat(settings.settings.phoneDetectionEnabled).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun permissionPagesFollowChoices() = runTest {
        settings.set(AppSettings(phoneDetectionEnabled = false))
        sources.setSources(listOf(Sources.healthConnect(enabled = false), Sources.phone(enabled = false)))
        val vm = vm(sdk = 30)
        vm.state.test {
            skipItems(1)
            assertThat(vm.model().permissionPages).isEmpty()
            vm.model()
            cancelAndIgnoreRemainingEvents()
        }
        val vm2 = vm(mapOf("step" to OnboardingStep.SOURCES.ordinal), sdk = 30)
        vm2.state.test {
            skipItems(1)
            vm2.next()
            assertThat(vm2.model().step).isEqualTo(OnboardingStep.IMPORT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun usePhoneDetectionOnlyDisablesHealthConnect() = runTest {
        sources.setSources(listOf(Sources.healthConnect(SourceStatus.NotInstalled), Sources.phone(enabled = false)))
        val vm = vm(mapOf("step" to OnboardingStep.PERMISSIONS.ordinal))
        vm.state.test {
            skipItems(1)
            vm.usePhoneDetectionOnly()
            assertThat(sources.sources.first { it.id == SourceId.HEALTH_CONNECT }.enabled).isFalse()
            assertThat(settings.settings.phoneDetectionEnabled).isTrue()
            assertThat(vm.model().permissionPage).isEqualTo(PermissionPage.ACTIVITY_RECOGNITION)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun notificationResultAdvances() = runTest {
        val vm = vm(mapOf("step" to OnboardingStep.PERMISSIONS.ordinal, "permission" to PermissionPage.USAGE_ACCESS.ordinal))
        vm.state.test {
            skipItems(1)
            vm.advancePermission()
            assertThat(vm.model().permissionPage).isEqualTo(PermissionPage.NOTIFICATIONS)
            vm.onNotificationsResult(false)
            assertThat(vm.model().notificationsGranted).isFalse()
            assertThat(vm.model().step).isEqualTo(OnboardingStep.IMPORT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun syncFailureShowsFailedAndRetryRuns() = runTest {
        sources.failNext("sync", IOException("offline"))
        val vm = vm(mapOf("step" to OnboardingStep.IMPORT.ordinal))
        vm.state.test {
            skipItems(1)
            assertThat(vm.model().history).isEqualTo(HistoryImport.Failed("offline"))
            vm.startImportAgain()
            assertThat(vm.model().history).isInstanceOf(HistoryImport.Done::class.java)
            assertThat(sources.callsTo("sync")).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun zeroNightsImported() = runTest {
        val vm = vm(mapOf("step" to OnboardingStep.IMPORT.ordinal))
        vm.state.test {
            skipItems(1)
            assertThat(vm.model().nightsFound).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun backupImportStreamsAndReports() = runTest {
        val report = ImportReport(5, 1, 0, null, null, emptyList())
        sources.importResult = report
        val vm = vm(mapOf("step" to OnboardingStep.IMPORT.ordinal))
        vm.state.test {
            skipItems(1)
            vm.importBackup { "abc".byteInputStream() }
            assertThat(String(sources.importedPayloads.single())).isEqualTo("abc")
            assertThat(vm.model().backup).isEqualTo(BackupImport.Done(report))
            assertThat(vm.model().nightsFound).isEqualTo(5)
            vm.importBackup { null }
            assertThat(vm.model().backup).isEqualTo(BackupImport.Failed(null))
            sources.failNext("importSleepAsAndroidBackup", IOException("bad"))
            vm.importBackup { "x".byteInputStream() }
            assertThat(vm.model().backup).isEqualTo(BackupImport.Failed("bad"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun revealFailureThenRetry() = runTest {
        analysis.failNext("refresh", IllegalStateException("engine"))
        val vm = vm(mapOf("step" to OnboardingStep.REVEAL.ordinal))
        vm.state.test {
            skipItems(1)
            assertThat(vm.model().reveal).isEqualTo(Reveal.Failed("engine"))
            vm.loadReveal()
            val r = vm.model().reveal as Reveal.Ready
            assertThat(r.tier).isEqualTo(SnapshotFixtures.priorsOnlyDay0(ctx).dataSufficiency.tier)
            assertThat(r.day).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun finishFailureKeepsStepAndRetries() = runTest {
        profiles.failNext("setOnboardingComplete", IOException("full"))
        val vm = vm(mapOf("step" to OnboardingStep.REVEAL.ordinal))
        vm.state.test {
            skipItems(1)
            vm.finish()
            assertThat(vm.model().error?.kind).isEqualTo(ErrorKind.FINISH)
            assertThat(profiles.onboardingComplete).isFalse()
            vm.finished.test { vm.finish(); awaitItem() }
            assertThat(profiles.onboardingComplete).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun cancelImportMarksCancelled() = runTest {
        val vm = vm(mapOf("step" to OnboardingStep.IMPORT.ordinal))
        vm.state.test {
            skipItems(1)
            vm.cancelImport()
            assertThat(vm.model().history).isEqualTo(HistoryImport.Cancelled)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
