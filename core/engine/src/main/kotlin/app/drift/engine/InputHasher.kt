package app.drift.engine

import app.drift.model.engine.EngineInput
import java.security.MessageDigest

/** SHA-256 over a canonical, order-independent serialization of an [EngineInput]. */
internal object InputHasher {
    fun hash(input: EngineInput, version: String): String {
        val sb = StringBuilder(4096)
        sb.append("engine=").append(version).append('\n')
        sb.append("now=").append(input.now).append(";zone=").append(input.zone.id).append('\n')
        val eps = input.episodes.map { e ->
            buildString {
                append("E|").append(e.id.value).append('|').append(e.start).append('|').append(e.end).append('|').append(e.zone.id)
                append('|').append(e.kind).append('|').append(e.inBed?.let { "${it.start}/${it.end}" } ?: "-")
                append('|').append(e.origin.sourceId.value).append('|').append(e.origin.recordId).append('|').append(e.origin.qualityTier)
                append('|').append(e.origin.dataOrigin ?: "-").append('|').append(e.origin.device ?: "-")
                append('|').append(e.quality.completeness).append('|').append(e.quality.detectionConfidence)
                append('|').append(e.quality.flags.map { it.name }.sorted().joinToString(","))
                append('|').append(e.userEdited)
                append('|').append(e.stages?.joinToString(",") { "${it.stage}@${it.interval.start}/${it.interval.end}" } ?: "-")
            }
        }.sorted()
        eps.forEach { sb.append(it).append('\n') }
        input.signals.signals.map { "S|$it" }.sorted().forEach { sb.append(it).append('\n') }
        sb.append("W|").append(input.signals.window).append('\n')
        input.annotations.map { "A|$it" }.sorted().forEach { sb.append(it).append('\n') }
        input.checkIns.map { "C|$it" }.sorted().forEach { sb.append(it).append('\n') }
        val pr = input.profile
        val sc = pr.schedule
        sb.append("P|").append(pr.ageBand).append('|').append(pr.chronotype).append('|')
            .append(sc.workdays.map { it.name }.sorted().joinToString(",")).append('|')
            .append(sc.wakeTime).append('|').append(sc.freeDayBedtime).append('|').append(sc.freeDayWakeTime).append('|')
            .append(pr.goals).append('\n')
        input.priorState?.let { st ->
            sb.append("X|").append(st.engineVersion).append('|').append(st.asOf).append('\n')
            st.values.toSortedMap().forEach { (k, v) -> sb.append(k).append('=').append(v).append('\n') }
        }
        val md = MessageDigest.getInstance("SHA-256").digest(sb.toString().toByteArray(Charsets.UTF_8))
        return md.joinToString("") { "%02x".format(it) }
    }
}
