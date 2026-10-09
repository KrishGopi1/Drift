package app.drift.feature.onboarding

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drift.model.SourceId
import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.engine.Schedule
import app.drift.model.engine.UserProfile
import app.drift.model.repository.AnalysisRepository
import app.drift.model.repository.AppSettings
import app.drift.model.repository.ProfileRepository
import app.drift.model.repository.SettingsRepository
import app.drift.model.repository.SourceInfo
import app.drift.model.repository.SourceRepository
import app.drift.model.repository.SyncReason
import app.drift.model.ui.ScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

/**
 * Drives the onboarding flow. The current step, permission page and answers live in [SavedStateHandle], so they
 * survive rotation and process death. The history import, backup import and reveal are re-run if the process was
 * killed while they were showing.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val sources: SourceRepository,
    private val settings: SettingsRepository,
    private val analysis: AnalysisRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {

    /** Test seams. Production uses the defaults. */
    internal var sdkInt: Int = Build.VERSION.SDK_INT
    internal var clock: Clock = Clock.systemDefaultZone()

    private data class Local(
        val step: OnboardingStep,
        val permissionPage: PermissionPage,
        val form: ProfileForm,
        val notificationsGranted: Boolean?,
        val history: HistoryImport = HistoryImport.Idle,
        val backup: BackupImport = BackupImport.Idle,
        val reveal: Reveal = Reveal.Idle,
        val error: StepError? = null,
        val busy: Boolean = false,
    )

    private val local = MutableStateFlow(restore())
    private val sourcesFlow = sources.observeSources()
    private val settingsFlow = settings.observeSettings()
    private val latestSources = sourcesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val latestSettings = settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    private val retry = MutableStateFlow(0)
    private val finishedEvents = Channel<Unit>(Channel.BUFFERED)
    private var importJob: Job? = null

    /** Fires once after setup is saved as complete. */
    val finished: Flow<Unit> = finishedEvents.receiveAsFlow()

    val state: StateFlow<ScreenState<OnboardingModel>> = retry.flatMapLatest {
        combine(local, sourcesFlow, settingsFlow) { l, srcs, app ->
            ScreenState.Ready(
                OnboardingModel(
                    step = l.step, form = l.form, sources = srcs, phoneDetection = app.phoneDetectionEnabled,
                    use24h = app.use24HourClock, permissionPage = l.permissionPage,
                    permissionPages = pagesFor(srcs, app), notificationsGranted = l.notificationsGranted,
                    history = l.history, backup = l.backup, reveal = l.reveal, error = l.error, busy = l.busy,
                ),
            ) as ScreenState<OnboardingModel>
        }.catch { emit(ScreenState.Error(it.message ?: "", retryable = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    init {
        when (local.value.step) {
            OnboardingStep.IMPORT -> startImport()
            OnboardingStep.REVEAL -> loadReveal()
            else -> Unit
        }
    }

    fun retryLoad() { retry.update { it + 1 } }

    // ---- Navigation --------------------------------------------------------------------------------------------

    fun next() {
        val l = local.value
        when (l.step) {
            OnboardingStep.ABOUT_YOU -> saveProfileAndContinue()
            OnboardingStep.SOURCES -> enterPermissions()
            OnboardingStep.PERMISSIONS -> advancePermission()
            OnboardingStep.IMPORT -> goTo(OnboardingStep.REVEAL).also { if (local.value.reveal is Reveal.Idle) loadReveal() }
            OnboardingStep.REVEAL -> finish()
            else -> goTo(OnboardingStep.entries[l.step.ordinal + 1])
        }
    }

    fun back() {
        val l = local.value
        when (l.step) {
            OnboardingStep.WELCOME -> Unit
            OnboardingStep.PERMISSIONS -> {
                val before = permissionPages().lastOrNull { it.ordinal < l.permissionPage.ordinal }
                if (before != null) setPermissionPage(before) else goTo(OnboardingStep.SOURCES)
            }
            OnboardingStep.IMPORT -> {
                if (l.history is HistoryImport.Running) cancelImport()
                val last = permissionPages().lastOrNull()
                if (last != null) { setPermissionPage(last); goTo(OnboardingStep.PERMISSIONS) } else goTo(OnboardingStep.SOURCES)
            }
            else -> goTo(OnboardingStep.entries[l.step.ordinal - 1])
        }
    }

    private fun goTo(step: OnboardingStep) {
        local.update { it.copy(step = step, error = null) }
        saved[KEY_STEP] = step.ordinal
    }

    private fun setPermissionPage(page: PermissionPage) {
        local.update { it.copy(permissionPage = page, error = null) }
        saved[KEY_PERMISSION] = page.ordinal
    }

    // ---- Answers -----------------------------------------------------------------------------------------------

    fun toggleWorkday(day: DayOfWeek) = updateForm { f ->
        f.copy(workdays = if (day in f.workdays) f.workdays - day else f.workdays + day)
    }
    fun setWake(minutes: Int) = updateForm { it.copy(wakeMinutes = minutes.mod(MINUTES_PER_DAY)) }
    fun setFreeBed(minutes: Int) = updateForm { it.copy(freeBedMinutes = minutes.mod(MINUTES_PER_DAY)) }
    fun setFreeWake(minutes: Int) = updateForm { it.copy(freeWakeMinutes = minutes.mod(MINUTES_PER_DAY)) }
    fun setAgeBand(band: AgeBand) = updateForm { it.copy(ageBand = band) }
    fun setChronotype(answer: ChronotypeAnswer?) = updateForm { it.copy(chronotype = answer) }

    private fun updateForm(transform: (ProfileForm) -> ProfileForm) {
        local.update { it.copy(form = transform(it.form)) }
        val f = local.value.form
        saved[KEY_WORKDAYS] = f.workdays.fold(0) { acc, d -> acc or (1 shl d.ordinal) }
        saved[KEY_WAKE] = f.wakeMinutes
        saved[KEY_FREE_BED] = f.freeBedMinutes
        saved[KEY_FREE_WAKE] = f.freeWakeMinutes
        saved[KEY_AGE] = f.ageBand.ordinal
        saved[KEY_CHRONO] = f.chronotype?.ordinal ?: -1
    }

    private fun saveProfileAndContinue() {
        val form = local.value.form
        viewModelScope.launch {
            local.update { it.copy(busy = true, error = null) }
            try {
                profiles.saveProfile(buildProfile(form))
                local.update { it.copy(busy = false) }
                goTo(OnboardingStep.SOURCES)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(busy = false, error = StepError(ErrorKind.SAVE, e.message.orEmpty())) }
            }
        }
    }

    // ---- Sources and permissions -------------------------------------------------------------------------------

    fun setSourceEnabled(id: SourceId, enabled: Boolean) {
        viewModelScope.launch { guarded { applySourceEnabled(id, enabled) } }
    }

    private suspend fun applySourceEnabled(id: SourceId, enabled: Boolean) {
        sources.setEnabled(id, enabled)
        if (id == SourceId.PHONE) settings.update { it.copy(phoneDetectionEnabled = enabled) }
    }

    /** Health Connect is unavailable: switch it off, turn phone detection on and carry on with the phone's own signals. */
    fun usePhoneDetectionOnly() {
        viewModelScope.launch {
            guarded {
                applySourceEnabled(SourceId.HEALTH_CONNECT, false)
                applySourceEnabled(SourceId.PHONE, true)
                setPermissionPage(PermissionPage.ACTIVITY_RECOGNITION)
            }
        }
    }

    fun onNotificationsResult(granted: Boolean) {
        local.update { it.copy(notificationsGranted = granted) }
        advancePermission()
    }

    /** Records an already-granted notification permission without moving on. */
    fun noteNotificationsGranted(granted: Boolean) {
        if (local.value.notificationsGranted != granted) local.update { it.copy(notificationsGranted = granted) }
    }

    /** "Not now" / "Continue" on a permission page. */
    fun advancePermission() {
        val after = permissionPages().firstOrNull { it.ordinal > local.value.permissionPage.ordinal }
        if (after != null) setPermissionPage(after) else goTo(OnboardingStep.IMPORT).also { startImportIfNeeded() }
    }

    private fun enterPermissions() {
        val first = permissionPages().firstOrNull()
        if (first == null) {
            goTo(OnboardingStep.IMPORT)
            startImportIfNeeded()
        } else {
            setPermissionPage(first)
            goTo(OnboardingStep.PERMISSIONS)
        }
    }

    private fun permissionPages(): List<PermissionPage> = pagesFor(latestSources.value, latestSettings.value)

    private fun pagesFor(srcs: List<SourceInfo>, app: AppSettings): List<PermissionPage> {
        val hcEnabled = srcs.any { it.id == SourceId.HEALTH_CONNECT && it.enabled }
        return PermissionPage.entries.filter {
            when (it) {
                PermissionPage.HEALTH_CONNECT -> hcEnabled
                PermissionPage.ACTIVITY_RECOGNITION, PermissionPage.USAGE_ACCESS -> app.phoneDetectionEnabled
                PermissionPage.NOTIFICATIONS -> sdkInt >= Build.VERSION_CODES.TIRAMISU
            }
        }
    }

    // ---- History import ----------------------------------------------------------------------------------------

    private fun startImportIfNeeded() {
        val h = local.value.history
        if (h is HistoryImport.Done || h is HistoryImport.Running) return
        startImport()
    }

    fun startImport() {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            local.update { it.copy(history = HistoryImport.Running(null)) }
            val progress = launch {
                sources.observeSyncProgress().collect { p ->
                    local.update { cur -> if (cur.history is HistoryImport.Running) cur.copy(history = HistoryImport.Running(p)) else cur }
                }
            }
            try {
                val report = sources.sync(SyncReason.FIRST_RUN)
                val names = latestSources.value.associate { it.id to it.displayName }
                local.update {
                    it.copy(
                        history = HistoryImport.Done(
                            newSessions = report.newEpisodes + report.updatedEpisodes,
                            errors = report.errors.mapKeys { (id, _) -> names[id] ?: id.value },
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(history = HistoryImport.Failed(e.message.orEmpty())) }
            } finally {
                progress.cancel()
            }
        }
    }

    fun startImportAgain() {
        importJob = null
        startImport()
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        local.update { it.copy(history = HistoryImport.Cancelled) }
    }

    /** Streams a Sleep as Android backup into the importer. [open] returns null when the file cannot be opened. */
    fun importBackup(open: () -> InputStream?) {
        viewModelScope.launch {
            local.update { it.copy(backup = BackupImport.Running) }
            try {
                val stream = open()
                if (stream == null) {
                    local.update { it.copy(backup = BackupImport.Failed(null)) }
                } else {
                    val report = stream.use { sources.importSleepAsAndroidBackup(it) }
                    local.update { it.copy(backup = BackupImport.Done(report)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(backup = BackupImport.Failed(e.message.orEmpty())) }
            }
        }
    }

    // ---- Reveal and finish -------------------------------------------------------------------------------------

    fun loadReveal() {
        viewModelScope.launch {
            local.update { it.copy(reveal = Reveal.Loading) }
            try {
                val now = Instant.now(clock)
                val snapshot = analysis.refresh(now)
                val today = now.atZone(clock.zone).toLocalDate()
                val days = snapshot.energyCurve.days
                val day = snapshot.energyCurve.forDate(today) ?: days.getOrNull(days.size / 2)
                local.update {
                    it.copy(
                        reveal = Reveal.Ready(
                            day = day, tier = snapshot.dataSufficiency.tier,
                            nightsNeeded = snapshot.dataSufficiency.nightsUntilPersonalNeed, now = now, zone = clock.zone,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(reveal = Reveal.Failed(e.message.orEmpty())) }
            }
        }
    }

    fun finish() {
        viewModelScope.launch {
            local.update { it.copy(busy = true, error = null) }
            try {
                profiles.setOnboardingComplete(true)
                local.update { it.copy(busy = false) }
                finishedEvents.send(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(busy = false, error = StepError(ErrorKind.FINISH, e.message.orEmpty())) }
            }
        }
    }

    fun dismissError() = local.update { it.copy(error = null) }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            local.update { it.copy(error = StepError(ErrorKind.GENERIC, e.message.orEmpty())) }
        }
    }

    private fun restore(): Local {
        val mask = saved.get<Int>(KEY_WORKDAYS)
        val defaults = ProfileForm()
        val form = ProfileForm(
            workdays = if (mask == null) defaults.workdays else DayOfWeek.entries.filter { mask and (1 shl it.ordinal) != 0 }.toSet(),
            wakeMinutes = saved[KEY_WAKE] ?: defaults.wakeMinutes,
            freeBedMinutes = saved[KEY_FREE_BED] ?: defaults.freeBedMinutes,
            freeWakeMinutes = saved[KEY_FREE_WAKE] ?: defaults.freeWakeMinutes,
            ageBand = saved.get<Int>(KEY_AGE)?.let { AgeBand.entries.getOrNull(it) } ?: defaults.ageBand,
            chronotype = saved.get<Int>(KEY_CHRONO)?.let { ChronotypeAnswer.entries.getOrNull(it) },
        )
        return Local(
            step = saved.get<Int>(KEY_STEP)?.let { OnboardingStep.entries.getOrNull(it) } ?: OnboardingStep.WELCOME,
            permissionPage = saved.get<Int>(KEY_PERMISSION)?.let { PermissionPage.entries.getOrNull(it) } ?: PermissionPage.HEALTH_CONNECT,
            form = form,
            notificationsGranted = null,
        )
    }

    companion object {
        private const val KEY_STEP = "step"
        private const val KEY_PERMISSION = "permission"
        private const val KEY_WORKDAYS = "workdays"
        private const val KEY_WAKE = "wake"
        private const val KEY_FREE_BED = "freeBed"
        private const val KEY_FREE_WAKE = "freeWake"
        private const val KEY_AGE = "age"
        private const val KEY_CHRONO = "chrono"
        private const val MINUTES_PER_DAY = 24 * 60


        internal fun buildProfile(form: ProfileForm): UserProfile = UserProfile(
            ageBand = form.ageBand,
            chronotype = form.chronotype,
            schedule = Schedule(
                workdays = form.workdays,
                wakeTime = if (form.workdays.isEmpty()) null else LocalTime.of(form.wakeMinutes / 60, form.wakeMinutes % 60),
                freeDayBedtime = LocalTime.of(form.freeBedMinutes / 60, form.freeBedMinutes % 60),
                freeDayWakeTime = LocalTime.of(form.freeWakeMinutes / 60, form.freeWakeMinutes % 60),
            ),
        )
    }
}
