package app.drift.feature.settings

import android.content.Context
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.CardTone
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftDivider
import app.drift.designsystem.DriftListRow
import app.drift.designsystem.DriftSwitchRow
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.DriftTopBar
import app.drift.designsystem.ErrorPanel
import app.drift.designsystem.ScreenStateHost
import app.drift.designsystem.SectionHeader
import app.drift.designsystem.SegmentedControl
import app.drift.designsystem.SourceProblemPanel
import app.drift.designsystem.actionLabel
import app.drift.model.SourceId
import app.drift.model.repository.LocalBackup
import app.drift.model.repository.ReminderKind
import app.drift.model.repository.SourceFix
import app.drift.model.repository.SourceInfo
import app.drift.model.repository.ThemeMode
import app.drift.model.source.SourceStatus
import app.drift.model.ui.ScreenState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class SettingsActions(
    val onOpenSchedule: () -> Unit = {},
    val onOpenMethodology: () -> Unit = {},
    val onOpenLicenses: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onSourceEnabled: (SourceId, Boolean) -> Unit = { _, _ -> },
    val onFix: (SourceFix) -> Unit = {},
    val onPickSleepAsAndroid: () -> Unit = {},
    val onReminder: (ReminderKind, Boolean) -> Unit = { _, _ -> },
    val onTheme: (ThemeMode) -> Unit = {},
    val onUse24Hour: (Boolean?) -> Unit = {},
    val onExport: () -> Unit = {},
    val onPickImport: () -> Unit = {},
    val onBackupNow: () -> Unit = {},
    val onRestore: (String) -> Unit = {},
    val onReset: () -> Unit = {},
    val onDismissNotice: () -> Unit = {},
    val onDismissImport: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * Stateful entry point. Owns the file pickers: [onFix] and [onResetComplete] are implemented by `:app`.
 */
@Composable
fun SettingsRoute(
    onOpenSchedule: () -> Unit,
    onOpenMethodology: () -> Unit,
    onOpenLicenses: () -> Unit,
    onFix: (SourceFix) -> Unit,
    onResetComplete: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.resetComplete.collect { onResetComplete() } }

    val exportName = stringResource(R.string.st_export_file_name)
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.export { context.contentResolver.openOutputStream(uri) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importDriftExport { context.contentResolver.openInputStream(uri) }
    }
    val saaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importSleepAsAndroid { context.contentResolver.openInputStream(uri) }
    }
    val lastImport = (state as? ScreenState.Ready)?.model?.importOutcome?.kind

    SettingsScreen(
        state = state,
        appVersion = appVersion(context),
        actions = SettingsActions(
            onOpenSchedule = onOpenSchedule, onOpenMethodology = onOpenMethodology, onOpenLicenses = onOpenLicenses,
            onSyncNow = viewModel::syncNow, onSourceEnabled = viewModel::setSourceEnabled, onFix = { fix ->
                if (fix == SourceFix.ImportSleepAsAndroidBackup) saaPicker.launch(arrayOf("*/*")) else onFix(fix)
            },
            onPickSleepAsAndroid = { saaPicker.launch(arrayOf("*/*")) },
            onReminder = viewModel::setReminder, onTheme = viewModel::setTheme, onUse24Hour = viewModel::setUse24Hour,
            onExport = { exporter.launch(exportName) }, onPickImport = { importer.launch(arrayOf("*/*")) },
            onBackupNow = viewModel::createBackup, onRestore = viewModel::restoreBackup, onReset = viewModel::resetAll,
            onDismissNotice = viewModel::dismissNotice,
            onDismissImport = viewModel::dismissImportOutcome,
            onRetry = viewModel::retryLoad,
        ),
        onTryAnotherFile = {
            viewModel.dismissImportOutcome()
            if (lastImport == ImportKind.SLEEP_AS_ANDROID) saaPicker.launch(arrayOf("*/*")) else importer.launch(arrayOf("*/*"))
        },
    )
}

private fun appVersion(context: Context): String? = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
} catch (_: Exception) {
    null
}

