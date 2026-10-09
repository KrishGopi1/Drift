package app.drift.source.sleepasandroid

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SleepAsAndroidInstallDetector @Inject constructor(@ApplicationContext private val context: Context) {
    fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(SleepAsAndroidCsvParser.SOURCE_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
