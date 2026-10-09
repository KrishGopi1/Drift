package app.drift.source.phone

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.model.AlarmKind
import app.drift.model.Checkpoint
import app.drift.model.QualityTier
import app.drift.model.Signal
import app.drift.model.SourceId
import app.drift.model.source.Capability
import app.drift.model.source.SourceStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

internal class FakePhoneSystem : PhoneSystem {
    var usage = true
    var recognition = true
    var screen: List<ScreenChange> = emptyList()
    var alarm: Instant? = null
    var plugged: Boolean? = null
    var steps: Pair<Long, Long>? = null
    var registered = 0
    override fun hasUsageAccess() = usage
    override fun hasActivityRecognition() = recognition
    override fun screenChanges(from: Instant, to: Instant) = if (usage) screen.filter { it.at in from..to } else emptyList()
    override fun nextAlarmAt() = alarm
    override fun isPlugged() = plugged
    override suspend fun stepCounter() = steps
    override fun registerSleepApi() { registered++ }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PhoneSourceAdapterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone: ZoneId = ZoneOffset.UTC
    private lateinit var store: PhoneStore
    private lateinit var system: FakePhoneSystem

    @Before fun setUp() {
        store = PhoneStore(context).also { it.clear() }
        system = FakePhoneSystem()
    }

    private fun adapter(now: Instant) = PhoneSourceAdapter(system, store, Clock.fixed(now, ZoneOffset.UTC)) { zone }

    private val day = Instant.parse("2026-03-04T00:00:00Z")
    private fun at(h: Int, m: Int = 0, dayOffset: Long = 0) = day.plus(Duration.ofDays(dayOffset)).plusSeconds(h * 3600L + m * 60L)

    /** A realistic day: phone in use all evening until 23:15, quiet, back at 07:05. */
    private fun busyEvening() {
        val changes = mutableListOf<ScreenChange>()
        var t = at(8, 0, -1)
        while (t < at(23, 5, -1)) {
            changes += ScreenChange(t, true); changes += ScreenChange(t.plusSeconds(240), false)
            t = t.plusSeconds(25 * 60)
        }
        changes += ScreenChange(at(23, 0, -1), true); changes += ScreenChange(at(23, 15, -1), false)
        changes += ScreenChange(at(7, 5), true); changes += ScreenChange(at(7, 9), false)
        system.screen = changes.sortedBy { it.at }.fold(mutableListOf()) { acc, c -> if (acc.lastOrNull()?.interactive != c.interactive) acc += c; acc }
    }

    @Test fun `identity and capabilities`() {
        val a = adapter(at(8))
        assertThat(a.id).isEqualTo(SourceId.PHONE)
        assertThat(a.qualityTier).isEqualTo(QualityTier.PHONE_INFERRED)
        assertThat(a.capabilities).containsAtLeast(Capability.SLEEP_SESSIONS, Capability.SCREEN, Capability.CHARGING, Capability.ALARMS)
    }

    @Test fun `status is available when both permissions are granted and registers the sleep api`() = runBlocking {
        assertThat(adapter(at(8)).status()).isEqualTo(SourceStatus.Available)
        assertThat(system.registered).isEqualTo(1)
    }

    @Test fun `status lists exactly the missing permissions`() = runBlocking {
        system.usage = false
        assertThat(adapter(at(8)).status()).isEqualTo(SourceStatus.NeedsPermission(setOf(PhoneSourceAdapter.PERMISSION_USAGE_ACCESS)))
        system.usage = true; system.recognition = false
        assertThat(adapter(at(8)).status()).isEqualTo(SourceStatus.NeedsPermission(setOf(Manifest.permission.ACTIVITY_RECOGNITION)))
        system.usage = false
        system.registered = 0
        assertThat((adapter(at(8)).status() as SourceStatus.NeedsPermission).missing)
            .containsExactly(PhoneSourceAdapter.PERMISSION_USAGE_ACCESS, Manifest.permission.ACTIVITY_RECOGNITION)
        assertThat(system.registered).isEqualTo(0) // never registers without the permission
    }

