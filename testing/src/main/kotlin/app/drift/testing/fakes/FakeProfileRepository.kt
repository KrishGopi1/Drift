package app.drift.testing.fakes

import app.drift.model.engine.UserProfile
import app.drift.model.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeProfileRepository(
    profile: UserProfile? = null,
    onboardingComplete: Boolean = false,
) : Recording(), ProfileRepository {
    private val profileFlow = MutableStateFlow(profile)
    private val onboarding = MutableStateFlow(onboardingComplete)

    val profile: UserProfile? get() = profileFlow.value
    val onboardingComplete: Boolean get() = onboarding.value

    override fun observeProfile(): Flow<UserProfile?> = profileFlow

    override suspend fun saveProfile(profile: UserProfile) {
        record("saveProfile", profile)
        profileFlow.value = profile
    }

    override fun observeOnboardingComplete(): Flow<Boolean> = onboarding

    override suspend fun setOnboardingComplete(complete: Boolean) {
        record("setOnboardingComplete", complete)
        onboarding.value = complete
    }
}
