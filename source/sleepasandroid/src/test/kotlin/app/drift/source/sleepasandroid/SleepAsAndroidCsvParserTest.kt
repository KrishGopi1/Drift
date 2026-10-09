package app.drift.source.sleepasandroid

import app.drift.model.EpisodeKind
import app.drift.model.QualityTier
import app.drift.model.Signal
import app.drift.model.SleepStage
import app.drift.model.SourceId
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class SleepAsAndroidCsvParserTest {
    private val parser = SleepAsAndroidCsvParser { ZoneId.of("Europe/Berlin") }
    private fun resource(name: String) = checkNotNull(javaClass.classLoader!!.getResourceAsStream(name)) { name }
    private fun parse(name: String) = parser.parse(resource(name))

    @Test fun basicFileWithBomMultipleHeadersQuotesAndEvents() {
        val r = parse("saa_basic.csv")
        assertThat(r.rowsRejected).isEqualTo(0)
        assertThat(r.episodes).hasSize(3)
        val night1 = r.episodes[0]
        assertThat(night1.start).isEqualTo(Instant.parse("2026-10-06T21:12:00Z")) // 23:12 CEST
        assertThat(night1.end).isEqualTo(Instant.parse("2026-10-07T04:42:00Z"))
        assertThat(night1.zone).isEqualTo(ZoneId.of("Europe/Berlin"))
        assertThat(night1.kind).isEqualTo(EpisodeKind.MAIN)
        assertThat(night1.origin.sourceId).isEqualTo(SourceId.SLEEP_AS_ANDROID)
        assertThat(night1.origin.qualityTier).isEqualTo(QualityTier.PHONE_APP)
        assertThat(night1.origin.dataOrigin).isEqualTo("com.urbandroid.sleep")
        assertThat(night1.origin.recordId).isEqualTo("1791321120000")
        assertThat(night1.id.value).isEqualTo("saa-1791321120000")
        // The second header block (different labels) and multi-line quoted comment still parse.
        assertThat(r.episodes[1].end.epochSecond - r.episodes[1].start.epochSecond).isEqualTo(6 * 3600L)
        assertThat(r.episodes[2].kind).isEqualTo(EpisodeKind.NAP)
        assertThat(r.episodes[2].stages).isNull()
    }

    @Test fun eventsBecomeStageSpans() {
        val n = parse("saa_basic.csv").episodes[0]
        val stages = n.stages!!
        assertThat(stages.map { it.stage }).containsExactly(
            SleepStage.LIGHT, SleepStage.DEEP, SleepStage.REM, SleepStage.AWAKE, SleepStage.LIGHT,
        ).inOrder()
        assertThat(stages[1].interval.duration).isEqualTo(Duration.ofMinutes(60))
        assertThat(stages.first().interval.start).isEqualTo(n.start)
        assertThat(stages.last().interval.end).isEqualTo(n.end)
        stages.zipWithNext().forEach { (a, b) -> assertThat(a.interval.end).isEqualTo(b.interval.start) }
    }

    @Test fun actigraphyColumnsBecomeMotionBucketsAcrossMidnight() {
        val r = parse("saa_basic.csv")
        val motion = r.signals.filterIsInstance<Signal.MotionBucket>().filter { it.origin.recordId == "1791321120000" }
        assertThat(motion).hasSize(45)
        assertThat(motion.first().interval.start).isEqualTo(Instant.parse("2026-10-06T21:15:00Z"))
        assertThat(motion.first().interval.duration).isEqualTo(Duration.ofMinutes(10))
        motion.zipWithNext().forEach { (a, b) -> assertThat(b.interval.start).isGreaterThan(a.interval.start) }
        assertThat(motion[1].motionLevel).isEqualTo(0.7)
        assertThat(motion.last().interval.start).isLessThan(r.episodes[0].end)
    }

    @Test fun usLocaleTwelveHourClockGmtOffsetAndDayMonthDisambiguation() {
        val r = parser.parse(resource("saa_us_locale.csv"))
        assertThat(r.rowsRejected).isEqualTo(0)
        val us = r.episodes[0]
        assertThat(us.zone).isEqualTo(ZoneId.of("GMT-05:00"))
        assertThat(us.start).isEqualTo(Instant.parse("2026-03-16T04:30:00Z"))
        assertThat(us.end).isEqualTo(Instant.parse("2026-03-16T11:30:00Z"))
        // "03/04/2026" with an id in April means 3 April, not 4 March.
        val dayFirst = r.episodes[1]
        assertThat(dayFirst.start).isEqualTo(Instant.parse("2026-04-03T23:00:00Z"))
        assertThat(dayFirst.end).isEqualTo(Instant.parse("2026-04-04T07:00:00Z"))
        assertThat(dayFirst.zone).isEqualTo(ZoneId.of("UTC"))
    }

    @Test fun semicolonDelimiterDecimalCommasAndBadRowsAreSkippedAndCounted() {
        val r = parse("saa_semicolon_bad_rows.csv")
        assertThat(r.episodes.map { it.origin.recordId }.distinct()).hasSize(2)
        assertThat(r.rowsRejected).isEqualTo(3) // orphan row, unreadable dates, end before start
        assertThat(r.warnings.joinToString("\n")).contains("Skipped")
        val good = r.episodes.first { it.start == Instant.parse("2026-09-10T20:50:00Z") }
        assertThat(good.end).isEqualTo(Instant.parse("2026-09-11T04:10:00Z"))
        val mars = r.episodes.first { it.origin.recordId == "1789200000000" }
        assertThat(mars.zone).isEqualTo(ZoneId.of("Europe/Berlin")) // unknown zone falls back to the device zone
        assertThat(r.warnings.joinToString()).contains("Unknown time zone")
        assertThat(r.warnings.joinToString()).contains("Duplicate record")
        val motion = r.signals.filterIsInstance<Signal.MotionBucket>().filter { it.origin.recordId == good.origin.recordId }
        assertThat(motion.map { it.motionLevel }).containsExactly(1.5, 0.25).inOrder()
    }

    @Test fun emptyAndBinaryInputsDoNotThrow() {
        val empty = parse("saa_empty.csv")
        assertThat(empty.episodes).isEmpty()
        assertThat(empty.rowsRejected).isEqualTo(0)
        val garbage = parse("saa_garbage.csv")
        assertThat(garbage.episodes).isEmpty()
    }

    @Test fun headerOnlyFileYieldsNothing() {
        val r = parser.parse(ByteArrayInputStream("Id,Tz,From,To,Sched,Hours,Rating\n".toByteArray()))
        assertThat(r.episodes).isEmpty()
        assertThat(r.rowsRejected).isEqualTo(0)
    }

    @Test fun recordWithoutIdStillImportsWithSyntheticId() {
        val csv = "Id,Tz,From,To\n,Europe/Berlin,\"2026-10-01 23:00:00\",\"2026-10-02 07:00:00\"\n"
        val r = parser.parse(ByteArrayInputStream(csv.toByteArray()))
        assertThat(r.episodes).hasSize(1)
        assertThat(r.episodes[0].origin.recordId).startsWith("t")
    }

    @Test fun eventsInSeparateRowsAttachToThePrecedingRecord() {
        val csv = """
            Id,Tz,From,To
            1790000000000,UTC,"2026-09-21 14:13:20","2026-09-21 22:13:20"
            Event,"DEEP_START-1790003600000","DEEP_END-1790007200000"
        """.trimIndent()
        val r = parser.parse(ByteArrayInputStream(csv.toByteArray()))
        assertThat(r.rowsRejected).isEqualTo(0)
        assertThat(r.episodes.single().stages!!.single().stage).isEqualTo(SleepStage.DEEP)
    }

    @Test fun absurdDurationsAreRejected() {
        val csv = "Id,Tz,From,To\n1790000000000,UTC,\"2026-09-01 00:00:00\",\"2026-09-20 00:00:00\"\n"
        val r = parser.parse(ByteArrayInputStream(csv.toByteArray()))
        assertThat(r.episodes).isEmpty()
        assertThat(r.rowsRejected).isEqualTo(1)
    }

    @Test fun adapterExposesImportAndReportsInstallState() = kotlinx.coroutines.test.runTest {
        var installed = false
        val adapter = SleepAsAndroidSourceAdapter({ installed }, parser)
        assertThat(adapter.status()).isEqualTo(app.drift.model.source.SourceStatus.NotInstalled)
        installed = true
        assertThat(adapter.status()).isEqualTo(app.drift.model.source.SourceStatus.Available)
        val pull = adapter.pull(app.drift.model.Checkpoint("x"))
        assertThat(pull.episodes).isEmpty()
        assertThat(pull.next).isEqualTo(app.drift.model.Checkpoint("x"))
        assertThat(adapter.importBackup(resource("saa_basic.csv")).episodes).hasSize(3)
    }
}
