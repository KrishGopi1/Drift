package app.drift.source.phone.detect

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class TwoStateHmmTest {
    private val t0 = Instant.parse("2026-03-01T18:00:00Z")

    private fun bin(i: Int, screen: Double? = 0.0, api: Double? = null, charging: Boolean? = null, steps: Double? = null): Bin {
        val start = t0.plus(BIN.multipliedBy(i.toLong()))
        val hour = (18.0 + i / 6.0) % 24.0
        return Bin(start, hour, screen, api, charging, steps)
    }

    @Test fun `empty input decodes to nothing`() {
        val r = TwoStateHmm().decode(emptyList())
        assertThat(r.path).isEmpty()
        assertThat(r.posterior).isEmpty()
    }

    @Test fun `screen use pulls the path awake and quiet night pulls it asleep`() {
        val bins = (0 until 144).map { i ->
            val night = i in 30..110 // 23:00 .. 12:20 local-ish
            bin(i, screen = if (night) 0.0 else 4.0)
        }
        val r = TwoStateHmm().decode(bins)
        assertThat(r.path[70]).isEqualTo(Hidden.ASLEEP)
        assertThat(r.path[5]).isEqualTo(Hidden.AWAKE)
        assertThat(r.path[130]).isEqualTo(Hidden.AWAKE)
        assertThat(r.posterior[70]).isGreaterThan(0.9)
        assertThat(r.posterior[5]).isLessThan(0.1)
    }

    @Test fun `a single brief phone check does not split the night`() {
        val bins = (0 until 144).map { i -> bin(i, screen = if (i == 60) 2.0 else if (i in 30..100) 0.0 else 5.0) }
        val r = TwoStateHmm().decode(bins)
        assertThat(r.path.subList(40, 95).all { it == Hidden.ASLEEP }).isTrue()
    }

    @Test fun `sleep api confidence outweighs a quiet daytime screen`() {
        val quietNoApi = (0 until 20).map { i -> Bin(t0, 14.0, 0.0, null, null, null) }
        val quietApi = (0 until 20).map { i -> Bin(t0, 14.0, 0.0, 0.95, null, null) }
        val hmm = TwoStateHmm()
        assertThat(hmm.logRatio(quietNoApi[0])).isLessThan(0.0)
        assertThat(hmm.logRatio(quietApi[0])).isGreaterThan(hmm.logRatio(quietNoApi[0]))
    }

    @Test fun `steps argue against sleep`() {
        val still = Bin(t0, 3.0, 0.0, null, null, 0.0)
        val walking = Bin(t0, 3.0, 0.0, null, null, 80.0)
        assertThat(TwoStateHmm().logRatio(walking)).isLessThan(TwoStateHmm().logRatio(still))
    }

    @Test fun `charging supports sleep but being unplugged is neutral`() {
        val h = TwoStateHmm()
        val base = Bin(t0, 3.0, null, null, null, null)
        assertThat(h.logRatio(base.copy(charging = true))).isGreaterThan(h.logRatio(base))
        assertThat(h.logRatio(base.copy(charging = false))).isEqualTo(h.logRatio(base))
    }

    @Test fun `time of day prior peaks at night and is negative in the afternoon`() {
        val h = TwoStateHmm()
        assertThat(h.logRatio(Bin(t0, 3.5, null, null, null, null))).isGreaterThan(1.0)
        assertThat(h.logRatio(Bin(t0, 15.5, null, null, null, null))).isLessThan(-1.0)
    }

    @Test fun `posteriors are probabilities`() {
        val bins = (0 until 60).map { bin(it, screen = if (it % 7 == 0) 3.0 else 0.0) }
        TwoStateHmm().decode(bins).posterior.forEach { assertThat(it).isIn(com.google.common.collect.Range.closed(0.0, 1.0)) }
    }
}
