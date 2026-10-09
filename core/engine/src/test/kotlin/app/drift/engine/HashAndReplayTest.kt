package app.drift.engine

import app.drift.engine.replay.Replay
import app.drift.engine.replay.ReplayConverter
import app.drift.engine.replay.ReplayFile
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.time.ZoneId

class HashAndReplayTest {
    private val utc = ZoneId.of("UTC")

    @Test fun `hash is stable under reordering and changes with the data`() {
        val input = Synth.steady(utc).input()
        val a = Kit.engine.analyze(input).inputHash
        val shuffled = Kit.engine.analyze(input.copy(episodes = input.episodes.reversed())).inputHash
        assertThat(shuffled).isEqualTo(a)
        val changed = input.copy(episodes = input.episodes.drop(1))
        assertThat(Kit.engine.analyze(changed).inputHash).isNotEqualTo(a)
        val moved = input.copy(now = input.now.plusSeconds(1))
        assertThat(Kit.engine.analyze(moved).inputHash).isNotEqualTo(a)
        assertThat(Kit.engine.analyze(input).inputHash).isEqualTo(a)
    }

    @Test fun `the sample export replays day by day`() {
        val text = javaClass.getResource("/replay/sample-export.json")!!.readText()
        val file = Json { ignoreUnknownKeys = true }.decodeFromString(ReplayFile.serializer(), text)
        val rows = Replay.run(ReplayConverter.convert(file), Kit.engine)
        assertThat(rows.size).isAtLeast(60)
        assertThat(rows.first().date).isLessThan(rows.last().date)
        assertThat(rows.last().needMinutes).isIn(com.google.common.collect.Range.closed(300L, 660L))
        val table = Replay.format(rows)
        assertThat(table).contains("need (90% band)")
        assertThat(table).contains("days replayed")
        // The prior-to-personal switch is annotated rather than counted as a jump.
        assertThat(rows.any { it.personalizedThisWeek }).isTrue()
        assertThat(rows.filter { it.needJump }).isEmpty()
    }

    @Test fun `replay feeds each run's state into the next`() {
        val text = javaClass.getResource("/replay/sample-export.json")!!.readText()
        val file = Json { ignoreUnknownKeys = true }.decodeFromString(ReplayFile.serializer(), text)
        val a = Replay.run(ReplayConverter.convert(file), Kit.engine)
        val b = Replay.run(ReplayConverter.convert(file), Kit.engine)
        assertThat(a).isEqualTo(b)
    }
}
