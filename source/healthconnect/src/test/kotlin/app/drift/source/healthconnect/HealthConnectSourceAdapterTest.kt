package app.drift.source.healthconnect

import androidx.health.connect.client.records.RestingHeartRateRecord
import app.drift.model.Checkpoint
import app.drift.model.source.SourceStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant

class HealthConnectSourceAdapterTest {
    private val now = Instant.parse("2026-10-09T08:00:00Z")
    private val clock = FixedClock(now)
    private val gateway = FakeGateway()
    private val adapter = HealthConnectSourceAdapter(gateway, HealthConnectMapper(), clock)
    private val s = Instant.parse("2026-10-08T21:00:00Z")
    private val e = Instant.parse("2026-10-09T05:00:00Z")

    @Test fun statusMapping() = runTest {
        gateway.provider = ProviderStatus.NOT_INSTALLED
        assertThat(adapter.status()).isEqualTo(SourceStatus.NotInstalled)
        gateway.provider = ProviderStatus.UPDATE_REQUIRED
        assertThat(adapter.status()).isEqualTo(SourceStatus.NotSupported)
        gateway.provider = ProviderStatus.AVAILABLE
        gateway.granted = emptySet()
        val missing = adapter.status() as SourceStatus.NeedsPermission
        assertThat(missing.missing).isEqualTo(HealthConnectPermissions.all)
        gateway.granted = setOf(HealthConnectPermissions.requiredPermission)
        assertThat(adapter.status()).isEqualTo(SourceStatus.Available)
    }

    @Test fun firstPullBackfillsNinetyDaysWithHistoryPermission() = runTest {
        gateway.records += sleepRecord("s1", s, e)
        val r = adapter.pull(null)
        assertThat(r.episodes).hasSize(1)
        assertThat(r.fullResync).isFalse()
        assertThat(r.next!!.token).startsWith("v1|")
        val (_, from, to) = gateway.reads.single()
        assertThat(Duration.between(from, to)).isEqualTo(Duration.ofDays(90))
        assertThat(to).isEqualTo(now)
    }

    @Test fun backfillIsThirtyDaysWithoutHistoryPermission() = runTest {
        gateway.granted = HealthConnectPermissions.all - HealthConnectPermissions.READ_HISTORY
        adapter.pull(null)
        assertThat(Duration.between(gateway.reads.single().second, gateway.reads.single().third)).isEqualTo(Duration.ofDays(30))
    }

    @Test fun historyDeniedByPlatformFallsBackToThirtyDays() = runTest {
        gateway.throwOnReadBeforeDays = 31
        val r = adapter.pull(null)
        assertThat(r.next).isNotNull()
        assertThat(Duration.between(gateway.reads.single().second, gateway.reads.single().third)).isEqualTo(Duration.ofDays(30))
    }

    @Test fun incrementalPullUsesStoredTokenAndReportsDeletions() = runTest {
        val first = adapter.pull(null)
        val rhr = RestingHeartRateRecord(s, null, 50, meta("r1", "com.fitbit.FitbitMobile"))
        gateway.changes += ChangesBatch(listOf(sleepRecord("s2", s, e), rhr), setOf("gone"), "next-tok", tokenExpired = false)
        val second = adapter.pull(first.next)
        assertThat(second.episodes.map { it.origin.recordId }).containsExactly("s2")
        assertThat(second.signals).hasSize(1)
        assertThat(second.deletedRecordIds).containsExactly("gone")
        assertThat(second.next!!.token).endsWith("|next-tok")
        assertThat(gateway.reads).hasSize(1) // no second full read
    }

    @Test fun expiredTokenTriggersBoundedResync() = runTest {
        val first = adapter.pull(null)
        gateway.records += sleepRecord("s9", s, e)
        gateway.changes += ChangesBatch(emptyList(), emptySet(), first.next!!.token, tokenExpired = true)
        val r = adapter.pull(first.next)
        assertThat(r.fullResync).isTrue()
        assertThat(r.episodes.map { it.origin.recordId }).containsExactly("s9")
        assertThat(Duration.between(gateway.reads.last().second, gateway.reads.last().third)).isEqualTo(Duration.ofDays(30))
        assertThat(r.next!!.token).isNotEqualTo(first.next!!.token)
    }

    @Test fun newlyGrantedPermissionTriggersFreshBackfill() = runTest {
        gateway.granted = setOf(HealthConnectPermissions.requiredPermission)
        val first = adapter.pull(null)
        gateway.granted = HealthConnectPermissions.all
        adapter.pull(first.next)
        assertThat(gateway.reads).hasSize(2)
    }

    @Test fun corruptCheckpointFallsBackToBackfill() = runTest {
        adapter.pull(Checkpoint("garbage"))
        assertThat(gateway.reads).hasSize(1)
    }

    @Test fun expectedFailuresYieldEmptyResultKeepingCheckpoint() = runTest {
        val first = adapter.pull(null)
        gateway.provider = ProviderStatus.NOT_INSTALLED
        assertThat(adapter.pull(first.next)).isEqualTo(app.drift.model.source.PullResult(emptyList(), emptyList(), first.next))
        gateway.provider = ProviderStatus.AVAILABLE
        gateway.granted = emptySet()
        assertThat(adapter.pull(first.next).next).isEqualTo(first.next)
        gateway.granted = HealthConnectPermissions.all
        gateway.throwOnRead = SecurityException("revoked")
        assertThat(adapter.pull(null).episodes).isEmpty()
        gateway.throwOnRead = IOException("binder died")
        assertThat(adapter.pull(null).episodes).isEmpty()
    }

    @Test fun permissionSetContainsRequiredScopes() {
        assertThat(HealthConnectPermissions.all).containsAtLeast(
            "android.permission.health.READ_SLEEP", "android.permission.health.READ_HEALTH_DATA_HISTORY",
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND", "android.permission.health.READ_HEART_RATE",
            "android.permission.health.READ_HEART_RATE_VARIABILITY", "android.permission.health.READ_RESTING_HEART_RATE",
            "android.permission.health.READ_STEPS", "android.permission.health.READ_EXERCISE",
        )
    }
}
