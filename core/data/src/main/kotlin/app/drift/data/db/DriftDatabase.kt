package app.drift.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.drift.data.crypto.DatabaseKeyProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        SourceEpisodeEntity::class, SignalEntity::class, UserEpisodeEntity::class, AnnotationEntity::class,
        CheckInEntity::class, SourceStateEntity::class, SnapshotEntity::class, EngineStateEntity::class,
        ProfileEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DriftDatabase : RoomDatabase() {
    abstract fun sourceEpisodes(): SourceEpisodeDao
    abstract fun signals(): SignalDao
    abstract fun userEpisodes(): UserEpisodeDao
    abstract fun annotations(): AnnotationDao
    abstract fun checkIns(): CheckInDao
    abstract fun sourceState(): SourceStateDao
    abstract fun snapshots(): SnapshotDao
    abstract fun engineState(): EngineStateDao
    abstract fun profile(): ProfileDao

    companion object {
        const val NAME = "drift.db"

        /** Opens the on-disk database encrypted with SQLCipher using the passphrase from [keys]. */
        fun openEncrypted(context: Context, keys: DatabaseKeyProvider): DriftDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(keys.passphrase())
            return Room.databaseBuilder(context, DriftDatabase::class.java, NAME)
                .openHelperFactory(factory)
                .build()
        }

        /** Plain in-memory database for JVM/Robolectric tests; never used in production wiring. */
        fun inMemory(context: Context): DriftDatabase =
            Room.inMemoryDatabaseBuilder(context, DriftDatabase::class.java).allowMainThreadQueries().build()
    }
}
