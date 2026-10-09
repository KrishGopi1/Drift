package app.drift.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.drift.model.repository.SourceFix
import app.drift.model.ui.InsufficientReason

@Composable
private fun PanelIcon(icon: ImageVector, tint: Color) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun PanelScaffold(
    icon: ImageVector,
    tint: Color,
    title: String,
    body: String,
    modifier: Modifier,
    tone: CardTone = CardTone.Default,
    announce: Boolean = false,
    middle: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    DriftCard(modifier = modifier, tone = tone) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md),
            horizontalAlignment = Alignment.Start,
        ) {
            PanelIcon(icon, tint)
            Text(title, style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface, modifier = Modifier.semantics { heading() })
            Text(body, style = DriftTheme.typography.body, color = DriftTheme.colors.onSurfaceMuted)
            middle()
            actions()
        }
    }
}

/** Asks for a permission with a plain-language reason and what the user gets (and loses) by saying no. */
@Composable
fun PermissionPanel(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    points: List<String> = emptyList(),
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    icon: ImageVector = DriftIcons.Shield,
) {
    PanelScaffold(
        icon = icon, tint = DriftTheme.colors.primary, title = title, body = body, modifier = modifier,
        middle = {
            if (points.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm)) {
                    points.forEach { point ->
                        Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm), verticalAlignment = Alignment.Top) {
                            Icon(DriftIcons.Check, null, tint = DriftTheme.colors.success, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                            Text(point, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurface)
                        }
                    }
                }
            }
        },
        actions = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs)) {
                DriftButton(actionLabel, onAction)
                if (secondaryLabel != null && onSecondary != null) DriftButton(secondaryLabel, onSecondary, style = ButtonStyle.Text)
            }
        },
    )
}

/**
 * Shown instead of a number when there is not enough data. The big counter is the number of nights still needed
 * ([daysNeeded]); pass null to hide it. Never shows an invented value.
 */
@Composable
fun InsufficientDataPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    daysNeeded: Int? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val counterDesc = daysNeeded?.let { stringResource(R.string.ds_days_needed_desc, it) }
    PanelScaffold(
        icon = DriftIcons.Moon, tint = DriftTheme.colors.windDown, title = title, body = body, modifier = modifier,
        middle = {
            if (daysNeeded != null && daysNeeded > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = counterDesc.orEmpty() },
                ) {
                    Text(daysNeeded.toString(), style = DriftTheme.typography.display, color = DriftTheme.colors.windDown, modifier = Modifier.clearAndSetSemantics {})
                    Text(
                        pluralStringResource(R.plurals.ds_days_needed_label, daysNeeded),
                        style = DriftTheme.typography.label, color = DriftTheme.colors.onSurfaceMuted,
                        modifier = Modifier.padding(bottom = 8.dp).clearAndSetSemantics {},
                    )
                }
            }
        },
        actions = { if (actionLabel != null && onAction != null) DriftButton(actionLabel, onAction, style = ButtonStyle.Secondary) },
    )
}

/** Default copy for each [InsufficientReason]. */
@Composable
fun InsufficientDataPanel(reason: InsufficientReason, daysNeeded: Int, modifier: Modifier = Modifier) {
    val (title, body) = when (reason) {
        InsufficientReason.NO_NIGHTS_YET -> R.string.ds_insufficient_title_no_nights to R.string.ds_insufficient_body_no_nights
        InsufficientReason.TOO_FEW_NIGHTS -> R.string.ds_insufficient_title_few to R.string.ds_insufficient_body_few
        InsufficientReason.TOO_MANY_MISSING_NIGHTS -> R.string.ds_insufficient_title_missing to R.string.ds_insufficient_body_missing
        InsufficientReason.NOT_ENOUGH_FREE_NIGHTS -> R.string.ds_insufficient_title_free to R.string.ds_insufficient_body_free
    }
    InsufficientDataPanel(stringResource(title), stringResource(body), modifier, daysNeeded)
}

/** Error with an optional Try again button. Announced politely to screen readers when it appears. */
@Composable
fun ErrorPanel(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    title: String = stringResource(R.string.ds_error_title),
) {
    PanelScaffold(
        icon = DriftIcons.Warning, tint = DriftTheme.colors.error, title = title, body = message, modifier = modifier, announce = true,
        actions = { if (onRetry != null) DriftButton(stringResource(R.string.ds_retry), onRetry, style = ButtonStyle.Secondary) },
    )
}

@Composable
fun SourceFix.actionLabel(): String = stringResource(
    when (this) {
        SourceFix.InstallHealthConnect -> R.string.ds_fix_install_hc
        SourceFix.UpdateHealthConnect -> R.string.ds_fix_update_hc
        is SourceFix.GrantHealthConnectPermissions -> R.string.ds_fix_grant_hc
        SourceFix.GrantUsageAccess -> R.string.ds_fix_usage_access
        is SourceFix.GrantRuntimePermissions -> R.string.ds_fix_runtime
        SourceFix.ImportSleepAsAndroidBackup -> R.string.ds_fix_import_saa
        SourceFix.OpenAppSettings -> R.string.ds_fix_open_settings
    },
)

/** A data source stopped delivering; one button runs the fix ([onFix] maps it to an intent or permission request). */
@Composable
fun SourceProblemPanel(
    message: String,
    fix: SourceFix,
    onFix: (SourceFix) -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelScaffold(
        icon = DriftIcons.Info, tint = DriftTheme.colors.warning, title = stringResource(R.string.ds_source_problem_title),
        body = message, modifier = modifier, announce = true,
        actions = { DriftButton(fix.actionLabel(), { onFix(fix) }) },
    )
}

/** Shown on a screen that depends on onboarding steps not yet finished. */
@Composable
fun FirstUsePanel(stepsRemaining: Int, onStart: () -> Unit, modifier: Modifier = Modifier) {
    PanelScaffold(
        icon = DriftIcons.Energy, tint = DriftTheme.colors.energy, title = stringResource(R.string.ds_first_use_title),
        body = pluralStringResource(R.plurals.ds_first_use_body, stepsRemaining, stepsRemaining), modifier = modifier,
        actions = { DriftButton(stringResource(R.string.ds_first_use_action), onStart) },
    )
}
