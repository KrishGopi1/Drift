package app.drift.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PerformanceTest {
    @Test fun `90 days of data analyze in under 50 ms after warm-up`() {
        val inputs = listOf(Synth.steady(days = 90).input(), Synth.fragmentedParent(days = 90).input(), Synth.rotatingNightShift(days = 90).input())
        val engine = DefaultSleepEngine()
        repeat(40) { inputs.forEach { engine.analyze(it) } }
        for (input in inputs) {
            val times = (0 until 25).map {
                val t0 = System.nanoTime()
                engine.analyze(input)
                (System.nanoTime() - t0) / 1e6
            }.sorted()
            val median = times[times.size / 2]
            println("median ${"%.2f".format(median)} ms, worst ${"%.2f".format(times.last())} ms for ${input.episodes.size} episodes")
            assertThat(median).isLessThan(50.0)
        }
    }
}
