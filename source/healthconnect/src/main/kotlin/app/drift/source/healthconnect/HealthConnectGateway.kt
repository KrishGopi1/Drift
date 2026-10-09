package app.drift.source.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import java.time.Instant
import kotlin.reflect.KClass

enum class RecordKind(val recordClass: KClass<out Record>, val readPermission: String) {
    SLEEP(SleepSessionRecord::class, "android.permission.health.READ_SLEEP"),
    HEART_RATE(HeartRateRecord::class, "android.permission.health.READ_HEART_RATE"),
    RESTING_HEART_RATE(RestingHeartRateRecord::class, "android.permission.health.READ_RESTING_HEART_RATE"),
    HRV(HeartRateVariabilityRmssdRecord::class, "android.permission.health.READ_HEART_RATE_VARIABILITY"),
    STEPS(StepsRecord::class, "android.permission.health.READ_STEPS"),
    EXERCISE(ExerciseSessionRecord::class, "android.permission.health.READ_EXERCISE"),
}

enum class ProviderStatus { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED }

/** One page-set of the Changes API: upserted records and deleted record ids since a token. */
data class ChangesBatch(
    val upserts: List<Record>,
    val deletedRecordIds: Set<String>,
    val nextToken: String,
    val tokenExpired: Boolean,
)

/** The small slice of Health Connect the adapter needs, so tests can supply fake record lists. */
interface HealthConnectGateway {
    fun providerStatus(): ProviderStatus
    suspend fun grantedPermissions(): Set<String>
    suspend fun readRecords(kinds: Set<RecordKind>, from: Instant, to: Instant): List<Record>
    suspend fun newChangesToken(kinds: Set<RecordKind>): String

    /** Follows `hasMore` internally; returns everything since [token]. If the token expired, [ChangesBatch.tokenExpired] is true. */
    suspend fun changesSince(token: String): ChangesBatch
}
