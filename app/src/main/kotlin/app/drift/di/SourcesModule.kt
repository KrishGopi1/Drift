package app.drift.di

import app.drift.data.repo.BackupImporter
import app.drift.model.source.SourceAdapter
import app.drift.source.healthconnect.HealthConnectSourceAdapter
import app.drift.source.phone.PhoneSourceAdapter
import app.drift.source.sleepasandroid.SleepAsAndroidSourceAdapter
import app.drift.source.sleepasandroid.SleepAsAndroidSyncCallback
import app.drift.sources.AppScope
import app.drift.sources.BroadcastSync
import app.drift.sources.SleepAsAndroidBackupImporter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * The only place that knows which concrete sources exist (plan section 2).
 * Adding the boAt ring = one more @IntoSet binding here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SourcesModule {
    @Binds @IntoSet abstract fun healthConnect(a: HealthConnectSourceAdapter): SourceAdapter
    @Binds @IntoSet abstract fun sleepAsAndroid(a: SleepAsAndroidSourceAdapter): SourceAdapter
    @Binds @IntoSet abstract fun phone(a: PhoneSourceAdapter): SourceAdapter
    @Binds @IntoSet abstract fun sleepAsAndroidImporter(i: SleepAsAndroidBackupImporter): BackupImporter
    @Binds abstract fun sleepAsAndroidSync(s: BroadcastSync): SleepAsAndroidSyncCallback

    companion object {
        @Provides @Singleton @AppScope
        fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
