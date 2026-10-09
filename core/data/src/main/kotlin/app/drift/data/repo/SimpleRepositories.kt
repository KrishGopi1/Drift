package app.drift.data.repo

import app.drift.data.codec.DriftJson
import app.drift.data.codec.ProfileDto
import app.drift.data.codec.toDto
import app.drift.data.codec.toModel
import app.drift.data.db.DriftDatabase
import app.drift.data.db.ProfileEntity
import app.drift.model.CheckIn
import app.drift.model.engine.UserProfile
import app.drift.model.repository.AppSettings
import app.drift.model.repository.CheckInRepository
import app.drift.model.repository.ProfileRepository
import app.drift.model.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val db: DriftDatabase,
    private val prefs: DriftPreferences,
) : ProfileRepository {
    override fun observeProfile(): Flow<UserProfile?> =
        db.profile().observe().map { row -> row?.let { DriftJson.decodeFromString(ProfileDto.serializer(), it.payload).toModel() } }

    override suspend fun saveProfile(profile: UserProfile) {
        db.profile().upsert(ProfileEntity(1, DriftJson.encodeToString(ProfileDto.serializer(), profile.toDto())))
    }

    override fun observeOnboardingComplete(): Flow<Boolean> = prefs.onboardingComplete
    override suspend fun setOnboardingComplete(complete: Boolean) = prefs.setOnboardingComplete(complete)
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(private val prefs: DriftPreferences) : SettingsRepository {
    override fun observeSettings(): Flow<AppSettings> = prefs.settings
    override suspend fun update(transform: (AppSettings) -> AppSettings) = prefs.update(transform)
}

@Singleton
class CheckInRepositoryImpl @Inject constructor(private val db: DriftDatabase) : CheckInRepository {
    override fun observeCheckIns(since: Instant): Flow<List<CheckIn>> =
        db.checkIns().observeSince(since.toEpochMilli()).map { rows -> rows.map(CheckInCodec::decode) }

    override suspend fun add(checkIn: CheckIn) {
        db.checkIns().upsert(CheckInCodec.encode(checkIn))
    }
}
