package app.drift.testing.fakes

import app.drift.model.CheckIn
import app.drift.model.repository.CheckInRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

class FakeCheckInRepository(checkIns: List<CheckIn> = emptyList()) : Recording(), CheckInRepository {
    private val state = MutableStateFlow(checkIns)
    val checkIns: List<CheckIn> get() = state.value

    fun set(list: List<CheckIn>) { state.value = list }

    override fun observeCheckIns(since: Instant): Flow<List<CheckIn>> =
        state.map { l -> l.filter { !it.at.isBefore(since) }.sortedBy { it.at } }

    override suspend fun add(checkIn: CheckIn) {
        record("add", checkIn)
        state.update { it + checkIn }
    }
}
