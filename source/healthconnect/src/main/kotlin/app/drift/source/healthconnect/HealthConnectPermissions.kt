package app.drift.source.healthconnect

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController

/**
 * Everything `:app` needs to turn a `SourceFix` into an action: the permission set, the request
 * contract, and intents to install, update or open Health Connect.
 */
object HealthConnectPermissions {
    const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

    const val READ_HISTORY = "android.permission.health.READ_HEALTH_DATA_HISTORY"
    const val READ_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

    /** Data permissions, one per record kind. Sleep sessions are mandatory; the rest improve quality. */
    val dataPermissions: Set<String> = RecordKind.entries.map { it.readPermission }.toSet()

    /** Without this the source is unusable. */
    val requiredPermission: String = RecordKind.SLEEP.readPermission

    /** Everything we ask for in one dialog. */
    val all: Set<String> = dataPermissions + READ_HISTORY + READ_IN_BACKGROUND

    /** Contract for `registerForActivityResult`; the result is the set of granted permission strings. */
    fun requestContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /** Opens the Play Store listing of Health Connect (needed on Android 9 to 13). */
    fun installIntent(context: Context): Intent = storeIntent(context)

    /** Same listing; the store shows "Update" when an older provider is installed. */
    fun updateIntent(context: Context): Intent = storeIntent(context)

    /** Opens Health Connect's own settings, where the user can manage this app's access. */
    fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    private fun storeIntent(context: Context): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = Uri.parse("market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding")
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
        }
}
