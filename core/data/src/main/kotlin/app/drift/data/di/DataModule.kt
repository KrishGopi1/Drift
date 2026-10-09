package app.drift.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.drift.data.crypto.AesGcmWrapper
import app.drift.data.crypto.AndroidKeystoreKeySource
import app.drift.data.crypto.DatabaseKeyProvider
import app.drift.data.crypto.KeyWrapper
import app.drift.data.crypto.KeystoreDatabaseKeys
import app.drift.data.db.DriftDatabase
import app.drift.data.repo.AnalysisRepositoryImpl
import app.drift.data.repo.BackupImporter
import app.drift.data.repo.BackupRepositoryImpl
import app.drift.data.repo.CheckInRepositoryImpl
import app.drift.data.repo.ProfileRepositoryImpl
import app.drift.data.repo.SettingsRepositoryImpl
import app.drift.data.repo.SleepRepositoryImpl
import app.drift.data.repo.SourceRepositoryImpl
import app.drift.model.repository.AnalysisRepository
import app.drift.model.repository.BackupRepository
import app.drift.model.repository.CheckInRepository
import app.drift.model.repository.ProfileRepository
import app.drift.model.repository.SettingsRepository
import app.drift.model.repository.SleepRepository
import app.drift.model.repository.SourceRepository
import app.drift.model.source.SourceAdapter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Clock
import javax.inject.Named
import javax.inject.Singleton

/**
 * Bindings for :core:data. :app must additionally bind `SleepEngine`, contribute `SourceAdapter`s with
 * `@IntoSet`, and (for CSV import) `BackupImporter`s with `@IntoSet`. Empty sets are valid.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds abstract fun sleep(impl: SleepRepositoryImpl): SleepRepository
    @Binds abstract fun analysis(impl: AnalysisRepositoryImpl): AnalysisRepository
    @Binds abstract fun sources(impl: SourceRepositoryImpl): SourceRepository
    @Binds abstract fun profile(impl: ProfileRepositoryImpl): ProfileRepository
    @Binds abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun checkIns(impl: CheckInRepositoryImpl): CheckInRepository
    @Binds abstract fun backup(impl: BackupRepositoryImpl): BackupRepository

    @Multibinds abstract fun sourceAdapters(): Set<SourceAdapter>
    @Multibinds abstract fun backupImporters(): Set<BackupImporter>
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {
    @Provides @Singleton
    fun keyProvider(@ApplicationContext context: Context): DatabaseKeyProvider = KeystoreDatabaseKeys.create(context)

    @Provides @Singleton
    fun database(@ApplicationContext context: Context, keys: DatabaseKeyProvider): DriftDatabase =
        DriftDatabase.openEncrypted(context, keys)

    @Provides @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            context.preferencesDataStoreFile("drift_prefs")
        }

    @Provides @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides @Named("backupDir")
    fun backupDir(@ApplicationContext context: Context): File = File(context.filesDir, "backups")

    @Provides @Named("backupCipher") @Singleton
    fun backupCipher(): KeyWrapper = AesGcmWrapper(AndroidKeystoreKeySource("drift_backup_key"))
}
