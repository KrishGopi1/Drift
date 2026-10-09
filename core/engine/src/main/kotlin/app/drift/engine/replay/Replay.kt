package app.drift.engine.replay

import app.drift.model.engine.EngineInput
import app.drift.model.engine.EngineState
import app.drift.model.engine.SignalWindow
import app.drift.model.engine.SleepEngine
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One replayed day: the headline numbers the engine produced when it ran at local noon on [date]. */
data class ReplayRow(
    val date: LocalDate,
    val needMinutes: Long,
    val needLowerMinutes: Long,
    val needUpperMinutes: Long,
    val debtMinutes: Long?,
    val sri7: Double?,
    val dlmoClock: String?,
    val melatoninWindow: String?,
    /** Need change versus seven days earlier, in minutes; null for the first week. */
    val needChangeWeekMinutes: Long?,
    val needPersonal: Boolean,
    /** True when the number switched from the population prior to the personal estimate this week. */
    val personalizedThisWeek: Boolean,
) {
    /** Plan 9.2: need should move by at most 15 min per week. */
    val needJump: Boolean get() = needChangeWeekMinutes != null && !personalizedThisWeek && Math.abs(needChangeWeekMinutes) > 15
}

object Replay {
    /** Replays [data] day by day, feeding each run's state into the next. */
    fun run(data: ReplayData, engine: SleepEngine, nowHour: Int = 12): List<ReplayRow> {
        if (data.episodes.isEmpty()) return emptyList()
        val zone = data.zone
        val firstDay = data.episodes.minOf { it.end.atZone(it.zone).toLocalDate() }
        val lastDay = data.episodes.maxOf { it.end.atZone(it.zone).toLocalDate() }
        val rows = ArrayList<ReplayRow>()
        var state: EngineState? = null
        var d = firstDay
        val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
        while (!d.isAfter(lastDay)) {
            val now = d.atTime(LocalTime.of(nowHour, 0)).atZone(zone).toInstant()
            val known = data.episodes.filter { !it.end.isAfter(now) }
            val input = EngineInput(
                now = now,
                zone = zone,
                episodes = known,
                signals = SignalWindow.empty(now),
                profile = data.profile,
                priorState = state,
                annotations = data.annotations.filter { !it.date.isAfter(d) },
                checkIns = data.checkIns.filter { !it.at.isAfter(now) },
            )
            val snap = engine.analyze(input)
            state = snap.state
            val need = snap.sleepNeed.value.toMinutes()
            val personal = snap.dataSufficiency.nightsUntilPersonalNeed == 0
            val prev = rows.getOrNull(rows.size - 7)
            val dlmo = snap.circadian.dlmoMinutes?.value?.let {
                val m = Math.floorMod(Math.round(it).toInt(), 1440)
                "%02d:%02d".format(m / 60, m % 60)
            }
            val win = snap.circadian.melatoninWindow?.let {
                it.start.atZone(zone).format(clock) + "-" + it.end.atZone(zone).format(clock)
            }
            rows += ReplayRow(
                date = d,
                needMinutes = need,
                needLowerMinutes = snap.sleepNeed.confidence?.lower?.toMinutes() ?: need,
                needUpperMinutes = snap.sleepNeed.confidence?.upper?.toMinutes() ?: need,
                debtMinutes = snap.sleepDebt?.value?.toMinutes(),
                sri7 = snap.regularity.sri7?.value,
                dlmoClock = dlmo,
                melatoninWindow = win,
                needChangeWeekMinutes = prev?.let { need - it.needMinutes },
                needPersonal = personal,
                personalizedThisWeek = personal && prev != null && !prev.needPersonal,
            )
            d = d.plusDays(1)
        }
        return rows
    }

    private fun hm(minutes: Long): String = "%dh%02d".format(minutes / 60, minutes % 60)

    fun format(rows: List<ReplayRow>): String {
        val sb = StringBuilder()
        sb.append("date        need (90% band)         debt    SRI7  DLMO   melatonin window  need change\n")
        for (r in rows) {
            val need = "${hm(r.needMinutes)} (${hm(r.needLowerMinutes)}-${hm(r.needUpperMinutes)})"
            val debt = r.debtMinutes?.let { hm(it) } ?: "n/a"
            val sri = r.sri7?.let { "%.0f".format(it) } ?: "n/a"
            val change = r.needChangeWeekMinutes?.let { "%+d min/wk".format(it) } ?: ""
            val flag = if (r.needJump) "  << exceeds 15 min/wk" else if (r.personalizedThisWeek) "  (switched from population prior)" else ""
            sb.append(
                "%-11s %-23s %-7s %-5s %-6s %-17s %s%s\n".format(
                    r.date, need, debt, sri, r.dlmoClock ?: "n/a", r.melatoninWindow ?: "n/a", change, flag,
                ),
            )
        }
        val jumps = rows.count { it.needJump }
        sb.append("\n${rows.size} days replayed; need moved more than 15 min/week on $jumps of them.\n")
        return sb.toString()
    }
}
