package app.drift.feature.onboarding

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.drift.charts.EnergyCurveChart
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.CardTone
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftDivider
import app.drift.designsystem.DriftIcons
import app.drift.designsystem.DriftSwitchRow
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.ErrorPanel
import app.drift.designsystem.InsufficientDataPanel
import app.drift.designsystem.PermissionPanel
import app.drift.designsystem.SourceProblemPanel
import app.drift.designsystem.actionLabel
import app.drift.model.Provenance
import app.drift.model.SourceId
import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.engine.SufficiencyTier
import app.drift.model.repository.SourceFix
import app.drift.model.repository.SourceInfo
import app.drift.model.source.SourceStatus

internal const val ACTIVITY_RECOGNITION_PERMISSION = "android.permission.ACTIVITY_RECOGNITION"
internal const val USAGE_ACCESS_PERMISSION = "android.permission.PACKAGE_USAGE_STATS"
internal const val NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

@Composable
internal fun errorText(error: StepError): String = when (error.kind) {
    ErrorKind.SAVE -> stringResource(R.string.ob_save_failed, error.detail.ifBlank { stringResource(R.string.ob_unknown_error) })
    ErrorKind.FINISH -> stringResource(R.string.ob_finish_failed, error.detail.ifBlank { stringResource(R.string.ob_unknown_error) })
    ErrorKind.GENERIC -> error.detail.ifBlank { stringResource(R.string.ob_unknown_error) }
}

@Composable
internal fun StepBody(model: OnboardingModel, actions: OnboardingActions, titleModifier: Modifier) {
    val use24h = model.use24h ?: DateFormat.is24HourFormat(LocalContext.current)
    Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.lg)) {
        model.error?.let { ErrorPanel(errorText(it)) }
        when (model.step) {
            OnboardingStep.WELCOME -> WelcomeStep(titleModifier)
            OnboardingStep.HOW_IT_WORKS -> HowItWorksStep(titleModifier)
            OnboardingStep.SCHEDULE -> ScheduleStep(model, actions, use24h, titleModifier)
            OnboardingStep.ABOUT_YOU -> AboutYouStep(model, actions, titleModifier)
            OnboardingStep.SOURCES -> SourcesStep(model, actions, titleModifier)
            OnboardingStep.PERMISSIONS -> PermissionsStep(model, actions, titleModifier)
            OnboardingStep.IMPORT -> ImportStep(model, actions, titleModifier)
            OnboardingStep.REVEAL -> RevealStep(model, actions, use24h, titleModifier)
        }
    }
}

@Composable
private fun WelcomeStep(titleModifier: Modifier) {
    StepIntro(DriftIcons.Moon, stringResource(R.string.ob_welcome_title), stringResource(R.string.ob_welcome_body), titleModifier = titleModifier)
    DriftCard {
        BulletRow(DriftIcons.Energy, stringResource(R.string.ob_welcome_energy_title), stringResource(R.string.ob_welcome_energy_body))
        DriftDivider()
        BulletRow(DriftIcons.Moon, stringResource(R.string.ob_welcome_debt_title), stringResource(R.string.ob_welcome_debt_body))
        DriftDivider()
        BulletRow(DriftIcons.BodyClock, stringResource(R.string.ob_welcome_clock_title), stringResource(R.string.ob_welcome_clock_body))
    }
    Text(stringResource(R.string.ob_welcome_private), style = DriftTheme.typography.label, color = DriftTheme.colors.onSurfaceMuted)
}

@Composable
private fun HowItWorksStep(titleModifier: Modifier) {
    StepIntro(DriftIcons.Info, stringResource(R.string.ob_how_title), stringResource(R.string.ob_how_body), titleModifier = titleModifier)
    ExplainCard(R.string.ob_how_energy_title, R.string.ob_how_energy_body)
    ExplainCard(R.string.ob_how_debt_title, R.string.ob_how_debt_body)
    ExplainCard(R.string.ob_how_clock_title, R.string.ob_how_clock_body)
}

