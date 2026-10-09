package app.drift.engine.models

import app.drift.engine.ParameterSet
import app.drift.engine.internal.Circ
import app.drift.engine.internal.DAY_MIN_L
import app.drift.engine.internal.Preprocessed
import app.drift.engine.internal.Stats
import app.drift.engine.internal.clamp
import app.drift.engine.internal.localToInstant
import app.drift.engine.internal.offsetMin
import app.drift.model.Confidence
import app.drift.model.ConfidenceLevel
import app.drift.model.Interval
import app.drift.model.Provenance
import app.drift.model.Quantity
import app.drift.model.engine.CircadianEstimate
import app.drift.model.engine.DebtBand
import app.drift.model.engine.EnergyCurve
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.EngineInput
import app.drift.model.engine.HabitCard
import app.drift.model.engine.HabitKind
import app.drift.model.engine.NapAdvice
import app.drift.model.engine.Recommendations
import app.drift.model.engine.TradeOff
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tonight's recommended sleep, computed before the energy model so that tomorrow's curve can assume it. */
internal class SleepPlan(
    val sleepOpportunity: Quantity<Duration>,
    val bedtime: Quantity<Instant>,
    val wakeTime: Quantity<Instant>,
    val tradeOff: TradeOff?,
    val sleepEfficiency: Double,
    val tibMinutes: Double,
    val wakeDay: Long,
) {
    val bed: Instant get() = bedtime.value
    val wake: Instant get() = wakeTime.value
}

/** Model 4.7: sleep opportunity, bed and wake times, melatonin trade-off, habit cards and nap advice. */
internal class RecommendationModel(private val p: ParameterSet) {

    fun sleepEfficiency(pre: Preprocessed): Double {
        val samples = pre.usable.takeLast(28).mapNotNull { it.main!!.sleepEfficiency }
        if (samples.size < p.seMinNights.i) return p.sleepEfficiencyDefault.v
        return Stats.median(samples)!!.clamp(0.6, 0.98)
    }

    fun plan(
        pre: Preprocessed,
        input: EngineInput,
        need: NeedResult,
        debtMinutes: Double?,
        circadian: CircadianEstimate,
        habitual: Habitual,
        workdayMedianWake: Double?,
        freeWakes: List<Double>,
    ): SleepPlan {
        val zone = input.zone
        val se = sleepEfficiency(pre)
        val target = input.profile.goals.targetSleep?.let { it.seconds / 60.0 }
        val n = target ?: need.displayed
        val payback = minOf(p.debtPaybackFraction.v * (debtMinutes ?: 0.0), p.debtPaybackCap.v)
        val tib = Math.round((n + payback) / se).toDouble()
        val tibLo = Math.round(((target ?: need.lower) + payback) / se).toDouble()
        val tibHi = Math.round(((target ?: need.upper) + payback) / se).toDouble()

        val nowHour = Math.floorMod(pre.nowLocal, DAY_MIN_L) / 60.0
        var wakeDay = if (pre.main(pre.todayDay) != null || nowHour >= 12.0) pre.todayDay + 1 else pre.todayDay
        val sched = input.profile.schedule
        var wake: Instant
        var wakeProv: Provenance
        var wakeSources = if (pre.usable.isNotEmpty()) pre.sources else emptySet()
        var tries = 0
        while (true) {
            val dow = java.time.LocalDate.ofEpochDay(wakeDay).dayOfWeek
            val isWork = dow in sched.workdays && !pre.shiftWorkMode
            val clock: Double
            if (isWork && sched.wakeTime != null) {
                clock = sched.wakeTime!!.hour * 60.0 + sched.wakeTime!!.minute
                wakeProv = Provenance.USER_ENTERED
            } else if (isWork && workdayMedianWake != null) {
                clock = workdayMedianWake
                wakeProv = Provenance.DERIVED
            } else if (!isWork && !pre.shiftWorkMode && freeWakes.size >= 3) {
                clock = Stats.median(freeWakes)!!
                wakeProv = Provenance.DERIVED
            } else if (!isWork && !pre.shiftWorkMode && sched.freeDayWakeTime != null) {
                clock = sched.freeDayWakeTime!!.hour * 60.0 + sched.freeDayWakeTime!!.minute
                wakeProv = Provenance.USER_ENTERED
            } else if (habitual.fromData) {
                clock = habitual.wake
                wakeProv = Provenance.DERIVED
            } else {
                clock = habitual.wake
                wakeProv = Provenance.ESTIMATED
            }
            wake = localToInstant(zone, wakeDay * DAY_MIN_L + clock.toLong())
            if (wake.isAfter(input.now.plus(Duration.ofHours(1))) || tries >= 3) break
            wakeDay++
            tries++
        }
        if (wakeProv == Provenance.DERIVED && wakeSources.isEmpty()) wakeProv = Provenance.ESTIMATED

        val bed = wake.minus(Duration.ofSeconds((tib * 60).toLong()))
        val bedLo = wake.minus(Duration.ofSeconds((tibHi * 60).toLong()))
        val bedHi = wake.minus(Duration.ofSeconds((tibLo * 60).toLong()))
        val level = need.level
        val opp = Quantity(
            minutesToDuration(tib), Provenance.ESTIMATED,
            Confidence(minutesToDuration(tibLo), minutesToDuration(maxOf(tibHi, tibLo)), 0.9, level),
            need.sources,
        )
        val bedQ = Quantity(bed, Provenance.ESTIMATED, Confidence(minOf(bedLo, bed), maxOf(bedHi, bed), 0.9, level), need.sources)
        val wakeQ: Quantity<Instant> = when (wakeProv) {
            Provenance.ESTIMATED -> Quantity(wake, Provenance.ESTIMATED, Confidence(wake.minusSeconds(3600), wake.plusSeconds(3600), 0.9, ConfidenceLevel.LOW), wakeSources)
            else -> Quantity(wake, wakeProv, null, wakeSources)
        }

        var trade: TradeOff? = null
        val win = circadian.melatoninWindow
        if (win != null && !pre.shiftWorkMode) {
            val diff = Duration.between(win.start, bed)
            if (diff.abs() < Duration.ofHours(12) && diff.toMinutes() < -p.bedConflictTolerance.v) {
                val following = Duration.between(win.start, wake).toMinutes()
                trade = TradeOff(
                    message = "Your sleep need points to a ${clockText(bed, zone)} bedtime, but your melatonin window starts at " +
                        "${clockText(win.start, zone)}. Going to bed that early may mean lying awake. Going to bed at " +
                        "${clockText(win.start, zone)} would still leave ${durText(following)} before your ${clockText(wake, zone)} wake-up.",
                    bedtimeIfFollowingWindow = win.start,
                )
            }
        }
        return SleepPlan(opp, bedQ, wakeQ, trade, se, tib, wakeDay)
    }

