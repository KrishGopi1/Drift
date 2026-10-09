package app.drift.data.db

import app.drift.data.codec.DriftJson
import app.drift.data.codec.EpisodeDto
import app.drift.data.codec.SignalDto
import app.drift.data.codec.SourceRefDto
import app.drift.data.codec.toDto
import app.drift.data.codec.toModel
import app.drift.data.merge.RawEpisode
import app.drift.data.merge.UserEdit
import app.drift.data.merge.episodeKey
import app.drift.model.Signal
import app.drift.model.SleepEpisode
import java.time.Instant

internal fun SleepEpisode.toRawEntity(ingestedAt: Instant) = SourceEpisodeEntity(
    key = episodeKey(origin),
    sourceId = origin.sourceId.value,
    recordId = origin.recordId,
    startMs = start.toEpochMilli(),
    endMs = end.toEpochMilli(),
    ingestedAtMs = ingestedAt.toEpochMilli(),
    payload = DriftJson.encodeToString(EpisodeDto.serializer(), toDto()),
)

internal fun SourceEpisodeEntity.toEpisode(): SleepEpisode = DriftJson.decodeFromString(EpisodeDto.serializer(), payload).toModel()
internal fun SourceEpisodeEntity.toRaw() = RawEpisode(toEpisode(), Instant.ofEpochMilli(ingestedAtMs))

internal fun UserEpisodeEntity.toEdit() = UserEdit(
    episode = DriftJson.decodeFromString(EpisodeDto.serializer(), payload).toModel(),
    deleted = deleted,
    suppressedKeys = if (suppressedKeys.isEmpty()) emptySet() else suppressedKeys.split('\n').toSet(),
)

internal fun UserEdit.toEntity() = UserEpisodeEntity(
    id = episode.id.value,
    startMs = episode.start.toEpochMilli(),
    endMs = episode.end.toEpochMilli(),
    deleted = deleted,
    suppressedKeys = suppressedKeys.sorted().joinToString("\n"),
    payload = DriftJson.encodeToString(EpisodeDto.serializer(), episode.toDto()),
)

internal fun Signal.toEntity(): SignalEntity {
    val d = toDto()
    return d.toEntity()
}

internal fun SignalDto.toEntity() = SignalEntity(
    sourceId = origin.sourceId, recordId = origin.recordId, family = family, startMs = startMs, endMs = endMs,
    v1 = v1, v2 = v2, code = code, tier = origin.tier.name, dataOrigin = origin.dataOrigin, device = origin.device,
)

internal fun SignalEntity.toDto() = SignalDto(
    family = family,
    origin = SourceRefDto(sourceId, recordId, app.drift.model.QualityTier.valueOf(tier), dataOrigin, device),
    startMs = startMs, endMs = endMs, v1 = v1, v2 = v2, code = code,
)

internal fun SignalEntity.toSignal(): Signal = toDto().toModel()