@Composable
private fun ExplainCard(title: Int, body: Int) {
    DriftCard {
        Text(stringResource(title), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
        Text(stringResource(body), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
    }
}

@Composable
private fun ScheduleStep(model: OnboardingModel, actions: OnboardingActions, use24h: Boolean, titleModifier: Modifier) {
    val form = model.form
    StepIntro(DriftIcons.BodyClock, stringResource(R.string.ob_schedule_title), stringResource(R.string.ob_schedule_body), titleModifier = titleModifier)
    DriftCard {
        Text(stringResource(R.string.ob_workdays_label), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
        WorkdayChips(form.workdays, actions.onToggleWorkday)
        if (form.workdays.isEmpty()) {
            Text(stringResource(R.string.ob_workdays_none), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
        } else {
            TimeStepper(stringResource(R.string.ob_wake_workday), form.wakeMinutes, use24h, actions.onWake)
        }
    }
    DriftCard {
        TimeStepper(stringResource(R.string.ob_bed_free), form.freeBedMinutes, use24h, actions.onFreeBed)
        DriftDivider()
        TimeStepper(stringResource(R.string.ob_wake_free), form.freeWakeMinutes, use24h, actions.onFreeWake)
    }
}

@Composable
internal fun ageBandLabel(band: AgeBand): String = stringResource(
    when (band) {
        AgeBand.AGE_18_25 -> R.string.ob_age_18_25
        AgeBand.AGE_26_35 -> R.string.ob_age_26_35
        AgeBand.AGE_36_50 -> R.string.ob_age_36_50
        AgeBand.AGE_51_64 -> R.string.ob_age_51_64
        AgeBand.AGE_65_PLUS -> R.string.ob_age_65_plus
    },
)

@Composable
internal fun chronotypeLabel(answer: ChronotypeAnswer?): String = stringResource(
    when (answer) {
        ChronotypeAnswer.DEFINITELY_MORNING -> R.string.ob_chrono_definitely_morning
        ChronotypeAnswer.MODERATELY_MORNING -> R.string.ob_chrono_moderately_morning
        ChronotypeAnswer.NEITHER -> R.string.ob_chrono_neither
        ChronotypeAnswer.MODERATELY_EVENING -> R.string.ob_chrono_moderately_evening
        ChronotypeAnswer.DEFINITELY_EVENING -> R.string.ob_chrono_definitely_evening
        null -> R.string.ob_chrono_unsure
    },
)

@Composable
private fun AboutYouStep(model: OnboardingModel, actions: OnboardingActions, titleModifier: Modifier) {
    StepIntro(DriftIcons.Shield, stringResource(R.string.ob_about_title), stringResource(R.string.ob_about_body), titleModifier = titleModifier)
    DriftCard {
        ChoiceGroup(
            stringResource(R.string.ob_age_label),
            AgeBand.entries.map { it to ageBandLabel(it) }, model.form.ageBand, actions.onAge,
        )
    }
    DriftCard {
        ChoiceGroup(
            stringResource(R.string.ob_chrono_label),
            (ChronotypeAnswer.entries + null).map { it to chronotypeLabel(it) }, model.form.chronotype, actions.onChronotype,
        )
    }
}

@Composable
internal fun sourceStatusText(info: SourceInfo): String = when (val s = info.status) {
    SourceStatus.Available -> stringResource(R.string.ob_source_status_available)
    is SourceStatus.NeedsPermission -> stringResource(R.string.ob_source_status_needs_permission)
    SourceStatus.NotInstalled -> stringResource(R.string.ob_source_status_not_installed)
    SourceStatus.NotSupported -> stringResource(R.string.ob_source_status_not_supported)
    is SourceStatus.Error -> stringResource(R.string.ob_source_status_error, s.reason)
}

@Composable
private fun sourceHint(id: SourceId): String = stringResource(
    when (id) {
        SourceId.HEALTH_CONNECT -> R.string.ob_source_hc_hint
        SourceId.SLEEP_AS_ANDROID -> R.string.ob_source_saa_hint
        SourceId.PHONE -> R.string.ob_source_phone_hint
        else -> R.string.ob_source_hint_generic
    },
)

@Composable
private fun SourcesStep(model: OnboardingModel, actions: OnboardingActions, titleModifier: Modifier) {
    StepIntro(DriftIcons.Shield, stringResource(R.string.ob_sources_title), stringResource(R.string.ob_sources_body), titleModifier = titleModifier)
    val known = listOf(SourceId.HEALTH_CONNECT, SourceId.SLEEP_AS_ANDROID, SourceId.PHONE)
    val ordered = known.mapNotNull { id -> model.sources.firstOrNull { it.id == id } } +
        model.sources.filter { it.id !in known && it.id != SourceId.USER }
    DriftCard {
        ordered.forEachIndexed { index, info ->
            if (index > 0) DriftDivider()
            Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs)) {
                DriftSwitchRow(info.displayName, info.enabled, { actions.onSourceEnabled(info.id, it) }, subtitle = sourceHint(info.id))
                if (info.enabled) {
                    StatusLine(sourceStatusText(info), info.status == SourceStatus.Available)
                    val fix = info.fix
                    if (fix != null) {
                        DriftButton(
                            fix.actionLabel(),
                            { if (fix == SourceFix.ImportSleepAsAndroidBackup) actions.onPickBackup() else actions.onFix(fix) },
                            style = ButtonStyle.Text,
                        )
                    }
                }
            }
        }
        if (ordered.none { it.id == SourceId.PHONE }) {
            if (ordered.isNotEmpty()) DriftDivider()
            DriftSwitchRow(
                stringResource(R.string.ob_source_phone_name), model.phoneDetection,
                { actions.onSourceEnabled(SourceId.PHONE, it) }, subtitle = sourceHint(SourceId.PHONE),
            )
        }
    }
    if (ordered.isEmpty()) {
        Text(stringResource(R.string.ob_sources_none), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
    }
}

private fun phoneHasPermission(model: OnboardingModel, permission: String): Boolean {
    val phone = model.sources.firstOrNull { it.id == SourceId.PHONE } ?: return false
    return when (val s = phone.status) {
        SourceStatus.Available -> true
        is SourceStatus.NeedsPermission -> permission !in s.missing
        else -> false
    }
}

internal fun permissionGranted(model: OnboardingModel, page: PermissionPage): Boolean = when (page) {
    PermissionPage.HEALTH_CONNECT -> model.sources.firstOrNull { it.id == SourceId.HEALTH_CONNECT }?.status == SourceStatus.Available
    PermissionPage.ACTIVITY_RECOGNITION -> phoneHasPermission(model, ACTIVITY_RECOGNITION_PERMISSION)
    PermissionPage.USAGE_ACCESS -> phoneHasPermission(model, USAGE_ACCESS_PERMISSION)
    PermissionPage.NOTIFICATIONS -> model.notificationsGranted == true
}

@Composable
private fun PermissionsStep(model: OnboardingModel, actions: OnboardingActions, titleModifier: Modifier) {
    when (model.permissionPage) {
        PermissionPage.HEALTH_CONNECT -> HealthConnectPage(model, actions, titleModifier)
        PermissionPage.ACTIVITY_RECOGNITION -> SimplePermissionPage(
            model, PermissionPage.ACTIVITY_RECOGNITION, R.string.ob_perm_activity_title, R.string.ob_perm_activity_body,
            listOf(R.string.ob_perm_activity_point_1, R.string.ob_perm_activity_point_2), R.string.ob_perm_activity_action,
            { actions.onFix(SourceFix.GrantRuntimePermissions(setOf(ACTIVITY_RECOGNITION_PERMISSION))) }, actions, titleModifier,
        )
        PermissionPage.USAGE_ACCESS -> SimplePermissionPage(
            model, PermissionPage.USAGE_ACCESS, R.string.ob_perm_usage_title, R.string.ob_perm_usage_body,
            listOf(R.string.ob_perm_usage_point_1, R.string.ob_perm_usage_point_2), R.string.ob_perm_usage_action,
            { actions.onFix(SourceFix.GrantUsageAccess) }, actions, titleModifier,
        )
        PermissionPage.NOTIFICATIONS -> SimplePermissionPage(
            model, PermissionPage.NOTIFICATIONS, R.string.ob_perm_notif_title, R.string.ob_perm_notif_body,
            listOf(R.string.ob_perm_notif_point_1, R.string.ob_perm_notif_point_2), R.string.ob_perm_notif_action,
            actions.onRequestNotifications, actions, titleModifier,
        )
    }
}

@Composable
private fun GrantedPanel(title: String, body: String) {
    DriftCard {
        StatusLine(title, ok = true)
        Text(body, style = DriftTheme.typography.body, color = DriftTheme.colors.onSurfaceMuted)
    }
}

@Composable
private fun SimplePermissionPage(
    model: OnboardingModel,
    page: PermissionPage,
    title: Int,
    body: Int,
    points: List<Int>,
    action: Int,
    onAction: () -> Unit,
    actions: OnboardingActions,
    titleModifier: Modifier,
) {
    val name = stringResource(title)
    if (permissionGranted(model, page)) {
        StepIntro(DriftIcons.Shield, name, null, titleModifier = titleModifier)
        GrantedPanel(stringResource(R.string.ob_perm_granted_title, name), stringResource(R.string.ob_perm_granted_body))
    } else {
        // The title is shown by the panel itself; the intro would repeat it.
        PermissionPanel(
            title = name, body = stringResource(body), actionLabel = stringResource(action), onAction = onAction,
            points = points.map { stringResource(it) },
            secondaryLabel = stringResource(R.string.ob_not_now), onSecondary = actions.onSkipPermission,
            modifier = titleModifier,
        )
        if (page == PermissionPage.NOTIFICATIONS && model.notificationsGranted == false) {
            StatusLine(stringResource(R.string.ob_perm_denied_note), ok = false)
        }
    }
}

@Composable
private fun HealthConnectPage(model: OnboardingModel, actions: OnboardingActions, titleModifier: Modifier) {
    val hc = model.sources.firstOrNull { it.id == SourceId.HEALTH_CONNECT }
    when (val status = hc?.status) {
        SourceStatus.Available -> {
            StepIntro(DriftIcons.Shield, stringResource(R.string.ob_perm_hc_connected_title), stringResource(R.string.ob_perm_hc_connected_body), titleModifier = titleModifier)
            val requested = (hc?.fix as? SourceFix.GrantHealthConnectPermissions)?.permissions.orEmpty()
            if (requested.isNotEmpty()) PermissionLines(requested, emptySet())
        }
        is SourceStatus.NeedsPermission -> {
            val requested = (hc?.fix as? SourceFix.GrantHealthConnectPermissions)?.permissions.orEmpty()
            val fix = hc?.fix ?: SourceFix.GrantHealthConnectPermissions(status.missing)
            PermissionPanel(
                title = stringResource(R.string.ob_perm_hc_title), body = stringResource(R.string.ob_perm_hc_body),
                actionLabel = fix.actionLabel(), onAction = { actions.onFix(fix) },
                points = listOf(stringResource(R.string.ob_perm_hc_point_1), stringResource(R.string.ob_perm_hc_point_2)),
                secondaryLabel = stringResource(R.string.ob_not_now), onSecondary = actions.onSkipPermission,
                modifier = titleModifier,
            )
            // Partial grants: show what is in and what is still missing. Both lists use only what the source reports.
            PermissionLines(requested - status.missing, status.missing)
        }
        else -> {
            val body = when (status) {
                SourceStatus.NotSupported -> R.string.ob_perm_hc_unsupported_body
                is SourceStatus.Error -> R.string.ob_perm_hc_missing_body
                else -> if (hc?.fix == SourceFix.UpdateHealthConnect) R.string.ob_perm_hc_update_body else R.string.ob_perm_hc_missing_body
            }
            val fix = hc?.fix ?: SourceFix.InstallHealthConnect
            SourceProblemPanel(
                message = stringResource(R.string.ob_perm_hc_missing_title) + ". " + stringResource(body),
                fix = fix, onFix = actions.onFix, modifier = titleModifier,
            )
            DriftButton(stringResource(R.string.ob_perm_use_phone_only), actions.onUsePhoneOnly, style = ButtonStyle.Secondary)
            DriftButton(stringResource(R.string.ob_not_now), actions.onSkipPermission, style = ButtonStyle.Text)
        }
    }
}

@Composable
private fun PermissionLines(granted: Set<String>, missing: Set<String>) {
    if (granted.isEmpty() && missing.isEmpty()) return
    DriftCard(tone = CardTone.Raised) {
        if (granted.isNotEmpty()) {
            Text(stringResource(R.string.ob_perm_hc_granted), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
            granted.sorted().forEach { StatusLine(permissionName(it), ok = true) }
        }
        if (missing.isNotEmpty()) {
            Text(stringResource(R.string.ob_perm_hc_missing), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
            missing.sorted().forEach { StatusLine(permissionName(it), ok = false) }
        }
    }
}

/** "android.permission.health.READ_SLEEP" becomes "Read sleep". */
internal fun permissionName(permission: String): String =
    permission.substringAfterLast('.').lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun ImportStep(model: OnboardingModel, actions: OnboardingActions, titleModifier: Modifier) {
    StepIntro(DriftIcons.Refresh, stringResource(R.string.ob_import_title), stringResource(R.string.ob_import_body), titleModifier = titleModifier)
    when (val h = model.history) {
        HistoryImport.Idle, is HistoryImport.Running -> {
            val progress = (h as? HistoryImport.Running)?.progress
            DriftCard {
                Text(stringResource(R.string.ob_import_reading), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
                ImportProgressBar(progress)
                if (progress != null) {
                    Text(
                        stringResource(R.string.ob_import_progress_percent, (progress.coerceIn(0f, 1f) * 100).toInt()),
                        style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted,
                    )
                }
            }
        }
        is HistoryImport.Done -> {
            if (model.nightsFound == 0) {
                InsufficientDataPanel(stringResource(R.string.ob_import_zero_title), stringResource(R.string.ob_import_zero_body))
            } else {
                DriftCard {
                    Text(
                        stringResource(if (h.errors.isEmpty()) R.string.ob_import_done_title else R.string.ob_import_partial_title),
                        style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface,
                    )
                    Text(
                        pluralStringResource(R.plurals.ob_import_found, model.nightsFound, model.nightsFound),
                        style = DriftTheme.typography.body, color = DriftTheme.colors.onSurfaceMuted,
                    )
                }
            }
            if (h.errors.isNotEmpty()) {
                DriftCard(tone = CardTone.Raised) {
                    h.errors.forEach { (name, reason) -> StatusLine(stringResource(R.string.ob_import_source_error, name, reason), ok = false) }
                }
            }
        }
        is HistoryImport.Failed -> ErrorPanel(
            h.message.ifBlank { stringResource(R.string.ob_unknown_error) },
            onRetry = actions.onRetryImport, title = stringResource(R.string.ob_import_failed_title),
        )
        HistoryImport.Cancelled -> DriftCard {
            Text(stringResource(R.string.ob_import_cancelled_title), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
            Text(stringResource(R.string.ob_import_cancelled_body), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
            DriftButton(stringResource(R.string.ob_import_retry), actions.onRetryImport, style = ButtonStyle.Secondary)
        }
    }
    BackupImportCard(model.backup, actions)
}

@Composable
private fun BackupImportCard(backup: BackupImport, actions: OnboardingActions) {
    DriftCard {
        Text(stringResource(R.string.ob_backup_title), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
        Text(stringResource(R.string.ob_backup_body), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
        when (backup) {
            BackupImport.Idle -> DriftButton(stringResource(R.string.ob_backup_action), actions.onPickBackup, style = ButtonStyle.Secondary)
            BackupImport.Running -> Text(stringResource(R.string.ob_backup_running), style = DriftTheme.typography.label, color = DriftTheme.colors.onSurface)
            is BackupImport.Done -> {
                Text(stringResource(R.string.ob_backup_done_title), style = DriftTheme.typography.label, color = DriftTheme.colors.success)
                ImportReportLines(backup.report)
            }
            is BackupImport.Failed -> {
                ErrorPanel(
                    backup.message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.ob_backup_unreadable),
                    title = stringResource(R.string.ob_backup_failed_title),
                )
                DriftButton(stringResource(R.string.ob_backup_try_again), actions.onPickBackup, style = ButtonStyle.Secondary)
            }
        }
    }
}

@Composable
private fun RevealStep(model: OnboardingModel, actions: OnboardingActions, use24h: Boolean, titleModifier: Modifier) {
    StepIntro(DriftIcons.Energy, stringResource(R.string.ob_reveal_title), stringResource(R.string.ob_reveal_body), titleModifier = titleModifier)
    when (val r = model.reveal) {
        Reveal.Idle, Reveal.Loading -> DriftCard {
            Text(stringResource(R.string.ob_reveal_loading), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
            ImportProgressBar(null)
        }
        is Reveal.Failed -> ErrorPanel(
            r.message.ifBlank { stringResource(R.string.ob_unknown_error) },
            onRetry = actions.onRetryReveal, title = stringResource(R.string.ob_reveal_failed_title),
        )
        is Reveal.Ready -> {
            val day = r.day
            val priorsOnly = r.tier == SufficiencyTier.PRIORS_ONLY
            DriftCard {
                if (day != null) {
                    EnergyCurveChart(dayCurve = day, now = r.now, zone = r.zone, use24h = use24h)
                } else {
                    Text(stringResource(R.string.ob_reveal_no_day), style = DriftTheme.typography.body, color = DriftTheme.colors.onSurfaceMuted)
                }
                Text(
                    stringResource(if (priorsOnly) R.string.ob_reveal_typical else R.string.ob_reveal_personal),
                    style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted,
                )
                if (r.nightsNeeded > 0) {
                    Text(
                        pluralStringResource(R.plurals.ob_reveal_nights_needed, r.nightsNeeded, r.nightsNeeded),
                        style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted,
                    )
                }
            }
        }
    }
}
