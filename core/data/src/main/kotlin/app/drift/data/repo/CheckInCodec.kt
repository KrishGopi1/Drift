package app.drift.data.repo

import app.drift.data.codec.CheckInDto
import app.drift.data.codec.DriftJson
import app.drift.data.codec.toDto
import app.drift.data.codec.toModel
import app.drift.data.db.CheckInEntity
import app.drift.model.CheckIn

internal object CheckInCodec {
    fun encode(c: CheckIn): CheckInEntity {
        val dto = c.toDto()
        return CheckInEntity(type = dto.type, atMs = c.at.toEpochMilli(), payload = DriftJson.encodeToString(CheckInDto.serializer(), dto))
    }

    fun decode(e: CheckInEntity): CheckIn = DriftJson.decodeFromString(CheckInDto.serializer(), e.payload).toModel()
}
