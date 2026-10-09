package app.drift.testing.fixtures

import app.drift.model.ui.ScreenState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ScreenStateFixturesTest {
    @Test fun `all returns the six variants in plan order`() {
        val all = ScreenStateFixtures.all(SnapshotFixtures.full())
        assertThat(all.keys.toList()).containsExactlyElementsIn(ScreenStateFixtures.NAMES).inOrder()
        val kinds = all.values.map { it::class }
        assertThat(kinds).containsExactly(
            ScreenState.Loading::class, ScreenState.FirstUse::class, ScreenState.Insufficient::class,
            ScreenState.Ready::class, ScreenState.SourceProblem::class, ScreenState.Error::class,
        ).inOrder()
        assertThat((all.getValue("ready") as ScreenState.Ready).model).isEqualTo(SnapshotFixtures.full())
    }

    @Test fun `variants cover every sealed subtype`() {
        val covered = ScreenStateFixtures.states("model").map { it::class.java }.toSet()
        val subtypes = ScreenState::class.java.permittedSubclasses.toSet()
        assertThat(covered).isEqualTo(subtypes)
    }
}
