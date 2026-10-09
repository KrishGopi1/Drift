package app.drift.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QuantityTest {
    @Test
    fun `estimated quantity requires a confidence band`() {
        assertThrows<IllegalArgumentException> { Quantity(8.0, Provenance.ESTIMATED) }
    }

    @Test
    fun `map transforms value and band`() {
        val q = Quantity(2.0, Provenance.ESTIMATED, Confidence(1.0, 3.0, 0.9, ConfidenceLevel.MEDIUM))
        val m = q.map { -it }
        assertThat(m.value).isEqualTo(-2.0)
        assertThat(m.confidence!!.lower).isEqualTo(-3.0)
        assertThat(m.confidence!!.upper).isEqualTo(-1.0)
    }
}
