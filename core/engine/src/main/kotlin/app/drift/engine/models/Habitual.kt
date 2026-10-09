package app.drift.engine.models

import app.drift.engine.ParameterSet
import app.drift.engine.internal.Circ
import app.drift.engine.internal.Preprocessed
import app.drift.model.engine.EngineInput

/** Habitual onset and wake as clock minutes (0..1440) from recent data or, failing that, the profile. */
internal class Habitual(val onset: Double, val wake: Double, val fromData: Boolean) {
    val durationMin: Double get() = Circ.mod(wake - onset)

    companion object {
        fun of(pre: Preprocessed, input: EngineInput, p: ParameterSet, needMin: Double, se: Double): Habitual {
            val recent = pre.usable.filter { it.day > pre.lastDay - 28 }
            if (recent.size >= 3) {
                val onset = Circ.mean(recent.map { it.main!!.onsetClock })!!
                val wake = Circ.mean(recent.map { it.main!!.wakeClock })!!
                return Habitual(onset, wake, true)
            }
            val sched = input.profile.schedule
            val tib = needMin / se
            sched.wakeTime?.let {
                val wake = it.hour * 60.0 + it.minute
                return Habitual(Circ.mod(wake - tib), wake, false)
            }
            if (sched.freeDayBedtime != null && sched.freeDayWakeTime != null) {
                return Habitual(
                    sched.freeDayBedtime!!.let { it.hour * 60.0 + it.minute },
                    sched.freeDayWakeTime!!.let { it.hour * 60.0 + it.minute },
                    false,
                )
            }
            val mid = p.chronoPrior(input.profile.chronotype)
            return Habitual(Circ.mod(mid - tib / 2), Circ.mod(mid + tib / 2), false)
        }
    }
}
