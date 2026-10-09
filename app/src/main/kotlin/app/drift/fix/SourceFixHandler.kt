package app.drift.fix

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.drift.model.repository.SourceFix
import app.drift.source.healthconnect.HealthConnectPermissions

/**
 * Maps every [SourceFix] to the Android action that resolves it. Features only emit the fix value;
 * this is the single place that knows about permission contracts and settings intents.
 * When a source lacks several things (e.g. usage access and activity recognition), the repository
 * reports one fix at a time and the next one appears once the first is resolved.
 */
@Composable
fun rememberSourceFixHandler(onResolved: () -> Unit, onImportBackup: () -> Unit): (SourceFix) -> Unit {
    val context = LocalContext.current
    val healthConnect = rememberLauncherForActivityResult(HealthConnectPermissions.requestContract()) { onResolved() }
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResolved() }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onResolved() }
    return remember(context) {
        { fix ->
            when (fix) {
                SourceFix.InstallHealthConnect -> settings.launchSafely(context, HealthConnectPermissions.installIntent(context))
                SourceFix.UpdateHealthConnect -> settings.launchSafely(context, HealthConnectPermissions.updateIntent(context))
                is SourceFix.GrantHealthConnectPermissions ->
                    healthConnect.launch(fix.permissions.ifEmpty { HealthConnectPermissions.all })
                SourceFix.GrantUsageAccess -> settings.launchSafely(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                is SourceFix.GrantRuntimePermissions -> runtime.launch(fix.permissions.toTypedArray())
                SourceFix.ImportSleepAsAndroidBackup -> onImportBackup()
                SourceFix.OpenAppSettings -> settings.launchSafely(context, appDetails(context))
            }
        }
    }
}

private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

private fun androidx.activity.result.ActivityResultLauncher<Intent>.launchSafely(context: Context, intent: Intent) {
    try {
        launch(intent)
    } catch (_: ActivityNotFoundException) {
        launch(appDetails(context))
    }
}
