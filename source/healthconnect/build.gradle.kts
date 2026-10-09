plugins {
    alias(libs.plugins.drift.android.library)
    alias(libs.plugins.drift.hilt)
}

android {
    namespace = "app.drift.source.healthconnect"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.health.connect.client)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(project(":testing"))
}
