package app.drift.background.widget

import app.drift.background.Fixtures
import app.drift.background.Fixtures.at
import app.drift.model.engine.EnergyPhase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class WidgetStateMapperTest {
    private val zone = Fixtures.zone

    @Test fun `no snapshot is the empty state`() {
        assertThat(WidgetStateMapper.map(null, Fixtures.noon, zone)).isEqualTo(WidgetState.Empty)
    }

    @Test fun `a snapshot without a curve for today is empty`() {
        val snap = Fixtures.snapshot(days = emptyList())
        assertThat(WidgetStateMapper.map(snap, Fixtures.noon, zone)).isEqualTo(WidgetState.Empty)
    }

    @Test fun `recent snapshot is fresh with the current and next phase`() {
        val now = at(14, 30)
        val state = WidgetStateMapper.map(Fixtures.snapshot(computedAt = at(13)), now, zone)
        assertThat(state).isInstanceOf(WidgetState.Fresh::class.java)
        val m = (state as WidgetState.Fresh).model
        assertThat(m.currentPhase).isEqualTo(EnergyPhase.AFTERNOON_DIP)
        assertThat(m.nextPhase).isEqualTo(EnergyPhase.EVENING_PEAK)
        assertThat(m.nextPhaseAt).isEqualTo(at(16))
        assertThat(m.nowFraction).isWithin(0.01f).of(14.5f / 24f)
        assertThat(m.points).hasSize(288)
    }

    @Test fun `snapshot older than 12 hours is stale but keeps the curve`() {
        val now = at(14, 30)
        val state = WidgetStateMapper.map(Fixtures.snapshot(computedAt = now.minus(Duration.ofHours(12)).minusSeconds(1)), now, zone)
        assertThat(state).isInstanceOf(WidgetState.Stale::class.java)
        assertThat((state as WidgetState.Stale).model.points).isNotEmpty()
        assertThat(state.age).isGreaterThan(Duration.ofHours(12))
    }

    @Test fun `exactly 12 hours old is still fresh`() {
        val now = at(14, 30)
        val state = WidgetStateMapper.map(Fixtures.snapshot(computedAt = now.minus(Duration.ofHours(12))), now, zone)
        assertThat(state).isInstanceOf(WidgetState.Fresh::class.java)
    }

    @Test fun `last phase of the day has no next phase`() {
        val state = WidgetStateMapper.map(Fixtures.snapshot(computedAt = at(23)), at(23, 30), zone) as WidgetState.Fresh
        assertThat(state.model.currentPhase).isEqualTo(EnergyPhase.MELATONIN_WINDOW)
        assertThat(state.model.nextPhase).isNull()
    }
}