/** Stateless settings screen. All six C5 states go through [ScreenStateHost]. */
@Composable
fun SettingsScreen(
    state: ScreenState<SettingsModel>,
    appVersion: String?,
    actions: SettingsActions,
    onTryAnotherFile: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(DriftTheme.colors.background)) {
        DriftTopBar(stringResource(R.string.st_title), applyStatusBarInsets = true)
        ScreenStateHost(
            state = state, modifier = Modifier.weight(1f).padding(horizontal = DriftTheme.spacing.gutter),
            onRetry = actions.onRetry, onFix = actions.onFix,
        ) { model ->
            SettingsContent(model, appVersion, actions, onTryAnotherFile)
        }
    }
}

@Composable
private fun SettingsContent(model: SettingsModel, appVersion: String?, actions: SettingsActions, onTryAnotherFile: () -> Unit) {
    val use24h = model.settings.use24HourClock ?: DateFormat.is24HourFormat(LocalContext.current)
    var restoreTarget by remember { mutableStateOf<LocalBackup?>(null) }
    var showReset by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = DriftTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sectionGap),
    ) {
        model.notice?.let { NoticeBanner(it, actions.onDismissNotice) }
        SourcesSection(model, actions)
        SectionHeader(stringResource(R.string.st_schedule_title))
        DriftCard {
            DriftListRow(
                stringResource(R.string.st_schedule_edit), subtitle = stringResource(R.string.st_schedule_edit_subtitle),
                onClick = actions.onOpenSchedule,
            )
        }
        SectionHeader(stringResource(R.string.st_reminders_title))
        DriftCard {
            ReminderKind.entries.forEachIndexed { i, kind ->
                if (i > 0) DriftDivider()
                DriftSwitchRow(
                    reminderTitle(kind), model.settings.reminders[kind] ?: false, { actions.onReminder(kind, it) },
                    subtitle = reminderDescription(kind),
                )
            }
        }
        SectionHeader(stringResource(R.string.st_appearance_title))
        DriftCard {
            Text(stringResource(R.string.st_theme_label), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
            val themes = ThemeMode.entries
            SegmentedControl(
                listOf(stringResource(R.string.st_theme_system), stringResource(R.string.st_theme_light), stringResource(R.string.st_theme_dark)),
                themes.indexOf(model.settings.theme), { actions.onTheme(themes[it]) },
                contentDescription = stringResource(R.string.st_theme_label),
            )
            Text(stringResource(R.string.st_clock_label), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
            val clock = listOf<Boolean?>(null, false, true)
            SegmentedControl(
                listOf(stringResource(R.string.st_clock_system), stringResource(R.string.st_clock_12), stringResource(R.string.st_clock_24)),
                clock.indexOf(model.settings.use24HourClock), { actions.onUse24Hour(clock[it]) },
                contentDescription = stringResource(R.string.st_clock_label),
            )
        }
        SectionHeader(stringResource(R.string.st_data_title))
        DataSection(model, use24h, actions, onRestore = { restoreTarget = it }, onReset = { showReset = true })
        SectionHeader(stringResource(R.string.st_about_title))
        AboutSection(appVersion, actions)
    }

    restoreTarget?.let { backup ->
        val whenText = formatInstant(backup.createdAt, model.zone)
        ConfirmDialog(
            title = stringResource(R.string.st_restore_confirm_title),
            body = stringResource(R.string.st_restore_confirm_body, whenText),
            confirmLabel = stringResource(R.string.st_restore),
            onConfirm = { actions.onRestore(backup.id); restoreTarget = null },
            onDismiss = { restoreTarget = null },
        )
    }
    if (showReset) {
        ResetDialog(onConfirm = { showReset = false; actions.onReset() }, onDismiss = { showReset = false })
    }
    model.importOutcome?.let { ImportOutcomeDialog(it, actions.onDismissImport, onTryAnotherFile) }
}

@Composable
private fun reminderTitle(kind: ReminderKind): String = stringResource(
    when (kind) {
        ReminderKind.WIND_DOWN -> R.string.st_reminder_wind_down
        ReminderKind.MELATONIN_WINDOW -> R.string.st_reminder_melatonin_window
        ReminderKind.CAFFEINE_CUTOFF -> R.string.st_reminder_caffeine_cutoff
        ReminderKind.MORNING_LIGHT -> R.string.st_reminder_morning_light
        ReminderKind.CONFIRM_SLEEP -> R.string.st_reminder_confirm_sleep
        ReminderKind.ENERGY_CHECK_IN -> R.string.st_reminder_energy_check_in
    },
)

@Composable
private fun reminderDescription(kind: ReminderKind): String = stringResource(
    when (kind) {
        ReminderKind.WIND_DOWN -> R.string.st_reminder_wind_down_desc
        ReminderKind.MELATONIN_WINDOW -> R.string.st_reminder_melatonin_window_desc
        ReminderKind.CAFFEINE_CUTOFF -> R.string.st_reminder_caffeine_cutoff_desc
        ReminderKind.MORNING_LIGHT -> R.string.st_reminder_morning_light_desc
        ReminderKind.CONFIRM_SLEEP -> R.string.st_reminder_confirm_sleep_desc
        ReminderKind.ENERGY_CHECK_IN -> R.string.st_reminder_energy_check_in_desc
    },
)

private fun formatInstant(at: java.time.Instant, zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(zone).format(at)

@Composable
private fun noticeText(n: Notice): String {
    val detail = n.detail.ifBlank { stringResource(R.string.st_unknown_error) }
    return when (n.kind) {
        NoticeKind.SYNCED -> stringResource(R.string.st_sync_done, n.detail.toIntOrNull() ?: 0)
        NoticeKind.SYNC_FAILED -> stringResource(R.string.st_sync_failed, detail)
        NoticeKind.SYNC_SOURCE_ERRORS -> stringResource(R.string.st_sync_source_errors, detail)
        NoticeKind.EXPORTED -> stringResource(R.string.st_exported)
        NoticeKind.EXPORT_FAILED -> stringResource(R.string.st_export_failed, detail)
        NoticeKind.BACKUP_CREATED -> stringResource(R.string.st_backup_created)
        NoticeKind.BACKUP_FAILED -> stringResource(R.string.st_backup_failed, detail)
        NoticeKind.RESTORED -> stringResource(R.string.st_restored)
        NoticeKind.RESTORE_FAILED -> stringResource(R.string.st_restore_failed, detail)
        NoticeKind.RESET_FAILED -> stringResource(R.string.st_reset_failed, detail)
    }
}

private val failureKinds = setOf(
    NoticeKind.SYNC_FAILED, NoticeKind.SYNC_SOURCE_ERRORS, NoticeKind.EXPORT_FAILED, NoticeKind.BACKUP_FAILED,
    NoticeKind.RESTORE_FAILED, NoticeKind.RESET_FAILED,
)

@Composable
internal fun NoticeBanner(notice: Notice, onDismiss: () -> Unit) {
    DriftCard(tone = CardTone.Raised) {
        StatusLine(noticeText(notice), ok = notice.kind !in failureKinds)
        DriftButton(stringResource(R.string.st_dismiss), onDismiss, style = ButtonStyle.Text)
    }
}

@Composable
private fun SourcesSection(model: SettingsModel, actions: SettingsActions) {
    SectionHeader(stringResource(R.string.st_sources_title))
    val broken = model.sources.firstOrNull { it.status is SourceStatus.NeedsPermission && it.fix != null }
    if (broken != null) {
        SourceProblemPanel(stringResource(R.string.st_sources_problem, broken.displayName), broken.fix!!,
            onFix = { fix -> if (fix == SourceFix.ImportSleepAsAndroidBackup) actions.onPickSleepAsAndroid() else actions.onFix(fix) },
        )
    }
    DriftCard {
        val list = model.sources.filter { it.id != SourceId.USER }
        if (list.isEmpty()) {
            Text(stringResource(R.string.st_sources_none), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
        }
        list.forEach { info ->
            SourceRow(info, model.zone, model.settings.phoneDetectionEnabled, actions)
            DriftDivider()
        }
        if (list.none { it.id == SourceId.PHONE }) {
            DriftSwitchRow(
                stringResource(R.string.st_phone_detection_name), model.settings.phoneDetectionEnabled,
                { actions.onSourceEnabled(SourceId.PHONE, it) }, subtitle = stringResource(R.string.st_phone_detection_subtitle),
            )
            DriftDivider()
        }
        DriftListRow(
            stringResource(if (model.syncing) R.string.st_syncing else R.string.st_sync_now),
            subtitle = stringResource(R.string.st_sync_now_subtitle),
            onClick = if (model.syncing) null else actions.onSyncNow,
        )
        DriftDivider()
        DriftListRow(
            stringResource(R.string.st_saa_import), subtitle = stringResource(R.string.st_saa_import_subtitle),
            onClick = if (model.busy) null else actions.onPickSleepAsAndroid,
        )
    }
}

@Composable
private fun SourceRow(info: SourceInfo, zone: ZoneId, phoneDetection: Boolean, actions: SettingsActions) {
    val checked = if (info.id == SourceId.PHONE) info.enabled && phoneDetection else info.enabled
    val sync = info.lastSync?.let { stringResource(R.string.st_source_last_sync, formatInstant(it, zone)) }
        ?: stringResource(R.string.st_source_never_synced)
    Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs)) {
        DriftSwitchRow(info.displayName, checked, { actions.onSourceEnabled(info.id, it) }, subtitle = sync)
        if (checked) {
            val status = when (val s = info.status) {
                SourceStatus.Available -> stringResource(R.string.st_source_status_available)
                is SourceStatus.NeedsPermission -> stringResource(R.string.st_source_status_needs_permission)
                SourceStatus.NotInstalled -> stringResource(R.string.st_source_status_not_installed)
                SourceStatus.NotSupported -> stringResource(R.string.st_source_status_not_supported)
                is SourceStatus.Error -> stringResource(R.string.st_source_status_error, s.reason)
            }
            StatusLine(status, info.status == SourceStatus.Available)
            info.lastError?.let { StatusLine(stringResource(R.string.st_source_last_error, it), ok = false) }
            val fix = info.fix
            if (fix != null) {
                DriftButton(
                    fix.actionLabel(),
                    { actions.onFix(fix) },
                    style = ButtonStyle.Text,
                )
            }
        } else {
            StatusLine(stringResource(R.string.st_source_off), ok = false)
        }
    }
}

@Composable
private fun DataSection(
    model: SettingsModel,
    use24h: Boolean,
    actions: SettingsActions,
    onRestore: (LocalBackup) -> Unit,
    onReset: () -> Unit,
) {
    val idle = !model.busy
    DriftCard {
        DriftListRow(stringResource(R.string.st_export), subtitle = stringResource(R.string.st_export_subtitle), onClick = if (idle) actions.onExport else null)
        DriftDivider()
        DriftListRow(stringResource(R.string.st_import), subtitle = stringResource(R.string.st_import_subtitle), onClick = if (idle) actions.onPickImport else null)
    }
    DriftCard {
        Text(stringResource(R.string.st_local_backups_title), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
        if (model.backups.isEmpty()) {
            Text(stringResource(R.string.st_backups_none), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
        }
        val context = LocalContext.current
        model.backups.forEach { backup ->
            val whenText = formatInstant(backup.createdAt, model.zone)
            val size = Formatter.formatShortFileSize(context, backup.sizeBytes)
            val restoreDesc = stringResource(R.string.st_restore_desc, whenText)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DriftListRow(whenText, subtitle = size, modifier = Modifier.weight(1f))
                DriftButton(
                    stringResource(R.string.st_restore), { onRestore(backup) }, style = ButtonStyle.Secondary, enabled = idle,
                    modifier = Modifier.semantics { contentDescription = restoreDesc },
                )
            }
        }
        DriftButton(stringResource(R.string.st_backup_now), actions.onBackupNow, style = ButtonStyle.Secondary, enabled = idle)
    }
    DriftCard {
        DestructiveRow(stringResource(R.string.st_reset), stringResource(R.string.st_reset_subtitle), idle, onReset)
    }
}

@Composable
private fun DestructiveRow(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = DriftTheme.spacing.sm)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xxs),
    ) {
        Text(title, style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.error)
        Text(subtitle, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
    }
}

