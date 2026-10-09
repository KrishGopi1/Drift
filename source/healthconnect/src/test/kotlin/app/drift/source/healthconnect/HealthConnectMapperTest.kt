package app.drift.source.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import app.drift.model.ActivityType
import app.drift.model.EpisodeKind
import app.drift.model.QualityTier
import app.drift.model.Signal
import app.drift.model.SleepStage
import app.drift.model.SourceId
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class HealthConnectMapperTest {
    private val mapper = HealthConnectMapper(defaultZone = { ZoneId.of("Europe/Berlin") })
    private val start = Instant.parse("2026-10-08T21:00:00Z")
    private val end = Instant.parse("2026-10-09T05:00:00Z")

    @Test fun sleepSessionWithStagesMapsToEpisode() {
        val stages = listOf(
            SleepSessionRecord.Stage(start, start.plusSeconds(3600), SleepSessionRecord.STAGE_TYPE_LIGHT),
            SleepSessionRecord.Stage(start.plusSeconds(3600), start.plusSeconds(7200), SleepSessionRecord.STAGE_TYPE_DEEP),
            SleepSessionRecord.Stage(start.plusSeconds(7200), start.plusSeconds(7800), SleepSessionRecord.STAGE_TYPE_AWAKE),
            SleepSessionRecord.Stage(start.plusSeconds(7800), start.plusSeconds(9000), SleepSessionRecord.STAGE_TYPE_REM),
            SleepSessionRecord.Stage(start.plusSeconds(9000), start.plusSeconds(9600), SleepSessionRecord.STAGE_TYPE_OUT_OF_BED),
            SleepSessionRecord.Stage(start.plusSeconds(9600), start.plusSeconds(9700), SleepSessionRecord.STAGE_TYPE_UNKNOWN),
            SleepSessionRecord.Stage(start.plusSeconds(9700), start.plusSeconds(9800), SleepSessionRecord.STAGE_TYPE_SLEEPING),
        )
        val ep = mapper.map(listOf(sleepRecord("s1", start, end, stages = stages, offset = ZoneOffset.ofHours(2)))).episodes.single()
        assertThat(ep.id.value).isEqualTo("hc-s1")
        assertThat(ep.origin.sourceId).isEqualTo(SourceId.HEALTH_CONNECT)
        assertThat(ep.origin.recordId).isEqualTo("s1")
        assertThat(ep.origin.dataOrigin).isEqualTo("com.fitbit.FitbitMobile")
        assertThat(ep.origin.qualityTier).isEqualTo(QualityTier.WEARABLE_WRIST)
        assertThat(ep.zone).isEqualTo(ZoneId.of("Europe/Berlin")) // +02:00 matches system zone in October
        assertThat(ep.kind).isEqualTo(EpisodeKind.MAIN)
        assertThat(ep.stages!!.map { it.stage }).containsExactly(
            SleepStage.LIGHT, SleepStage.DEEP, SleepStage.AWAKE, SleepStage.REM, SleepStage.AWAKE, SleepStage.UNKNOWN, SleepStage.LIGHT,
        ).inOrder()
        assertThat(ep.inBed!!.start).isEqualTo(start)
        assertThat(ep.quality.completeness).isLessThan(0.5)
    }

    @Test fun sessionWithoutStagesHasNullStagesAndFullCompleteness() {
        val ep = mapper.map(listOf(sleepRecord("s", start, end))).episodes.single()
        assertThat(ep.stages).isNull()
        assertThat(ep.quality.completeness).isEqualTo(1.0)
    }

    @Test fun foreignOffsetKeepsOffsetAsZone() {
        val ep = mapper.map(listOf(sleepRecord("s", start, end, offset = ZoneOffset.ofHoursMinutes(5, 30)))).episodes.single()
        assertThat(ep.zone).isEqualTo(ZoneOffset.ofHoursMinutes(5, 30))
    }

    @Test fun missingOffsetUsesSystemZone() {
        val ep = mapper.map(listOf(sleepRecord("s", start, end, offset = null))).episodes.single()
        assertThat(ep.zone).isEqualTo(ZoneId.of("Europe/Berlin"))
    }

    @Test fun middayShortSessionIsNap() {
        val ep = mapper.map(listOf(sleepRecord("n", Instant.parse("2026-10-09T12:00:00Z"), Instant.parse("2026-10-09T12:40:00Z")))).episodes.single()
        assertThat(ep.kind).isEqualTo(EpisodeKind.NAP)
        val night = mapper.map(listOf(sleepRecord("m", Instant.parse("2026-10-09T01:00:00Z"), Instant.parse("2026-10-09T02:00:00Z")))).episodes.single()
        assertThat(night.kind).isEqualTo(EpisodeKind.MAIN)
    }

    @Test fun originAndDeviceDriveTier() {
        val ring = Device(type = Device.TYPE_RING, manufacturer = "Acme", model = "R1")
        val unknownPkgRing = mapper.map(listOf(sleepRecord("a", start, end, pkg = "com.unknown.app", device = ring))).episodes.single()
        assertThat(unknownPkgRing.origin.qualityTier).isEqualTo(QualityTier.WEARABLE_RING)
        assertThat(unknownPkgRing.origin.device).isEqualTo("Acme R1")
        val saa = mapper.map(listOf(sleepRecord("b", start, end, pkg = "com.urbandroid.sleep"))).episodes.single()
        assertThat(saa.origin.qualityTier).isEqualTo(QualityTier.PHONE_APP)
        val unknown = mapper.map(listOf(sleepRecord("c", start, end, pkg = "com.unknown.app"))).episodes.single()
        assertThat(unknown.origin.qualityTier).isEqualTo(OriginTierTable.UNKNOWN_ORIGIN_TIER)
        val oura = mapper.map(listOf(sleepRecord("d", start, end, pkg = "com.ouraring.oura"))).episodes.single()
        assertThat(oura.origin.qualityTier).isEqualTo(QualityTier.WEARABLE_RING)
    }

    @Test fun boatSlotCanBeRegistered() {
        val table = OriginTierTable.DEFAULT.with("com.example.boat" to QualityTier.WEARABLE_RING)
        assertThat(table.tierFor("com.example.boat")).isEqualTo(QualityTier.WEARABLE_RING)
        assertThat(OriginTierTable.DEFAULT.tierFor("com.example.boat")).isEqualTo(OriginTierTable.UNKNOWN_ORIGIN_TIER)
    }

    @Test fun signalsMapToCanonicalTypes() {
        val m = meta("x", "com.fitbit.FitbitMobile")
        val t = Instant.parse("2026-10-09T01:02:03Z")
        val records = listOf(
            HeartRateRecord(start, null, start.plusSeconds(900), null, listOf(
                HeartRateRecord.Sample(start.plusSeconds(10), 50), HeartRateRecord.Sample(start.plusSeconds(20), 54),
                HeartRateRecord.Sample(start.plusSeconds(400), 60),
            ), m),
            RestingHeartRateRecord(t, null, 48, m),
            HeartRateVariabilityRmssdRecord(t, null, 61.5, m),
            StepsRecord(start, null, start.plusSeconds(60), null, 77, m),
            ExerciseSessionRecord(startTime = start, startZoneOffset = null, endTime = start.plusSeconds(1800), endZoneOffset = null, exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, metadata = m),
        )
        val signals = mapper.map(records).signals
        val hr = signals.filterIsInstance<Signal.HeartRateSample>()
        assertThat(hr.map { it.bpm }).containsExactly(52.0, 60.0).inOrder() // 5-minute bucket means
        assertThat(signals.filterIsInstance<Signal.RestingHeartRate>().single().bpm).isEqualTo(48.0)
        assertThat(signals.filterIsInstance<Signal.HrvSample>().single().rmssdMillis).isEqualTo(61.5)
        assertThat(signals.filterIsInstance<Signal.StepBucket>().single().count).isEqualTo(77)
        assertThat(signals.filterIsInstance<Signal.ActivityBucket>().single().type).isEqualTo(ActivityType.RUNNING)
        assertThat(signals.all { it.origin.recordId == "x" }).isTrue()
    }

    @Test fun stageMappingCoversAllSdkConstants() {
        assertThat(HealthConnectMapper.mapStage(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED)).isEqualTo(SleepStage.AWAKE)
        assertThat(HealthConnectMapper.mapStage(999)).isEqualTo(SleepStage.UNKNOWN)
    }
}
