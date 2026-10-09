package app.drift.di

import app.drift.engine.DefaultSleepEngine
import app.drift.model.engine.SleepEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {
    /** Fail-safe on: an unexpected engine error yields a priors-only snapshot instead of a crash. */
    @Provides @Singleton
    fun sleepEngine(): SleepEngine = DefaultSleepEngine(failSafe = true)
}
