package app.drift

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.drift.background.BackgroundScheduler
import app.drift.background.reschedule.RescheduleReceiver
import app.drift.model.repository.ProfileRepository
import app.drift.source.phone.PhoneTracking
import app.drift.source.sleepasandroid.SleepAsAndroidBroadcastReceiver
import app.drift.sources.AppScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DriftApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduler: BackgroundScheduler
    @Inject lateinit var profiles: ProfileRepository
    @Inject @AppScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Receivers the OS no longer delivers to the manifest (charging, unlock, Sleep as Android broadcasts).
        PhoneTracking.start(this)
        RescheduleReceiver.registerUnlockListener(this)
        SleepAsAndroidBroadcastReceiver.register(this)
        appScope.launch {
            if (profiles.observeOnboardingComplete().first()) scheduler.scheduleAll()
        }
    }
}
