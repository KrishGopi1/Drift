package app.drift.testing.synthetic

import app.drift.model.AlarmKind
import app.drift.model.CheckIn
import app.drift.model.DataQuality
import app.drift.model.DayAnnotation
import app.drift.model.EpisodeId
import app.drift.model.EpisodeKind
import app.drift.model.Interval
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.Signal
import app.drift.model.SleepEpisode
import app.drift.model.SleepStage
import app.drift.model.SourceId
import app.drift.model.SourceRef
import app.drift.model.StageSpan
import app.drift.model.ActivityType
import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.engine.EngineInput
import app.drift.model.engine.Goals
import app.drift.model.engine.Schedule
import app.drift.model.engine.SignalWindow
import app.drift.model.engine.UserProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** What a replay file contains once read: everything the engine replay CLI needs to build an [EngineInput]. */
data class ReplayData(
    val name: String,
    val zone: ZoneId,
    val now: Instant,
    val profile: UserProfile,
    val episodes: List<SleepEpisode>,
    val annotations: List<DayAnnotation>,
    val checkIns: List<CheckIn>,
    val signals: List<Signal>,
) {
    /** Engine input at [at] (default: [now]), restricted to data that existed by then, up to 90 days back. */
    fun toEngineInput(at: Instant = now): EngineInput {
        val horizon = at.minus(Duration.ofDays(90))
        return EngineInput(
            now = at, zone = zone,
            episodes = episodes.filter { !it.end.isAfter(at) && it.end.isAfter(horizon) },
            signals = SignalWindow(Interval(horizon, at), signals.filter { signalTime(it).let { t -> !t.isAfter(at) && t.isAfter(horizon) } }),
            profile = profile, priorState = null,
            annotations = annotations.filter { !it.date.isAfter(at.atZone(zone).toLocalDate()) },
            checkIns = checkIns.filter { !it.at.isAfter(at) },
        )
    }
}

/**
 * The replay file format ("drift-replay" version 1). See docs/qa/REPLAY_FORMAT.md for the field-by-field spec.
 *
 * ```
 * val text = ReplayJson.write(Scenarios.steadySleeper())
 * val data = ReplayJson.read(text)       // same episodes, annotations, check-ins, signals, profile, zone
 * ```
 *
 * Instants are ISO-8601 UTC strings; dates are `yyyy-MM-dd`; times are `HH:mm`; enums use their Kotlin names.
 * Ground truth is written under `truth` for synthetic data only; readers must ignore it.
 */
object ReplayJson {
    const val FORMAT = "drift-replay"
    const val VERSION = 1

    private val pretty = Json { prettyPrint = true }

    fun write(h: SyntheticHistory, includeTruth: Boolean = true, includeSignals: Boolean = true, pretty: Boolean = true): String =
        encode(
            ReplayData(h.name, h.zone, h.now, h.profile, h.episodes, h.annotations, h.checkIns, if (includeSignals) h.signals else emptyList()),
            if (includeTruth) h.truth else null, pretty,
        )

    fun write(data: ReplayData, pretty: Boolean = true): String = encode(data, null, pretty)