@Composable
private fun AboutSection(appVersion: String?, actions: SettingsActions) {
    DriftCard {
        DriftListRow(stringResource(R.string.st_version), subtitle = appVersion ?: stringResource(R.string.st_version_unknown))
        DriftDivider()
        DriftListRow(stringResource(R.string.st_privacy_title), subtitle = stringResource(R.string.st_privacy_body))
        DriftDivider()
        DriftListRow(stringResource(R.string.st_methodology), subtitle = stringResource(R.string.st_methodology_subtitle), onClick = actions.onOpenMethodology)
        DriftDivider()
        DriftListRow(stringResource(R.string.st_licenses), subtitle = stringResource(R.string.st_licenses_subtitle), onClick = actions.onOpenLicenses)
    }
}

// ---- Dialogs -----------------------------------------------------------------------------------------------------

@Composable
internal fun ConfirmCard(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    extra: @Composable () -> Unit = {},
) {
    DriftCard(tone = CardTone.Raised) {
        Text(
            title, style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(body, style = DriftTheme.typography.body, color = DriftTheme.colors.onSurfaceMuted)
        extra()
        DriftButton(confirmLabel, onConfirm, enabled = confirmEnabled, modifier = Modifier.fillMaxWidth())
        DriftButton(stringResource(R.string.st_cancel), onDismiss, style = ButtonStyle.Text, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ScrollableDialog(onDismiss) { ConfirmCard(title, body, confirmLabel, onConfirm, onDismiss) }
}

/** What the user must type, and a "delete everything" button that stays disabled until it matches. */
@Composable
internal fun ResetConfirmCard(typed: String, onTyped: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val word = stringResource(R.string.st_reset_word)
    ConfirmCard(
        title = stringResource(R.string.st_reset_title),
        body = stringResource(R.string.st_reset_body),
        confirmLabel = stringResource(R.string.st_reset_confirm),
        onConfirm = onConfirm, onDismiss = onDismiss,
        confirmEnabled = typed.trim() == word,
    ) {
        OutlinedTextField(
            value = typed, onValueChange = onTyped, singleLine = true,
            label = { Text(stringResource(R.string.st_reset_type_prompt, word)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ResetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) { ResetConfirmCard(typed, { typed = it }, onConfirm, onDismiss) }
}

@Composable
internal fun ImportOutcomeCard(outcome: ImportOutcome, onClose: () -> Unit, onTryAnotherFile: () -> Unit) {
    DriftCard(tone = CardTone.Raised) {
        when (outcome) {
            is ImportOutcome.Success -> {
                Text(stringResource(R.string.st_import_ok_title), style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface)
                ImportReportLines(outcome.report)
            }
            is ImportOutcome.Failure -> {
                ErrorPanel(
                    outcome.message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.st_import_failed_unreadable),
                    title = stringResource(R.string.st_import_failed_title),
                )
                DriftButton(stringResource(R.string.st_import_try_another), onTryAnotherFile, modifier = Modifier.fillMaxWidth())
            }
        }
        DriftButton(stringResource(R.string.st_close), onClose, style = ButtonStyle.Text, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ImportOutcomeDialog(outcome: ImportOutcome, onClose: () -> Unit, onTryAnotherFile: () -> Unit) {
    ScrollableDialog(onClose) { ImportOutcomeCard(outcome, onClose, onTryAnotherFile) }
}

/** Dialog whose content scrolls, so it stays usable at 200% font on small screens. */
@Composable
private fun ScrollableDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) { content() }
    }
}
