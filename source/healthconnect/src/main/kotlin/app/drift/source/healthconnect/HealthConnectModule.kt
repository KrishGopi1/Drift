package app.drift.source.healthconnect

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the gateway. `:app` registers the adapter itself:
 * `@Binds @IntoSet abstract fun healthConnect(a: HealthConnectSourceAdapter): SourceAdapter`.
 * To add the boAt ring via Health Connect, build the adapter with
 * `OriginTierTable.DEFAULT.with(<boat package> to QualityTier.WEARABLE_RING)`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HealthConnectModule {
    @Binds abstract fun gateway(impl: RealHealthConnectGateway): HealthConnectGateway
}
