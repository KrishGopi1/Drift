package app.drift.feature.energy.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier

/** Marks the [Clock] the Energy screen reads "now" and the local zone from. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EnergyClock

@Module
@InstallIn(SingletonComponent::class)
object EnergyClockModule {
    @Provides
    @EnergyClock
    fun provideEnergyClock(): Clock = Clock.systemDefaultZone()
}
