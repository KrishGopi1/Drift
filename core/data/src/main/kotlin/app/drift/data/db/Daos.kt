package app.drift.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceEpisodeDao {
    @Query("SELECT * FROM source_episodes WHERE endMs > :fromMs AND startMs < :toMs ORDER BY startMs")
    fun observeOverlapping(fromMs: Long, toMs: Long): Flow<List<SourceEpisodeEntity>>

    @Query("SELECT * FROM source_episodes WHERE endMs > :fromMs AND startMs < :toMs ORDER BY startMs")
    suspend fun overlapping(fromMs: Long, toMs: Long): List<SourceEpisodeEntity>

    @Query("SELECT * FROM source_episodes WHERE `key` = :key")
    suspend fun get(key: String): SourceEpisodeEntity?

    @Query("SELECT * FROM source_episodes ORDER BY startMs")
    suspend fun all(): List<SourceEpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<SourceEpisodeEntity>)

    @Query("DELETE FROM source_episodes WHERE sourceId = :sourceId AND recordId IN (:recordIds)")
    suspend fun deleteRecords(sourceId: String, recordIds: List<String>)

    @Query("DELETE FROM source_episodes WHERE sourceId = :sourceId AND startMs >= :fromMs")
    suspend fun deleteFrom(sourceId: String, fromMs: Long)

    @Query("DELETE FROM source_episodes")
    suspend fun clear()
}

@Dao
interface SignalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<SignalEntity>)

    @Query("SELECT * FROM signals WHERE endMs >= :fromMs AND startMs < :toMs ORDER BY startMs")
    suspend fun window(fromMs: Long, toMs: Long): List<SignalEntity>

    @Query("SELECT * FROM signals ORDER BY id")
    suspend fun all(): List<SignalEntity>

    @Query("DELETE FROM signals WHERE sourceId = :sourceId AND recordId IN (:recordIds)")
    suspend fun deleteRecords(sourceId: String, recordIds: List<String>)

    @Query("DELETE FROM signals WHERE sourceId = :sourceId AND startMs >= :fromMs")
    suspend fun deleteFrom(sourceId: String, fromMs: Long)

    @Query("DELETE FROM signals WHERE endMs < :beforeMs")
    suspend fun deleteBefore(beforeMs: Long)

    @Query("SELECT COUNT(*) FROM signals")
    suspend fun count(): Int

    @Query("DELETE FROM signals")
    suspend fun clear()
}

@Dao
interface UserEpisodeDao {
    @Query("SELECT * FROM user_episodes WHERE endMs > :fromMs AND startMs < :toMs ORDER BY startMs")
    fun observeOverlapping(fromMs: Long, toMs: Long): Flow<List<UserEpisodeEntity>>

    @Query("SELECT * FROM user_episodes ORDER BY startMs")
    suspend fun all(): List<UserEpisodeEntity>

    @Query("SELECT * FROM user_episodes WHERE id = :id")
    suspend fun get(id: String): UserEpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: UserEpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<UserEpisodeEntity>)

    @Query("DELETE FROM user_episodes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM user_episodes")
    suspend fun clear()
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM day_annotations WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    fun observe(from: Long, to: Long): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM day_annotations ORDER BY epochDay")
    suspend fun all(): List<AnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AnnotationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AnnotationEntity>)

    @Query("DELETE FROM day_annotations")
    suspend fun clear()
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins WHERE atMs >= :sinceMs ORDER BY atMs")
    fun observeSince(sinceMs: Long): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins WHERE atMs >= :sinceMs ORDER BY atMs")
    suspend fun since(sinceMs: Long): List<CheckInEntity>

    @Query("SELECT * FROM check_ins ORDER BY atMs")
    suspend fun all(): List<CheckInEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CheckInEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CheckInEntity>)

    @Query("DELETE FROM check_ins")
    suspend fun clear()
}

@Dao
interface SourceStateDao {
    @Query("SELECT * FROM source_state")
    fun observeAll(): Flow<List<SourceStateEntity>>

    @Query("SELECT * FROM source_state")
    suspend fun all(): List<SourceStateEntity>

    @Query("SELECT * FROM source_state WHERE sourceId = :id")
    suspend fun get(id: String): SourceStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SourceStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SourceStateEntity>)

    @Query("DELETE FROM source_state")
    suspend fun clear()
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots ORDER BY computedAtMs DESC, id DESC LIMIT 1")
    fun observeLatest(): Flow<SnapshotEntity?>

    @Query("SELECT * FROM snapshots ORDER BY computedAtMs DESC, id DESC LIMIT 1 OFFSET 1")
    fun observePrevious(): Flow<SnapshotEntity?>

    @Query("SELECT * FROM snapshots ORDER BY computedAtMs DESC, id DESC LIMIT 1")
    suspend fun latest(): SnapshotEntity?

    @Query("SELECT * FROM snapshots ORDER BY computedAtMs, id")
    suspend fun all(): List<SnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SnapshotEntity): Long

    @Query("DELETE FROM snapshots WHERE id NOT IN (SELECT id FROM snapshots ORDER BY computedAtMs DESC, id DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Query("DELETE FROM snapshots")
    suspend fun clear()
}

@Dao
interface EngineStateDao {
    @Query("SELECT * FROM engine_state WHERE id = 1")
    suspend fun get(): EngineStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: EngineStateEntity)

    @Query("DELETE FROM engine_state")
    suspend fun clear()
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun clear()
}
