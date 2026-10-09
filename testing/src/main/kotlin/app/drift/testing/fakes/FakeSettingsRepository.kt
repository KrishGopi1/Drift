package app.drift.testing.fakes

import app.drift.model.repository.AppSettings
import app.drift.model.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(settings: AppSettings = AppSettings()) : Recording(), SettingsRepository {
    private val state = MutableStateFlow(settings)
    val settings: AppSettings get() = state.value

    fun set(settings: AppSettings) { state.value = settings }

    override fun observeSettings(): Flow<AppSettings> = state

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        record("update")
        state.value = transform(state.value)
    }
}
