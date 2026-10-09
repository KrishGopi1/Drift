package app.drift.source.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlin.reflect.KClass

/** Production gateway over [HealthConnectClient]. Kept thin: all logic lives in the adapter and mapper. */
class RealHealthConnectGateway @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : HealthConnectGateway {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override fun providerStatus(): ProviderStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> ProviderStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> ProviderStatus.UPDATE_REQUIRED
        else -> ProviderStatus.NOT_INSTALLED
    }

    override suspend fun grantedPermissions(): Set<String> = client.permissionController.getGrantedPermissions()

    override suspend fun readRecords(kinds: Set<RecordKind>, from: Instant, to: Instant): List<Record> {
        val out = ArrayList<Record>()
        for (kind in kinds) {
            @Suppress("UNCHECKED_CAST")
            readAll(kind.recordClass as KClass<Record>, from, to, out)
        }
        return out
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, from: Instant, to: Instant, out: MutableList<Record>) {
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(type, TimeRangeFilter.between(from, to), pageSize = PAGE_SIZE, pageToken = pageToken),
            )
            out.addAll(response.records)
            pageToken = response.pageToken
        } while (pageToken != null)
    }

    override suspend fun newChangesToken(kinds: Set<RecordKind>): String =
        client.getChangesToken(ChangesTokenRequest(kinds.map { it.recordClass }.toSet()))

    override suspend fun changesSince(token: String): ChangesBatch {
        val upserts = ArrayList<Record>()
        val deleted = LinkedHashSet<String>()
        var next = token
        while (true) {
            val response = try {
                client.getChanges(next)
            } catch (e: IOException) {
                throw e
            }
            if (response.changesTokenExpired) return ChangesBatch(emptyList(), emptySet(), next, tokenExpired = true)
            for (change in response.changes) {
                when (change) {
                    is UpsertionChange -> upserts += change.record
                    is DeletionChange -> deleted += change.recordId
                }
            }
            next = response.nextChangesToken
            if (!response.hasMore) break
        }
        return ChangesBatch(upserts, deleted, next, tokenExpired = false)
    }

    private companion object { const val PAGE_SIZE = 5000 }
}
