package app.drift.source.healthconnect

import app.drift.model.QualityTier

/**
 * Maps the package name that wrote a Health Connect record (its data origin) to a [QualityTier].
 * Higher tiers win when sources disagree about the same span (plan section 5.1).
 *
 * Extension slot for the boAt ring: if the boAt companion app writes to Health Connect, register its
 * package with `OriginTierTable.DEFAULT.with(boatPackage to QualityTier.WEARABLE_RING)` where the table is
 * built (see [HealthConnectModule]); the engine needs no change (plan section 5.4).
 */
class OriginTierTable private constructor(private val tiers: Map<String, QualityTier>) {

    fun with(vararg extra: Pair<String, QualityTier>): OriginTierTable = OriginTierTable(tiers + extra)

    /**
     * Tier for [origin]. Unknown writers fall back on the recording device type when Health Connect
     * reports one, and otherwise on [UNKNOWN_ORIGIN_TIER].
     */
    fun tierFor(origin: String?, deviceType: DeviceKind = DeviceKind.UNKNOWN): QualityTier {
        origin?.let { tiers[it] }?.let { return it }
        return when (deviceType) {
            DeviceKind.RING -> QualityTier.WEARABLE_RING
            DeviceKind.WATCH, DeviceKind.BAND -> QualityTier.WEARABLE_WRIST
            DeviceKind.PHONE -> QualityTier.PHONE_APP
            DeviceKind.UNKNOWN -> UNKNOWN_ORIGIN_TIER
        }
    }

    enum class DeviceKind { WATCH, RING, BAND, PHONE, UNKNOWN }

    companion object {
        /** Unidentified writers are assumed to be a phone app: recorded data, but the lowest recorded tier. */
        val UNKNOWN_ORIGIN_TIER = QualityTier.PHONE_APP

        const val SLEEP_AS_ANDROID = "com.urbandroid.sleep"

        val DEFAULT = OriginTierTable(
            mapOf(
                SLEEP_AS_ANDROID to QualityTier.PHONE_APP,
                "com.google.android.apps.fitness" to QualityTier.PHONE_INFERRED,
                "com.google.android.apps.healthdata" to QualityTier.PHONE_INFERRED,
                "com.google.android.apps.wear.companion" to QualityTier.WEARABLE_WRIST,
                "com.google.android.apps.wearables.maestro.companion" to QualityTier.WEARABLE_WRIST,
                "com.fitbit.FitbitMobile" to QualityTier.WEARABLE_WRIST,
                "com.garmin.android.apps.connectmobile" to QualityTier.WEARABLE_WRIST,
                "com.samsung.android.shealth" to QualityTier.WEARABLE_WRIST,
                "com.sec.android.app.shealth" to QualityTier.WEARABLE_WRIST,
                "com.withings.wiscale2" to QualityTier.WEARABLE_WRIST,
                "com.xiaomi.wearable" to QualityTier.WEARABLE_WRIST,
                "com.xiaomi.hm.health" to QualityTier.WEARABLE_WRIST,
                "com.huawei.health" to QualityTier.WEARABLE_WRIST,
                "com.polar.polarflow" to QualityTier.WEARABLE_WRIST,
                "com.whoop.android" to QualityTier.WEARABLE_WRIST,
                "com.coros.coros" to QualityTier.WEARABLE_WRIST,
                "com.suunto.connectivity.suuntoconnectivity" to QualityTier.WEARABLE_WRIST,
                "com.ouraring.oura" to QualityTier.WEARABLE_RING,
                "com.ultrahuman.android" to QualityTier.WEARABLE_RING,
                "com.circular.ring" to QualityTier.WEARABLE_RING,
            ),
        )
    }
}