    private fun encode(d: ReplayData, truth: GroundTruth?, prettyPrint: Boolean): String {
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("name", d.name)
            put("zone", d.zone.id)
            put("now", d.now.toString())
            put("profile", profile(d.profile))
            put("episodes", JsonArray(d.episodes.map(::episode)))
            put("annotations", JsonArray(d.annotations.map(::annotation)))
            put("checkIns", JsonArray(d.checkIns.map(::checkIn)))
            put("signals", JsonArray(d.signals.map(::signal)))
            if (truth != null) put("truth", truth(truth))
        }
        return if (prettyPrint) pretty.encodeToString(JsonElement.serializer(), root) else root.toString()
    }

    fun read(text: String): ReplayData {
        val root = Json.parseToJsonElement(text).jsonObject
        require(root.str("format") == FORMAT) { "Not a drift-replay file" }
        require(root["version"]!!.jsonPrimitive.int == VERSION) { "Unsupported replay version" }
        return ReplayData(
            name = root.str("name"),
            zone = ZoneId.of(root.str("zone")),
            now = Instant.parse(root.str("now")),
            profile = readProfile(root["profile"]!!.jsonObject),
            episodes = root["episodes"]!!.jsonArray.map { readEpisode(it.jsonObject) },
            annotations = root["annotations"]!!.jsonArray.map { readAnnotation(it.jsonObject) },
            checkIns = root["checkIns"]!!.jsonArray.map { readCheckIn(it.jsonObject) },
            signals = (root["signals"]?.jsonArray ?: JsonArray(emptyList())).map { readSignal(it.jsonObject) },
        )
    }

    // ---- profile
    private fun profile(p: UserProfile) = buildJsonObject {
        put("ageBand", p.ageBand.name)
        put("chronotype", p.chronotype?.name)
        put("schedule", buildJsonObject {
            put("workdays", JsonArray(p.schedule.workdays.sortedBy { it.value }.map { JsonPrimitive(it.name) }))
            put("wakeTime", p.schedule.wakeTime?.toString())
            put("freeDayBedtime", p.schedule.freeDayBedtime?.toString())
            put("freeDayWakeTime", p.schedule.freeDayWakeTime?.toString())
        })
        put("goals", buildJsonObject {
            put("targetSleepMinutes", p.goals.targetSleep?.toMinutes())
            put("remindersEnabled", p.goals.remindersEnabled)
        })
    }

    private fun readProfile(o: JsonObject): UserProfile {
        val s = o["schedule"]!!.jsonObject
        val g = o["goals"]?.jsonObject
        return UserProfile(
            ageBand = AgeBand.valueOf(o.str("ageBand")),
            chronotype = o.strOrNull("chronotype")?.let(ChronotypeAnswer::valueOf),
            schedule = Schedule(
                workdays = s["workdays"]!!.jsonArray.map { DayOfWeek.valueOf(it.jsonPrimitive.content) }.toSet(),
                wakeTime = s.strOrNull("wakeTime")?.let(LocalTime::parse),
                freeDayBedtime = s.strOrNull("freeDayBedtime")?.let(LocalTime::parse),
                freeDayWakeTime = s.strOrNull("freeDayWakeTime")?.let(LocalTime::parse),
            ),
            goals = Goals(
                targetSleep = g?.get("targetSleepMinutes")?.takeIf { it !is JsonNull }?.jsonPrimitive?.long?.let(Duration::ofMinutes),
                remindersEnabled = g?.get("remindersEnabled")?.jsonPrimitive?.booleanOrNull ?: true,
            ),
        )
    }

    // ---- episodes
    private fun origin(o: SourceRef) = buildJsonObject {
        put("sourceId", o.sourceId.value)
        put("recordId", o.recordId)
        put("qualityTier", o.qualityTier.name)
        put("dataOrigin", o.dataOrigin)
        put("device", o.device)
    }

    private fun readOrigin(o: JsonObject) = SourceRef(
        SourceId(o.str("sourceId")), o.str("recordId"), QualityTier.valueOf(o.str("qualityTier")),
        o.strOrNull("dataOrigin"), o.strOrNull("device"),
    )

    private fun interval(i: Interval) = buildJsonObject { put("start", i.start.toString()); put("end", i.end.toString()) }
    private fun readInterval(o: JsonObject) = Interval(Instant.parse(o.str("start")), Instant.parse(o.str("end")))

    private fun episode(e: SleepEpisode) = buildJsonObject {
        put("id", e.id.value)
        put("start", e.start.toString())
        put("end", e.end.toString())
        put("zone", e.zone.id)
        put("kind", e.kind.name)
        put("origin", origin(e.origin))
        put("quality", buildJsonObject {
            put("completeness", e.quality.completeness)
            put("detectionConfidence", e.quality.detectionConfidence)
            put("flags", JsonArray(e.quality.flags.map { JsonPrimitive(it.name) }.sortedBy { it.content }))
        })
        put("userEdited", e.userEdited)
        put("inBed", e.inBed?.let(::interval) ?: JsonNull)
        put("stages", e.stages?.let { l ->
            JsonArray(l.map { s -> buildJsonObject { put("stage", s.stage.name); put("start", s.interval.start.toString()); put("end", s.interval.end.toString()) } })
        } ?: JsonNull)
    }

    private fun readEpisode(o: JsonObject): SleepEpisode {
        val q = o["quality"]?.jsonObject
        return SleepEpisode(
            id = EpisodeId(o.str("id")),
            start = Instant.parse(o.str("start")),
            end = Instant.parse(o.str("end")),
            zone = ZoneId.of(o.str("zone")),
            kind = EpisodeKind.valueOf(o.str("kind")),
            stages = o["stages"]?.takeIf { it !is JsonNull }?.jsonArray?.map {
                val s = it.jsonObject
                StageSpan(SleepStage.valueOf(s.str("stage")), readInterval(s))
            },
            inBed = o["inBed"]?.takeIf { it !is JsonNull }?.jsonObject?.let(::readInterval),
            origin = readOrigin(o["origin"]!!.jsonObject),
            quality = if (q == null) DataQuality.RECORDED else DataQuality(
                q["completeness"]!!.jsonPrimitive.double,
                q["detectionConfidence"]!!.jsonPrimitive.double,
                q["flags"]!!.jsonArray.map { QualityFlag.valueOf(it.jsonPrimitive.content) }.toSet(),
            ),
            userEdited = o["userEdited"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

    // ---- annotations, check-ins
    private fun annotation(a: DayAnnotation) = buildJsonObject {
        put("date", a.date.toString())
        put("freeDay", a.freeDay)
        put("alarmSet", a.alarmSet)
        put("alarmDismissedAt", a.alarmDismissedAt?.toString())
    }

    private fun readAnnotation(o: JsonObject) = DayAnnotation(
        LocalDate.parse(o.str("date")),
        o["freeDay"]?.jsonPrimitive?.booleanOrNull,
        o["alarmSet"]?.jsonPrimitive?.booleanOrNull,
        o.strOrNull("alarmDismissedAt")?.let(Instant::parse),
    )

    private fun checkIn(c: CheckIn) = when (c) {
        is CheckIn.EnergyRating -> buildJsonObject { put("type", "ENERGY_RATING"); put("at", c.at.toString()); put("rating", c.rating) }
        is CheckIn.ReactionTest -> buildJsonObject {
            put("type", "REACTION_TEST"); put("at", c.at.toString())
            put("medianRtMillis", c.medianRtMillis); put("lapses", c.lapses); put("trials", c.trials)
        }
        is CheckIn.SleepRating -> buildJsonObject {
            put("type", "SLEEP_RATING"); put("at", c.at.toString()); put("night", c.night.toString()); put("rating", c.rating)
        }
    }

    private fun readCheckIn(o: JsonObject): CheckIn = when (val t = o.str("type")) {
        "ENERGY_RATING" -> CheckIn.EnergyRating(Instant.parse(o.str("at")), o["rating"]!!.jsonPrimitive.int)
        "REACTION_TEST" -> CheckIn.ReactionTest(
            Instant.parse(o.str("at")), o["medianRtMillis"]!!.jsonPrimitive.double, o["lapses"]!!.jsonPrimitive.int, o["trials"]!!.jsonPrimitive.int,
        )
        "SLEEP_RATING" -> CheckIn.SleepRating(Instant.parse(o.str("at")), LocalDate.parse(o.str("night")), o["rating"]!!.jsonPrimitive.int)
        else -> error("Unknown check-in type $t")
    }

    // ---- signals
    private fun signal(s: Signal): JsonObject = buildJsonObject {
        put("type", s::class.simpleName)
        put("origin", origin(s.origin))
        when (s) {
            is Signal.HeartRateSample -> { put("at", s.at.toString()); put("bpm", s.bpm) }
            is Signal.RestingHeartRate -> { put("at", s.at.toString()); put("bpm", s.bpm) }
            is Signal.HrvSample -> { put("at", s.at.toString()); put("rmssdMillis", s.rmssdMillis) }
            is Signal.StepBucket -> { put("interval", interval(s.interval)); put("count", s.count) }
            is Signal.ActivityBucket -> { put("interval", interval(s.interval)); put("activity", s.type.name) }
            is Signal.ScreenEvent -> { put("at", s.at.toString()); put("interactive", s.interactive) }
            is Signal.MotionBucket -> { put("interval", interval(s.interval)); put("motionLevel", s.motionLevel); put("sleepConfidence", s.sleepConfidence) }
            is Signal.LightBucket -> { put("interval", interval(s.interval)); put("meanLux", s.meanLux) }
            is Signal.ChargingEvent -> { put("at", s.at.toString()); put("plugged", s.plugged) }
            is Signal.AlarmEvent -> { put("at", s.at.toString()); put("kind", s.kind.name) }
        }
    }

    private fun readSignal(o: JsonObject): Signal {
        val origin = readOrigin(o["origin"]!!.jsonObject)
        fun at() = Instant.parse(o.str("at"))
        fun iv() = readInterval(o["interval"]!!.jsonObject)
        fun num(k: String) = o[k]!!.jsonPrimitive.double
        return when (val t = o.str("type")) {
            "HeartRateSample" -> Signal.HeartRateSample(origin, at(), num("bpm"))
            "RestingHeartRate" -> Signal.RestingHeartRate(origin, at(), num("bpm"))
            "HrvSample" -> Signal.HrvSample(origin, at(), num("rmssdMillis"))
            "StepBucket" -> Signal.StepBucket(origin, iv(), o["count"]!!.jsonPrimitive.long)
            "ActivityBucket" -> Signal.ActivityBucket(origin, iv(), ActivityType.valueOf(o.str("activity")))
            "ScreenEvent" -> Signal.ScreenEvent(origin, at(), o["interactive"]!!.jsonPrimitive.booleanOrNull!!)
            "MotionBucket" -> Signal.MotionBucket(origin, iv(), num("motionLevel"), o["sleepConfidence"]?.jsonPrimitive?.doubleOrNull)
            "LightBucket" -> Signal.LightBucket(origin, iv(), num("meanLux"))
            "ChargingEvent" -> Signal.ChargingEvent(origin, at(), o["plugged"]!!.jsonPrimitive.booleanOrNull!!)
            "AlarmEvent" -> Signal.AlarmEvent(origin, at(), AlarmKind.valueOf(o.str("kind")))
            else -> error("Unknown signal type $t")
        }
    }

    // ---- truth (write-only)
    private fun truth(t: GroundTruth) = buildJsonObject {
        put("sleepNeedMinutes", t.sleepNeed.toMinutes())
        put("dlmoMinutesByDate", buildJsonObject { t.dlmoMinutesByDate.toSortedMap().forEach { (d, m) -> put(d.toString(), m) } })
        put("missingDates", JsonArray(t.missingDates.sorted().map { JsonPrimitive(it.toString()) }))
        put("facts", buildJsonObject { t.facts.forEach { (k, v) -> put(k, v) } })
        put("nights", JsonArray(t.nights.map { n ->
            buildJsonObject {
                put("date", n.date.toString()); put("zone", n.zone.id)
                put("start", n.interval.start.toString()); put("end", n.interval.end.toString())
                put("awakenings", n.awakenings)
            }
        }))
    }

    private fun JsonObject.str(k: String): String = this[k]!!.jsonPrimitive.content
    private fun JsonObject.strOrNull(k: String): String? = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
}
