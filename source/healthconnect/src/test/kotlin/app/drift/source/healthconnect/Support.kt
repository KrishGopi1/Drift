package app.drift.source.healthconnect

import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

fun meta(id: String, pkg: String, device: Device? = null): Metadata {
    val base = Metadata.autoRecordedWithId(id, device ?: Device(type = Device.TYPE_UNKNOWN))
    // The provider assigns the data origin at write time, so tests set it reflectively.
    Metadata::class.java.getDeclaredField("dataOrigin").apply { isAccessible = true }.set(base, DataOrigin(pkg))
    return base
}

fun sleepRecord(
    id: String,
    start: Instant,
    end: Instant,
    pkg: String = "com.fitbit.FitbitMobile",
    stages: List<SleepSessionRecord.Stage> = emptyList(),
    offset: ZoneOffset? = ZoneOffset.UTC,
    device: Device? = null,
) = SleepSessionRecord(start, offset, end, offset, metadata = meta(id, pkg, device), stages = stages)

class FixedClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}

class FakeGateway(
    var provider: ProviderStatus = ProviderStatus.AVAILABLE,
    var granted: Set<String> = HealthConnectPermissions.all,
) : HealthConnectGateway {
    val records = ArrayList<Record>()
    var changes = ArrayDeque<ChangesBatch>()
    var tokenCounter = 0
    val reads = ArrayList<Triple<Set<RecordKind>, Instant, Instant>>()
    var throwOnRead: Exception? = null
    var throwOnReadBeforeDays: Long? = null
    var clock: FixedClock? = null

    override fun providerStatus() = provider
    override suspend fun grantedPermissions() = granted
    override suspend fun readRecords(kinds: Set<RecordKind>, from: Instant, to: Instant): List<Record> {
        throwOnRead?.let { throw it }
        throwOnReadBeforeDays?.let { d -> if (from.isBefore(to.minusSeconds(d * 86400 - 1))) throw SecurityException("history") }
        reads += Triple(kinds, from, to)
        return records.toList()
    }
    override suspend fun newChangesToken(kinds: Set<RecordKind>) = "tok-${++tokenCounter}"
    override suspend fun changesSince(token: String): ChangesBatch = changes.removeFirst().let { it.copy(nextToken = it.nextToken) }
}
