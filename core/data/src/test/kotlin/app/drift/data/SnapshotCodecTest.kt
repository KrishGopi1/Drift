package app.drift.data

import app.drift.data.codec.SnapshotCodec
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SnapshotCodecTest {
    @Test fun roundTripsExactly() {
        val snap = Fixtures.snapshot()
        val back = SnapshotCodec.decode(SnapshotCodec.encode(snap))
        assertThat(back).isEqualTo(snap)
    }

    @Test fun roundTripsNullsAndEmpties() {
        val snap = Fixtures.snapshot().let {
            it.copy(
                sleepDebt = null, debtBand = null, debtTrend = emptyList(),
                circadian = it.circadian.copy(dlmoMinutes = null, melatoninWindow = null, windDownStart = null, reentrainment = null),
                recommendations = it.recommendations.copy(tradeOff = null, nap = null, habits = emptyList()),
                explanations = emptyList(),
            )
        }
        assertThat(SnapshotCodec.decode(SnapshotCodec.encode(snap))).isEqualTo(snap)
    }

    @Test fun nonFiniteDoublesSurvive() {
        val snap = Fixtures.snapshot().let { it.copy(state = it.state.copy(values = mapOf("a" to Double.POSITIVE_INFINITY, "b" to -0.0))) }
        assertThat(SnapshotCodec.decode(SnapshotCodec.encode(snap))).isEqualTo(snap)
    }
}
