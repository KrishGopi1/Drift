package app.drift.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drift.model.SourceId
import app.drift.model.repository.AppSettings
import app.drift.model.repository.BackupRepository
import app.drift.model.repository.LocalBackup
import app.drift.model.repository.ProfileRepository
import app.drift.model.repository.ReminderKind
import app.drift.model.repository.SettingsRepository
import app.drift.model.repository.SourceRepository
import app.drift.model.repository.SyncReason
import app.drift.model.repository.ThemeMode
import app.drift.model.ui.ScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sources: SourceRepository,
    private val settings: SettingsRepository,
    private val backups: BackupRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    internal var zone: ZoneId = ZoneId.systemDefault()

    private data class Local(
        val backups: List<LocalBackup> = emptyList(),
        val syncing: Boolean = false,
        val busy: Boolean = false,
        val notice: Notice? = null,
        val importOutcome: ImportOutcome? = null,
    )

    private val local = MutableStateFlow(Local())
    private val retry = MutableStateFlow(0)
    private val resetEvents = Channel<Unit>(Channel.BUFFERED)

    /** Fires once after everything was wiped. `:app` then restarts at onboarding. */
    val resetComplete: Flow<Unit> = resetEvents.receiveAsFlow()

    val state: StateFlow<ScreenState<SettingsModel>> = retry.flatMapLatest {
        combine(local, sources.observeSources(), settings.observeSettings()) { l, srcs, app ->
            ScreenState.Ready(
                SettingsModel(
                    sources = srcs, settings = app, backups = l.backups, syncing = l.syncing, busy = l.busy,
                    notice = l.notice, importOutcome = l.importOutcome, zone = zone,
                ),
            ) as ScreenState<SettingsModel>
        }.catch { emit(ScreenState.Error(it.message.orEmpty(), retryable = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    init { refreshBackups() }

    fun retryLoad() { retry.update { it + 1 }; refreshBackups() }

    private fun refreshBackups() {
        viewModelScope.launch {
            try {
                val list = backups.listLocalBackups()
                local.update { it.copy(backups = list) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(notice = Notice(NoticeKind.BACKUP_FAILED, e.message.orEmpty())) }
            }
        }
    }

    // ---- Sources ----------------------------------------------------------------------------------------------

    fun syncNow() {
        viewModelScope.launch {
            local.update { it.copy(syncing = true, notice = null) }
            try {
                val r = sources.sync(SyncReason.USER_PULL)
                val notice = if (r.errors.isEmpty()) {
                    Notice(NoticeKind.SYNCED, (r.newEpisodes + r.updatedEpisodes).toString())
                } else {
                    Notice(NoticeKind.SYNC_SOURCE_ERRORS, r.errors.entries.joinToString("; ") { (id, msg) -> "${id.value}: $msg" })
                }
                local.update { it.copy(syncing = false, notice = notice) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(syncing = false, notice = Notice(NoticeKind.SYNC_FAILED, e.message.orEmpty())) }
            }
        }
    }

    fun setSourceEnabled(id: SourceId, enabled: Boolean) {
        viewModelScope.launch {
            try {
                sources.setEnabled(id, enabled)
                if (id == SourceId.PHONE) settings.update { it.copy(phoneDetectionEnabled = enabled) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(notice = Notice(NoticeKind.SYNC_FAILED, e.message.orEmpty())) }
            }
        }
    }

    fun importSleepAsAndroid(open: () -> InputStream?) = runImport(ImportKind.SLEEP_AS_ANDROID, open) { sources.importSleepAsAndroidBackup(it) }

    // ---- Preferences ------------------------------------------------------------------------------------------

    fun setReminder(kind: ReminderKind, enabled: Boolean) = updateSettings { it.copy(reminders = it.reminders + (kind to enabled)) }
    fun setTheme(mode: ThemeMode) = updateSettings { it.copy(theme = mode) }
    fun setUse24Hour(value: Boolean?) = updateSettings { it.copy(use24HourClock = value) }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            try {
                settings.update(transform)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(notice = Notice(NoticeKind.BACKUP_FAILED, e.message.orEmpty())) }
            }
        }
    }

    // ---- Data -------------------------------------------------------------------------------------------------

    /** Writes the export into the stream [open] returns; null means the destination could not be opened. */
    fun export(open: () -> OutputStream?) {
        viewModelScope.launch {
            local.update { it.copy(busy = true, notice = null) }
            val notice = try {
                val out = open() ?: throw IllegalStateException("")
                out.use { backups.exportAll(it) }
                Notice(NoticeKind.EXPORTED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Notice(NoticeKind.EXPORT_FAILED, e.message.orEmpty())
            }
            local.update { it.copy(busy = false, notice = notice) }
        }
    }

    fun importDriftExport(open: () -> InputStream?) = runImport(ImportKind.DRIFT_EXPORT, open) { backups.importAll(it) }

    private fun runImport(kind: ImportKind, open: () -> InputStream?, run: suspend (InputStream) -> app.drift.model.repository.ImportReport) {
        viewModelScope.launch {
            local.update { it.copy(busy = true, importOutcome = null) }
            val outcome = try {
                val stream = open()
                if (stream == null) ImportOutcome.Failure(kind, null) else ImportOutcome.Success(kind, stream.use { run(it) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportOutcome.Failure(kind, e.message.orEmpty())
            }
            local.update { it.copy(busy = false, importOutcome = outcome) }
            if (kind == ImportKind.DRIFT_EXPORT) refreshBackups()
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            local.update { it.copy(busy = true, notice = null) }
            try {
                backups.createLocalBackup()
                val list = backups.listLocalBackups()
                local.update { it.copy(busy = false, backups = list, notice = Notice(NoticeKind.BACKUP_CREATED)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(busy = false, notice = Notice(NoticeKind.BACKUP_FAILED, e.message.orEmpty())) }
            }
        }
    }

    fun restoreBackup(id: String) {
        viewModelScope.launch {
            local.update { it.copy(busy = true, notice = null) }
            val notice = try {
                backups.restoreLocalBackup(id)
                Notice(NoticeKind.RESTORED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Notice(NoticeKind.RESTORE_FAILED, e.message.orEmpty())
            }
            local.update { it.copy(busy = false, notice = notice) }
        }
    }

    /** Deletes everything, marks onboarding as not done, then signals [resetComplete]. */
    fun resetAll() {
        viewModelScope.launch {
            local.update { it.copy(busy = true, notice = null) }
            try {
                backups.wipeAll()
                profiles.setOnboardingComplete(false)
                local.update { Local() }
                resetEvents.send(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(busy = false, notice = Notice(NoticeKind.RESET_FAILED, e.message.orEmpty())) }
            }
        }
    }

    fun dismissNotice() = local.update { it.copy(notice = null) }
    fun dismissImportOutcome() = local.update { it.copy(importOutcome = null) }
}
