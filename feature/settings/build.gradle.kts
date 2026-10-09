plugins {
    alias(libs.plugins.drift.android.feature)
}

android {
    namespace = "app.drift.feature.settings"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
