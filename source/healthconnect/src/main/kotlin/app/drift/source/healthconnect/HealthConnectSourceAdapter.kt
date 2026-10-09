package app.drift.source.healthconnect

import app.drift.model.Checkpoint
import app.drift.model.QualityTier
import app.drift.model.SourceId
import app.drift.model.source.Capability
import app.drift.model.source.PullResult
import app.drift.model.source.SourceAdapter
import app.drift.model.source.SourceStatus
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject

/**
 * Health Connect source (plan 5.1).
 *
 * First pull backfills 90 days (30 without the history permission), then follows the Changes API using the
 * stored checkpoint. An expired token triggers a bounded 30-day re-read reported with `fullResync = true`.
 * The checkpoint also records which record kinds the token covers, so granting a new permission later
 * automatically triggers a fresh backfill (a token never covers kinds that were not granted when it was issued).
 */
class HealthConnectSourceAdapter internal constructor(
    private val gateway: HealthConnectGateway,
    private val mapper: HealthConnectMapper,
    private val clock: Clock,
) : SourceAdapter {

    @Inject constructor(gateway: HealthConnectGateway) :
        this(gateway, HealthConnectMapper(OriginTierTable.DEFAULT), Clock.systemUTC())

    constructor(gateway: HealthConnectGateway, tiers: OriginTierTable, zone: () -> ZoneId = { ZoneId.systemDefault() }) :
        this(gateway, HealthConnectMapper(tiers, zone), Clock.systemUTC())

    override val id: SourceId = SourceId.HEALTH_CONNECT
    override val qualityTier: QualityTier = QualityTier.WEARABLE_WRIST
    override val capabilities: Set<Capability> = setOf(
        Capability.SLEEP_SESSIONS, Capability.SLEEP_STAGES, Capability.HEART_RATE, Capability.RESTING_HEART_RATE,
        Capability.HRV, Capability.STEPS, Capability.EXERCISE,
    )

    override suspend fun status(): SourceStatus {
        when (gateway.providerStatus()) {
            ProviderStatus.NOT_INSTALLED -> return SourceStatus.NotInstalled
            ProviderStatus.UPDATE_REQUIRED -> return SourceStatus.NotSupported
            ProviderStatus.AVAILABLE -> Unit
        }
        val granted = try {
            gateway.grantedPermissions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SourceStatus.Error(e.message ?: "Health Connect could not be reached")
        }
        return if (HealthConnectPermissions.requiredPermission in granted) {
            SourceStatus.Available
        } else {
            SourceStatus.NeedsPermission(HealthConnectPermissions.all - granted)
        }
    }

    override suspend fun pull(since: Checkpoint?): PullResult {
        if (gateway.providerStatus() != ProviderStatus.AVAILABLE) return unchanged(since)
        return try {
            val granted = gateway.grantedPermissions()
            if (HealthConnectPermissions.requiredPermission !in granted) return unchanged(since)
            val kinds = RecordKind.entries.filter { it.readPermission in granted }.toSet()
            val state = since?.let(::decode)
            if (state == null || state.kinds != kinds) {
                backfill(kinds, historyGranted = HealthConnectPermissions.READ_HISTORY in granted)
            } else {
                incremental(kinds, state.token)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            unchanged(since)
        } catch (e: IOException) {
            unchanged(since)
        }
    }

    private suspend fun backfill(kinds: Set<RecordKind>, historyGranted: Boolean): PullResult {
        val now = clock.instant()
        // Token first: anything written while we read is then picked up by the next incremental pull.
        val token = gateway.newChangesToken(kinds)
        val window = if (historyGranted) BACKFILL_WITH_HISTORY else BACKFILL_DEFAULT
        val records = try {
            gateway.readRecords(kinds, now.minus(window), now)
        } catch (e: SecurityException) {
            if (!historyGranted) throw e
            gateway.readRecords(kinds, now.minus(BACKFILL_DEFAULT), now)
        }
        val mapped = mapper.map(records)
        return PullResult(mapped.episodes, mapped.signals, encode(kinds, token))
    }

    private suspend fun incremental(kinds: Set<RecordKind>, token: String): PullResult {
        val batch = gateway.changesSince(token)
        if (batch.tokenExpired) return resync(kinds)
        val mapped = mapper.map(batch.upserts)
        return PullResult(mapped.episodes, mapped.signals, encode(kinds, batch.nextToken), batch.deletedRecordIds)
    }

    private suspend fun resync(kinds: Set<RecordKind>): PullResult {
        val now = clock.instant()
        val token = gateway.newChangesToken(kinds)
        val mapped = mapper.map(gateway.readRecords(kinds, now.minus(RESYNC_WINDOW), now))
        return PullResult(mapped.episodes, mapped.signals, encode(kinds, token), fullResync = true)
    }

    private fun unchanged(since: Checkpoint?) = PullResult(emptyList(), emptyList(), since)

    private data class State(val kinds: Set<RecordKind>, val token: String)

    private fun encode(kinds: Set<RecordKind>, token: String): Checkpoint =
        Checkpoint("v1|${kinds.map { it.name }.sorted().joinToString(",")}|$token")

    private fun decode(checkpoint: Checkpoint): State? {
        val parts = checkpoint.token.split('|', limit = 3)
        if (parts.size != 3 || parts[0] != "v1" || parts[2].isEmpty()) return null
        val kinds = parts[1].split(',').map { name -> RecordKind.entries.firstOrNull { it.name == name } ?: return null }
        return State(kinds.toSet(), parts[2])
    }

    companion object {
        val BACKFILL_WITH_HISTORY: Duration = Duration.ofDays(90)
        val BACKFILL_DEFAULT: Duration = Duration.ofDays(30)
        val RESYNC_WINDOW: Duration = Duration.ofDays(30)

    }
}