    fun complete(
        plan: SleepPlan,
        pre: Preprocessed,
        input: EngineInput,
        circadian: CircadianEstimate,
        energy: EnergyCurve,
        debtMinutes: Double?,
        debtBand: DebtBand?,
    ): Recommendations {
        val zone = input.zone
        val bed = plan.bed
        val wake = plan.wake
        val habits = ArrayList<HabitCard>()
        fun hrs(h: Double) = Duration.ofSeconds((h * 3600).toLong())
        habits += HabitCard(HabitKind.CAFFEINE_CUTOFF, Interval(bed.minus(hrs(p.caffeineHours.v)), bed), p.caffeineHours.key)
        habits += HabitCard(HabitKind.LAST_MEAL, Interval(bed.minus(hrs(p.mealHours.v)), bed), p.mealHours.key)
        habits += HabitCard(HabitKind.ALCOHOL_CUTOFF, Interval(bed.minus(hrs(p.alcoholHours.v)), bed), p.alcoholHours.key)

        // Morning light: this morning's wake if it was within the last hour of the window, else the next wake.
        val todayWake = pre.main(pre.todayDay)?.let { Instant.ofEpochSecond(it.endAbs * 60) }
        val lightStart = if (todayWake != null && todayWake.plusSeconds((p.lightWindowMin.v * 60).toLong()).isAfter(input.now)) todayWake else wake
        habits += HabitCard(
            HabitKind.MORNING_LIGHT,
            Interval(lightStart, lightStart.plusSeconds((p.lightWindowMin.v * 60).toLong())),
            p.lightWindowMin.key,
        )

        val windDown = circadian.windDownStart
        if (windDown != null) {
            val exAfter = (todayWake ?: input.now).plusSeconds((p.exerciseAfterWakeMin.v * 60).toLong())
            val exStart = maxOf(exAfter, input.now)
            if (windDown.isAfter(exStart)) {
                habits += HabitCard(HabitKind.EXERCISE_WINDOW, Interval(exStart, windDown), p.exerciseAfterWakeMin.key)
            }
            val dimEnd = if (bed.isAfter(windDown)) bed else circadian.melatoninWindow?.end ?: windDown
            habits += HabitCard(HabitKind.DIM_LIGHTS, Interval(windDown, if (dimEnd.isBefore(windDown)) windDown else dimEnd), p.dimLightsFromWindDown.key)
        }

        var nap: NapAdvice? = null
        val today = java.time.LocalDate.ofEpochDay(pre.todayDay)
        val dip = energy.forDate(today)?.segments?.firstOrNull { it.phase == EnergyPhase.AFTERNOON_DIP }
        if (debtMinutes != null && debtMinutes >= p.napMinDebtMin.v && debtBand != DebtBand.LOW && dip != null) {
            val latestEnd = bed.minus(Duration.ofMinutes(p.napEndBeforeBedMin.i.toLong()))
            val start = maxOf(dip.interval.start, input.now)
            val end = minOf(dip.interval.end, latestEnd)
            if (Duration.between(start, end).toMinutes() >= p.napMinMin.v) {
                nap = NapAdvice(
                    Interval(start, end),
                    Duration.ofMinutes(p.napMaxMin.i.toLong()),
                    "Your debt is ${durText(debtMinutes.toLong())} and your afternoon dip is around ${clockText(dip.extremumAt ?: dip.interval.start, zone)}. " +
                        "A ${p.napMinMin.i} to ${p.napMaxMin.i} minute nap that ends by ${clockText(end, zone)} will not cut into tonight's sleep.",
                )
            }
        }
        return Recommendations(plan.sleepOpportunity, plan.bedtime, plan.wakeTime, plan.tradeOff, habits, nap)
    }

    companion object {
        private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

        fun clockText(i: Instant, zone: ZoneId): String = ZonedDateTime.ofInstant(i, zone).format(CLOCK)

        fun durText(minutes: Long): String {
            val m = Math.abs(minutes)
            val h = m / 60
            val r = m % 60
            return when {
                h == 0L -> "$r min"
                r == 0L -> "$h h"
                else -> "$h h $r min"
            }
        }
    }
}
