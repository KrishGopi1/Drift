package app.drift.engine.internal

import app.drift.model.EpisodeKind
import app.drift.model.QualityTier
import app.drift.model.SourceId
import java.time.LocalDate
import java.time.ZoneId

/** Half-open span in minutes; absolute (epoch) or local (minutes since 1970-01-01T00:00 local) by context. */
internal class Span(val start: Long, val end: Long) {
    val length: Long get() = end - start
}

/** One merged episode after fragment merging; all models read this, never raw [app.drift.model.SleepEpisode]. */
internal class ProcEpisode(
    val ids: List<String>,
    val startAbs: Long,
    val endAbs: Long,
    val zone: ZoneId,
    val localStart: Long,
    val localEnd: Long,
    val asleepLocal: List<Span>,
    val tst: Double,
    val weight: Double,
    val userConfirmed: Boolean,
    val userEdited: Boolean,
    val sources: Set<SourceId>,
    val forcedKind: EpisodeKind?,
    val bestTier: QualityTier,
    val sleepEfficiency: Double?,
    val allRecorded: Boolean,
) {
    val sleepDay: Long get() = Math.floorDiv(localEnd, DAY_MIN_L)
    val spanMin: Long get() = endAbs - startAbs
    val onsetClock: Double get() = Circ.mod(localStart.toDouble())
    val wakeClock: Double get() = Circ.mod(localEnd.toDouble())
    val midClock: Double get() = Circ.mod((localStart + localEnd) / 2.0)
    val wakeMinuteOfDay: Int get() = Math.floorMod(localEnd, DAY_MIN_L).toInt()
}

internal class NightRecord(
    val day: Long,
    val main: ProcEpisode?,
    val naps: List<ProcEpisode>,
    val excludedMain: ProcEpisode?,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(day)
    val napMinutes: Double get() = naps.sumOf { it.tst }

    /** Nap credit in minutes: full up to [fullLimit], then at [overRate]. */
    fun napCredit(fullLimit: Double, overRate: Double): Double {
        val n = napMinutes
        return minOf(n, fullLimit) + overRate * maxOf(0.0, n - fullLimit)
    }
}

internal class Preprocessed(
    val nights: List<NightRecord>,
    val byDay: Map<Long, NightRecord>,
    val todayDay: Long,
    val lastDay: Long,
    val nowLocal: Long,
    val nowAbs: Long,
    val shiftWorkMode: Boolean,
    val shiftNightDays: Set<Long>,
    val alarmAbs: List<Long>,
    val excludedCount: Int,
) {
    /** Nights with a usable main sleep, oldest first. */
    val usable: List<NightRecord> = nights.filter { it.main != null }
    val sources: Set<SourceId> = usable.flatMap { n -> (listOfNotNull(n.main) + n.naps).flatMap { it.sources } }.toSet()
    fun main(day: Long): ProcEpisode? = byDay[day]?.main
    val nowMinuteOfDay: Int get() = Math.floorMod(nowLocal, DAY_MIN_L).toInt()
}

/** UTC offset of [zone] at the given absolute minute, in minutes. */
internal fun offsetMin(zone: ZoneId, absMin: Long): Long =
    zone.rules.getOffset(java.time.Instant.ofEpochSecond(absMin * 60)).totalSeconds / 60L

/** Converts local minutes since 1970-01-01T00:00 local into an instant in [zone] (gaps resolve forward, overlaps to the earlier offset). */
internal fun localToInstant(zone: ZoneId, localMin: Long): java.time.Instant =
    java.time.LocalDateTime.ofEpochSecond(localMin * 60, 0, java.time.ZoneOffset.UTC).atZone(zone).toInstant()
