package app.drift.source.sleepasandroid

import app.drift.model.DataQuality
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.QualityTier
import app.drift.model.Signal
import app.drift.model.SleepEpisode
import app.drift.model.SleepStage
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.StageSpan
import java.io.InputStream
import java.io.InputStreamReader
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale

/** Result of parsing a Sleep as Android CSV backup. */
data class SleepAsAndroidImport(
    val episodes: List<SleepEpisode>,
    val signals: List<Signal>,
    /** Rows that looked like records but could not be used (bad dates, impossible spans, ...). */
    val rowsRejected: Int,
    val warnings: List<String>,
)

/**
 * Tolerant parser for Sleep as Android's CSV backup export.
 *
 * Layout handled (the format is not formally specified, so the parser keys on column names, not positions):
 * - A header row starting with `Id` names the columns (Id, Tz, From, To, Sched, Hours, Rating, Comment,
 *   Framerate, Snore, Noise, Cycles, DeepSleep, LenAdjust, Geo) followed by actigraphy time-of-day labels
 *   (`0:15`, `0:25`, ...) and optionally `Event`. A file holds many header blocks; each applies to the rows
 *   below it until the next header.
 * - Data rows carry the record; actigraphy values line up with the header's time labels.
 * - Event cells look like `DEEP_START-1587339200000` and may sit in the record row or in following rows.
 * - Dates come in several locale formats; the `Id` (start time in epoch milliseconds) disambiguates day/month order.
 * - Quoted fields, doubled quotes, newlines inside quotes, BOM, CRLF and `,`/`;`/tab delimiters are accepted.
 * Unusable rows are skipped and counted; nothing here throws for bad content.
 */
class SleepAsAndroidCsvParser(private val defaultZone: () -> ZoneId = { ZoneId.systemDefault() }) {

    fun parse(input: InputStream): SleepAsAndroidImport {
        val reader = CsvReader(InputStreamReader(input, Charsets.UTF_8))
        val state = ParseState()
        while (true) {
            val row = reader.nextRow() ?: break
            if (row.isEmpty() || row.all { it.isBlank() }) continue
            state.accept(row)
        }
        state.flush()
        return state.result()
    }

