plugins {
    alias(libs.plugins.drift.android.feature)
}

android {
    namespace = "app.drift.feature.onboarding"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
