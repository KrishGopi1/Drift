package app.drift.source.phone

import android.Manifest
import android.content.Context
import app.drift.model.AlarmKind
import app.drift.model.Checkpoint
import app.drift.model.DataQuality
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.QualityTier
import app.drift.model.Signal
import app.drift.model.SleepEpisode
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.source.Capability
import app.drift.model.source.PullResult
import app.drift.model.source.SourceAdapter
import app.drift.model.source.SourceStatus
import app.drift.source.phone.detect.ChargeTimeline
import app.drift.source.phone.detect.NightAnalyzer
import app.drift.source.phone.detect.PhoneDataSet
import app.drift.source.phone.detect.ScreenTimeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Phone-only sleep detection (plan 5.3): reconstructs each night after the fact from screen history,
 * the Sleep API, charging, steps and the alarm clock. Works with reduced confidence if either
 * permission is missing, and never fabricates a night from the clock alone.
 */
class PhoneSourceAdapter internal constructor(
    private val system: PhoneSystem,
    private val store: PhoneStore,
    private val clock: Clock,
    private val zoneProvider: () -> ZoneId,
) : SourceAdapter {

    @Inject constructor(@ApplicationContext context: Context) :
        this(AndroidPhoneSystem(context), PhoneStore(context), Clock.systemUTC(), { ZoneId.systemDefault() })

    override val id: SourceId = SourceId.PHONE
    override val capabilities: Set<Capability> = setOf(
        Capability.SLEEP_SESSIONS, Capability.STEPS, Capability.SCREEN, Capability.MOTION, Capability.CHARGING, Capability.ALARMS,
    )
    override val qualityTier: QualityTier = QualityTier.PHONE_INFERRED

    override suspend fun status(): SourceStatus {
        val missing = buildSet {
            if (!system.hasUsageAccess()) add(PERMISSION_USAGE_ACCESS)
            if (!system.hasActivityRecognition()) add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (system.hasActivityRecognition()) system.registerSleepApi()
        return if (missing.isEmpty()) SourceStatus.Available else SourceStatus.NeedsPermission(missing)
    }

    override suspend fun pull(since: Checkpoint?): PullResult = withContext(Dispatchers.Default) {
        val now = clock.instant()
        val zone = zoneProvider()
        try {
            PhoneSampler(system, store).sample(now)
            if (system.hasActivityRecognition()) system.registerSleepApi()
            reconstruct(since, now, zone)
        } catch (_: SecurityException) {
            PullResult(emptyList(), emptyList(), Checkpoint(now.toString()))
        }
    }

    private fun reconstruct(since: Checkpoint?, now: Instant, zone: ZoneId): PullResult {
        val oldest = now.minus(LOOKBACK)
        val from = since?.token?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?.minus(Duration.ofDays(1))?.coerceAtLeast(oldest) ?: oldest
        val firstDay = from.atZone(zone).toLocalDate()
        val lastDay = now.atZone(zone).toLocalDate()

        // Screen events are fetched with margin so the state at each window start is known.
        val screenQuery = Interval(from.minus(SCREEN_MARGIN), now)
        val screenChanges = if (system.hasUsageAccess()) system.screenChanges(screenQuery.start, screenQuery.end) else emptyList()
        val screen = if (system.hasUsageAccess()) ScreenTimeline(screenChanges) else null
        val data = PhoneDataSet(
            screen = screen,
            segments = store.segments(),
            classify = store.classify(),
            charging = ChargeTimeline(store.charging()),
            steps = stepSpans(store.steps()),
            sleepApiActive = store.sleepApiDelivering(now),
        )

        val episodes = mutableListOf<SleepEpisode>()
        var day: LocalDate = firstDay
        while (!day.isAfter(lastDay)) {
            NightAnalyzer.analyze(day, zone, now, data)?.let { found ->
                if (found.wake <= now) episodes += episode(day, zone, found.onset, found.wake, found.confidence, found.completeness)
            }
            day = day.plusDays(1)
        }

        val signals = buildSignals(screenChanges.filter { it.at >= from }, from, now, data, screen)
        return PullResult(episodes = episodes, signals = signals, next = Checkpoint(now.toString()))
    }

    private fun episode(day: LocalDate, zone: ZoneId, onset: Instant, wake: Instant, confidence: Double, completeness: Double) = SleepEpisode(
        id = EpisodeId("phone-$day"),
        start = onset,
        end = wake,
        zone = zone,
        kind = EpisodeKind.MAIN,
        origin = ref("night-$day"),
        quality = DataQuality(completeness = completeness.coerceIn(0.0, 1.0), detectionConfidence = confidence.coerceIn(0.0, 1.0)),
    )

    private fun ref(recordId: String) = SourceRef(SourceId.PHONE, "phone-$recordId", QualityTier.PHONE_INFERRED, dataOrigin = "android-system")

    private fun buildSignals(screenChanges: List<ScreenChange>, from: Instant, now: Instant, data: PhoneDataSet, screen: ScreenTimeline?): List<Signal> {
        val out = mutableListOf<Signal>()
        screenChanges.forEach { out += Signal.ScreenEvent(ref("screen-${it.at.toEpochMilli()}"), it.at, it.interactive) }
        store.charging().filter { it.at >= from }.forEach { out += Signal.ChargingEvent(ref("charge-${it.at.toEpochMilli()}"), it.at, it.plugged) }
        data.steps.filter { it.end >= from }.forEach {
            out += Signal.StepBucket(ref("steps-${it.start.toEpochMilli()}"), Interval(it.start, it.end), it.count)
        }
        data.classify.filter { it.at >= from }.groupBy { it.at.toEpochMilli() / MOTION_BUCKET.toMillis() }.forEach { (k, list) ->
            val start = Instant.ofEpochMilli(k * MOTION_BUCKET.toMillis())
            out += Signal.MotionBucket(
                origin = ref("motion-${start.toEpochMilli()}"),
                interval = Interval(start, start.plus(MOTION_BUCKET)),
                motionLevel = (list.map { it.motion }.average() / 100.0).coerceIn(0.0, 1.0),
                sleepConfidence = (list.map { it.confidence }.average() / 100.0).coerceIn(0.0, 1.0),
            )
        }
        store.alarms().filter { it.triggerAt >= from }.forEach { a ->
            out += Signal.AlarmEvent(ref("alarm-${a.triggerAt.toEpochMilli()}"), a.triggerAt, AlarmKind.SCHEDULED)
            if (a.triggerAt <= now) {
                // The first screen-on soon after the alarm time is the best available estimate of dismissal.
                screen?.firstScreenOnIn(a.triggerAt, a.triggerAt.plus(DISMISS_WINDOW))?.let {
                    out += Signal.AlarmEvent(ref("alarm-dismissed-${a.triggerAt.toEpochMilli()}"), it, AlarmKind.DISMISSED)
                }
            }
        }
        return out
    }

    private fun Instant.coerceAtLeast(min: Instant) = if (this < min) min else this

    companion object {
        /** Name used for the usage-access entry in [SourceStatus.NeedsPermission.missing]; :app maps it to SourceFix.GrantUsageAccess. */
        const val PERMISSION_USAGE_ACCESS = "android.permission.PACKAGE_USAGE_STATS"
        private val LOOKBACK: Duration = Duration.ofDays(7)
        private val SCREEN_MARGIN: Duration = Duration.ofHours(30)
        private val MOTION_BUCKET: Duration = Duration.ofMinutes(10)
        private val DISMISS_WINDOW: Duration = Duration.ofMinutes(90)
    }
}
