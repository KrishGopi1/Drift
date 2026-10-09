package app.drift.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Raw per-source episode. [payload] is the exact [app.drift.data.codec.EpisodeDto] JSON (stages, in-bed, quality). */
@Entity(tableName = "source_episodes", indices = [Index("startMs"), Index("endMs"), Index("sourceId")])
data class SourceEpisodeEntity(
    @PrimaryKey val key: String,
    val sourceId: String,
    val recordId: String,
    val startMs: Long,
    val endMs: Long,
    val ingestedAtMs: Long,
    val payload: String,
)

/** One signal sample or bucket, millisecond precision. [family] discriminates the [app.drift.model.Signal] subtype. */
@Entity(
    tableName = "signals",
    indices = [
        Index(value = ["sourceId", "recordId", "family", "startMs"], unique = true),
        Index("startMs"),
        Index(value = ["sourceId", "recordId"]),
    ],
)
data class SignalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val recordId: String,
    val family: String,
    val startMs: Long,
    val endMs: Long,
    val v1: Double,
    val v2: Double?,
    val code: String?,
    val tier: String,
    val dataOrigin: String?,
    val device: String?,
)

/** A user edit, manual entry, confirmation or deletion tombstone ([deleted]). */
@Entity(tableName = "user_episodes", indices = [Index("startMs"), Index("endMs")])
data class UserEpisodeEntity(
    @PrimaryKey val id: String,
    val startMs: Long,
    val endMs: Long,
    val deleted: Boolean,
    /** Newline-separated raw-episode keys this edit replaces. */
    val suppressedKeys: String,
    val payload: String,
)

@Entity(tableName = "day_annotations")
data class AnnotationEntity(
    @PrimaryKey val epochDay: Long,
    val freeDay: Boolean?,
    val alarmSet: Boolean?,
    val alarmDismissedAt: String?,
)

@Entity(tableName = "check_ins", indices = [Index(value = ["type", "atMs"], unique = true), Index("atMs")])
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val atMs: Long,
    val payload: String,
)

/** Sync state and enablement per source. */
@Entity(tableName = "source_state")
data class SourceStateEntity(
    @PrimaryKey val sourceId: String,
    val enabled: Boolean,
    val checkpoint: String?,
    val lastSyncMs: Long?,
    val lastError: String?,
)

@Entity(tableName = "snapshots", indices = [Index("computedAtMs")])
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val computedAtMs: Long,
    val engineVersion: String,
    val inputHash: String,
    val computedAt: String,
    val payload: String,
)

@Entity(tableName = "engine_state")
data class EngineStateEntity(@PrimaryKey val id: Int = 1, val payload: String)

@Entity(tableName = "profile")
data class ProfileEntity(@PrimaryKey val id: Int = 1, val payload: String)
