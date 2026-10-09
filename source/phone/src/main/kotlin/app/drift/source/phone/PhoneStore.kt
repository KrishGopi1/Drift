package app.drift.source.phone

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/**
 * Small module-private store for what the OS only tells us live (Sleep API events, charging changes,
 * step counter readings, the next alarm). SharedPreferences holding JSON arrays, pruned to [RETENTION].
 */
internal class PhoneStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized fun segments(): List<SleepSegment> = read(KEY_SEGMENTS) { SleepSegment(Instant.ofEpochMilli(it.getLong("s")), Instant.ofEpochMilli(it.getLong("e")), it.getInt("st")) }
    @Synchronized fun classify(): List<ClassifySample> = read(KEY_CLASSIFY) { ClassifySample(Instant.ofEpochMilli(it.getLong("t")), it.getInt("c"), it.getInt("m"), it.getInt("l")) }
    @Synchronized fun charging(): List<ChargeChange> = read(KEY_CHARGE) { ChargeChange(Instant.ofEpochMilli(it.getLong("t")), it.getBoolean("p")) }
    @Synchronized fun steps(): List<StepSample> = read(KEY_STEPS) { StepSample(Instant.ofEpochMilli(it.getLong("t")), it.getLong("n"), it.getLong("b")) }
    @Synchronized fun alarms(): List<AlarmSample> = read(KEY_ALARMS) { AlarmSample(Instant.ofEpochMilli(it.getLong("s")), Instant.ofEpochMilli(it.getLong("a"))) }

    @Synchronized fun addSegments(items: List<SleepSegment>, now: Instant) {
        val merged = (segments() + items).distinctBy { it.start to it.end }
        write(KEY_SEGMENTS, merged.filter { it.end > now - RETENTION }) { JSONObject().put("s", it.start.toEpochMilli()).put("e", it.end.toEpochMilli()).put("st", it.status) }
        markApiDelivery(now)
    }

    @Synchronized fun addClassify(items: List<ClassifySample>, now: Instant) {
        val merged = (classify() + items).distinctBy { it.at }
        write(KEY_CLASSIFY, merged.filter { it.at > now - CLASSIFY_RETENTION }) { JSONObject().put("t", it.at.toEpochMilli()).put("c", it.confidence).put("m", it.motion).put("l", it.light) }
        markApiDelivery(now)
    }

    /** Records a plug/unplug; repeated identical states are dropped so a sampled state never duplicates a broadcast. */
    @Synchronized fun addCharging(at: Instant, plugged: Boolean, now: Instant) {
        val all = charging().sortedBy { it.at }
        if (all.lastOrNull()?.plugged == plugged) return
        write(KEY_CHARGE, (all + ChargeChange(at, plugged)).filter { it.at > now - RETENTION }) { JSONObject().put("t", it.at.toEpochMilli()).put("p", it.plugged) }
    }

    @Synchronized fun addStepSample(sample: StepSample, now: Instant) {
        write(KEY_STEPS, (steps() + sample).distinctBy { it.at }.filter { it.at > now - RETENTION }) { JSONObject().put("t", it.at.toEpochMilli()).put("n", it.total).put("b", it.bootKey) }
    }

    @Synchronized fun addAlarm(sample: AlarmSample, now: Instant) {
        val kept = alarms().filter { it.triggerAt != sample.triggerAt && it.triggerAt > now - RETENTION }
        write(KEY_ALARMS, kept + sample) { JSONObject().put("s", it.sampledAt.toEpochMilli()).put("a", it.triggerAt.toEpochMilli()) }
    }

    /** True once the Sleep API has delivered anything, i.e. its silence on a night is meaningful. */
    fun sleepApiDelivering(now: Instant): Boolean =
        prefs.getLong(KEY_API_LAST, 0L).let { it > 0 && Instant.ofEpochMilli(it) > now - RETENTION }

    private fun markApiDelivery(now: Instant) = prefs.edit().putLong(KEY_API_LAST, now.toEpochMilli()).apply()

    @Synchronized fun clear() = prefs.edit().clear().apply()

    private fun <T> read(key: String, parse: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { parse(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun <T> write(key: String, items: List<T>, encode: (T) -> JSONObject) {
        val arr = JSONArray()
        items.forEach { arr.put(encode(it)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        private const val FILE = "drift_phone_source"
        private const val KEY_SEGMENTS = "segments"
        private const val KEY_CLASSIFY = "classify"
        private const val KEY_CHARGE = "charging"
        private const val KEY_STEPS = "steps"
        private const val KEY_ALARMS = "alarms"
        private const val KEY_API_LAST = "api_last_delivery"
        val RETENTION: Duration = Duration.ofDays(30)
        val CLASSIFY_RETENTION: Duration = Duration.ofDays(10)
    }
}