    @Test fun `pull reconstructs last night as a phone-inferred main episode`() = runBlocking {
        busyEvening()
        val now = at(7, 30)
        val r = adapter(now).pull(null)
        assertThat(r.episodes).hasSize(1)
        val e = r.episodes.single()
        assertThat(e.origin.qualityTier).isEqualTo(QualityTier.PHONE_INFERRED)
        assertThat(e.origin.sourceId).isEqualTo(SourceId.PHONE)
        assertThat(Duration.between(e.start, at(23, 25, -1)).abs()).isAtMost(Duration.ofMinutes(30))
        assertThat(Duration.between(e.end, at(7, 5)).abs()).isAtMost(Duration.ofMinutes(20))
        assertThat(e.quality.detectionConfidence).isGreaterThan(0.2)
        assertThat(r.next).isNotNull()
    }

    @Test fun `pull is idempotent so the same record id is produced again`() = runBlocking {
        busyEvening()
        val first = adapter(at(7, 30)).pull(null).episodes.single()
        val again = adapter(at(7, 50)).pull(Checkpoint(at(7, 30).toString())).episodes.single()
        assertThat(again.origin.recordId).isEqualTo(first.origin.recordId)
        assertThat(again.id).isEqualTo(first.id)
    }

    @Test fun `pull without any permission returns nothing and does not throw`() = runBlocking {
        system.usage = false; system.recognition = false
        val r = adapter(at(8)).pull(null)
        assertThat(r.episodes).isEmpty()
    }

    @Test fun `pull emits screen charging and alarm signals including an estimated dismissal`() = runBlocking {
        busyEvening()
        store.addCharging(at(23, 20, -1), true, at(23, 20, -1))
        store.addAlarm(AlarmSample(at(21, 30, -1), at(7, 0)), at(21, 30, -1))
        val signals = adapter(at(7, 30)).pull(null).signals
        assertThat(signals.filterIsInstance<Signal.ScreenEvent>()).isNotEmpty()
        assertThat(signals.filterIsInstance<Signal.ChargingEvent>().map { it.plugged }).contains(true)
        val alarms = signals.filterIsInstance<Signal.AlarmEvent>()
        assertThat(alarms.map { it.kind }).containsAtLeast(AlarmKind.SCHEDULED, AlarmKind.DISMISSED)
        assertThat(alarms.first { it.kind == AlarmKind.DISMISSED }.at).isEqualTo(at(7, 5))
        assertThat(signals.all { it.origin.qualityTier == QualityTier.PHONE_INFERRED }).isTrue()
    }

    @Test fun `sampling records plugged state next alarm and steps`() = runBlocking {
        system.plugged = true
        system.alarm = at(7, 0)
        system.steps = 1000L to 42L
        PhoneSampler(system, store).sample(at(21, 30, -1))
        assertThat(store.charging().single().plugged).isTrue()
        assertThat(store.alarms().single().triggerAt).isEqualTo(at(7, 0))
        assertThat(store.steps().single().total).isEqualTo(1000L)
    }

    @Test fun `an alarm already in the past is not stored`() = runBlocking {
        system.alarm = at(6, 0)
        PhoneSampler(system, store).sample(at(8))
        assertThat(store.alarms()).isEmpty()
    }

    @Test fun `next sample slot is evening or deep night in local time`() {
        val z = ZoneId.of("Europe/London")
        assertThat(PhoneSampler.nextSlot(Instant.parse("2026-03-04T10:00:00Z"), z)).isEqualTo(Instant.parse("2026-03-04T21:30:00Z"))
        assertThat(PhoneSampler.nextSlot(Instant.parse("2026-03-04T22:00:00Z"), z)).isEqualTo(Instant.parse("2026-03-05T02:30:00Z"))
    }

    @Test fun `store round trips and prunes old data`() {
        val now = at(12)
        store.addSegments(listOf(SleepSegment(now.minus(Duration.ofDays(40)), now.minus(Duration.ofDays(40)).plusSeconds(3600), 0), SleepSegment(at(0), at(6), 0)), now)
        assertThat(store.segments()).hasSize(1)
        assertThat(store.sleepApiDelivering(now)).isTrue()
        store.addCharging(at(1), true, now); store.addCharging(at(2), true, now); store.addCharging(at(3), false, now)
        assertThat(store.charging().map { it.plugged }).containsExactly(true, false).inOrder()
        store.addClassify(listOf(ClassifySample(at(1), 80, 1, 2), ClassifySample(at(1), 80, 1, 2)), now)
        assertThat(store.classify()).hasSize(1)
    }
}