    private inner class ParseState {
        private var header: Header? = null
        private var current: Pending? = null
        private val records = LinkedHashMap<String, Built>()
        private var rejected = 0
        private val warnings = ArrayList<String>()
        private var rowNumber = 0

        fun accept(row: List<String>) {
            rowNumber++
            val first = row[0].trim()
            if (first.equals("Id", ignoreCase = true) && row.size > 3) {
                flush()
                header = Header.from(row)
                return
            }
            val h = header
            if (h == null) {
                reject("row $rowNumber appears before any header")
                return
            }
            val events = row.mapNotNull(::parseEvent)
            val looksLikeRecord = h.from != null && row.size > h.from && isRecordRow(first, row, h)
            if (looksLikeRecord) {
                flush()
                current = Pending(h, row).also { it.events += events }
            } else if (events.isNotEmpty() && current != null) {
                current!!.events += events
            } else if (events.isEmpty() && first.equals("Event", ignoreCase = true)) {
                // Label-only line announcing event rows.
            } else {
                reject("row $rowNumber is not a recognisable record")
            }
        }

        private fun isRecordRow(first: String, row: List<String>, h: Header): Boolean {
            val fromCell = row.getOrNull(h.from ?: return false)?.trim().orEmpty()
            if (first.equals("Event", ignoreCase = true) || parseEvent(fromCell) != null) return false
            return fromCell.isNotEmpty() && (first.all { it.isDigit() } || fromCell.any { it.isDigit() })
        }

        fun flush() {
            val p = current ?: return
            current = null
            val built = build(p) ?: return
            val key = built.episode.origin.recordId
            if (records.put(key, built) != null) warnings += "Duplicate record $key in file; keeping the later copy."
        }

        private fun build(p: Pending): Built? {
            val h = p.header
            val row = p.row
            fun cell(i: Int?) = i?.let { row.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
            val id = cell(h.id)
            val zone = zoneFrom(cell(h.tz))
            val idEpoch = id?.let(::epochFromId)
            val fromText = cell(h.from)
            val toText = cell(h.to)
            if (fromText == null || toText == null) { reject("record ${id ?: rowNumber} lacks From/To"); return null }
            val span = parseSpan(fromText, toText, zone, idEpoch)
            if (span == null) { reject("record ${id ?: rowNumber}: unreadable dates '$fromText' / '$toText'"); return null }
            val (start, end) = span
            if (!end.isAfter(start) || Duration.between(start, end) > MAX_SESSION) {
                reject("record ${id ?: rowNumber}: implausible span $start to $end")
                return null
            }
            val recordId = id ?: "t${start.toEpochMilli()}"
            val origin = SourceRef(SourceId.SLEEP_AS_ANDROID, recordId, QualityTier.PHONE_APP, SOURCE_PACKAGE, null)
            val stages = buildStages(p.events, start, end)
            val episode = SleepEpisode(
                id = EpisodeId("saa-$recordId"),
                start = start, end = end, zone = zone,
                kind = kindOf(start, end, zone),
                stages = stages,
                inBed = Interval(start, end),
                origin = origin,
                quality = DataQuality.RECORDED,
            )
            return Built(episode, motionSignals(h, row, origin, start, end, zone))
        }

        private fun zoneFrom(text: String?): ZoneId {
            if (text == null) return defaultZone()
            return try {
                ZoneId.of(text.replace(" ", ""))
            } catch (e: DateTimeException) {
                warnOnce("Unknown time zone '$text'; using the device zone.")
                defaultZone()
            }
        }

        private fun reject(message: String) {
            rejected++
            if (warnings.size < MAX_WARNINGS) warnings += "Skipped: $message"
        }

        private fun warnOnce(message: String) {
            if (message !in warnings && warnings.size < MAX_WARNINGS) warnings += message
        }

        fun result(): SleepAsAndroidImport {
            val built = records.values.sortedBy { it.episode.start }
            return SleepAsAndroidImport(built.map { it.episode }, built.flatMap { it.signals }, rejected, warnings.toList())
        }
    }

    private class Pending(val header: Header, val row: List<String>) { val events = ArrayList<Event>() }
    private class Built(val episode: SleepEpisode, val signals: List<Signal>)
    private class Event(val label: String, val at: Instant)

    private class Header(
        val id: Int?, val tz: Int?, val from: Int?, val to: Int?,
        /** Column index to time-of-day for the actigraphy columns. */
        val actigraphy: List<Pair<Int, LocalTime>>,
    ) {
        companion object {
            fun from(row: List<String>): Header {
                val names = row.map { it.trim().lowercase(Locale.ROOT) }
                fun idx(n: String) = names.indexOf(n).takeIf { it >= 0 }
                val acti = row.mapIndexedNotNull { i, raw -> parseClockLabel(raw.trim())?.let { i to it } }
                return Header(idx("id"), idx("tz"), idx("from"), idx("to"), acti)
            }
        }
    }

    // ---- dates ---------------------------------------------------------------------------------------

    private fun epochFromId(id: String): Instant? {
        val n = id.toLongOrNull() ?: return null
        return when {
            n in 946_684_800_000L..4_102_444_800_000L -> Instant.ofEpochMilli(n)
            n in 946_684_800L..4_102_444_800L -> Instant.ofEpochSecond(n)
            else -> null
        }
    }

    /** Picks the date format whose reading of [from] lies closest to the id's epoch (or the first that parses). */
    private fun parseSpan(from: String, to: String, zone: ZoneId, idEpoch: Instant?): Pair<Instant, Instant>? {
        var best: Pair<Instant, Instant>? = null
        var bestDistance = Long.MAX_VALUE
        for (format in FORMATS) {
            val a = parseLocal(from, format) ?: continue
            val b = parseLocal(to, format) ?: continue
            val start = a.atZone(zone).toInstant()
            val end = b.atZone(zone).toInstant()
            if (idEpoch == null) return start to end
            val distance = Duration.between(idEpoch, start).abs().toMillis()
            if (distance < bestDistance) { best = start to end; bestDistance = distance }
        }
        // A start more than a day from the id means the text and the id disagree; trust the text only if nothing nearer exists.
        return best
    }

    private fun parseLocal(text: String, format: DateTimeFormatter): LocalDateTime? = try {
        LocalDateTime.parse(text.trim().replace(Regex("\\s+"), " "), format)
    } catch (e: DateTimeParseException) {
        null
    }

    // ---- events and stages ---------------------------------------------------------------------------

    private fun parseEvent(cell: String): Event? {
        val m = EVENT.matchEntire(cell.trim()) ?: return null
        val ms = m.groupValues[2].toLongOrNull() ?: return null
        val instant = if (m.groupValues[2].length >= 12) Instant.ofEpochMilli(ms) else Instant.ofEpochSecond(ms)
        return Event(m.groupValues[1], instant)
    }

    private fun buildStages(events: List<Event>, start: Instant, end: Instant): List<StageSpan>? {
        val window = Interval(start, end)
        val stageEvents = events.mapNotNull { e ->
            val (stage, isStart) = when (e.label) {
                "DEEP_START" -> SleepStage.DEEP to true
                "DEEP_END" -> SleepStage.DEEP to false
                "LIGHT_START" -> SleepStage.LIGHT to true
                "LIGHT_END" -> SleepStage.LIGHT to false
                "REM_START" -> SleepStage.REM to true
                "REM_END" -> SleepStage.REM to false
                "AWAKE_START" -> SleepStage.AWAKE to true
                "AWAKE_END" -> SleepStage.AWAKE to false
                else -> return@mapNotNull null
            }
            Triple(e.at, stage, isStart)
        }.sortedBy { it.first }
        if (stageEvents.isEmpty()) return null
        val spans = ArrayList<StageSpan>()
        var openStage: SleepStage? = null
        var openAt = start
        fun close(at: Instant) {
            val s = openStage ?: return
            Interval(openAt, at).intersect(window)?.takeIf { it.duration > Duration.ZERO }?.let { spans += StageSpan(s, it) }
            openStage = null
        }
        for ((at, stage, isStart) in stageEvents) {
            if (isStart) { close(at); openStage = stage; openAt = at } else if (openStage == stage) close(at)
        }
        close(end)
        return spans.takeIf { it.isNotEmpty() }
    }

    // ---- actigraphy ----------------------------------------------------------------------------------

    private fun motionSignals(h: Header, row: List<String>, origin: SourceRef, start: Instant, end: Instant, zone: ZoneId): List<Signal> {
        if (h.actigraphy.size < 2) return emptyList()
        val startLocal = start.atZone(zone).toLocalDateTime()
        // Labels run forward from the start; roll the date whenever the clock time wraps past midnight.
        var cursor = startLocal.toLocalDate().atTime(h.actigraphy.first().second)
        if (cursor.isBefore(startLocal.minusHours(12))) cursor = cursor.plusDays(1)
        val times = ArrayList<Instant>(h.actigraphy.size)
        var prev: LocalDateTime? = null
        for ((_, t) in h.actigraphy) {
            var at = cursor.toLocalDate().atTime(t)
            if (prev != null && at.isBefore(prev)) at = at.plusDays(1)
            prev = at
            cursor = at
            times += at.atZone(zone).toInstant()
        }
        val out = ArrayList<Signal>()
        for (i in h.actigraphy.indices) {
            val value = row.getOrNull(h.actigraphy[i].first)?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: continue
            val s = times[i]
            val e = if (i + 1 < times.size) times[i + 1] else s.plus(Duration.between(times[maxOf(i - 1, 0)], s).coerceAtLeast(Duration.ofMinutes(1)))
            if (!e.isAfter(s) || s.isBefore(start.minus(Duration.ofHours(1))) || s.isAfter(end.plus(Duration.ofHours(1)))) continue
            out += Signal.MotionBucket(origin, Interval(s, e), value, null)
        }
        return out
    }

    private fun kindOf(start: Instant, end: Instant, zone: ZoneId): EpisodeKind {
        val d = Duration.between(start, end)
        if (d >= Duration.ofHours(3)) return EpisodeKind.MAIN
        val mid = start.plus(d.dividedBy(2)).atZone(zone).toLocalTime()
        return if (mid.hour in 9..19) EpisodeKind.NAP else EpisodeKind.MAIN
    }

    companion object {
        const val SOURCE_PACKAGE = "com.urbandroid.sleep"
        private const val MAX_WARNINGS = 25
        private val MAX_SESSION: Duration = Duration.ofHours(24)
        private val EVENT = Regex("^([A-Z][A-Z0-9_]+)-(\\d{9,13})(?:-.*)?$")
        private val CLOCK_LABEL = Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$")

        internal fun parseClockLabel(text: String): LocalTime? {
            if (!CLOCK_LABEL.matches(text)) return null
            val parts = text.split(':').map { it.toInt() }
            return try { LocalTime.of(parts[0], parts[1], parts.getOrElse(2) { 0 }) } catch (e: DateTimeException) { null }
        }

        private fun f(pattern: String): DateTimeFormatter =
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH)

        /** Observed and likely locale formats. Day/month order is settled against the record Id. */
        private val FORMATS: List<DateTimeFormatter> = listOf(
            "d. M. yyyy H:mm", "d. M. yyyy H:mm:ss", "d.M.yyyy H:mm", "d.M.yyyy H:mm:ss",
            "yyyy-M-d H:mm", "yyyy-M-d H:mm:ss", "yyyy-M-d'T'H:mm:ss", "yyyy/M/d H:mm", "yyyy/M/d H:mm:ss", "yyyy. M. d. H:mm",
            "M/d/yyyy h:mm a", "M/d/yyyy h:mm:ss a", "M/d/yyyy H:mm", "d/M/yyyy H:mm", "d/M/yyyy h:mm a", "d-M-yyyy H:mm",
            "d MMM yyyy H:mm", "d MMM yyyy h:mm a", "MMM d, yyyy h:mm a", "MMM d, yyyy H:mm", "d MMMM yyyy H:mm",
        ).map(::f)
    }
}
