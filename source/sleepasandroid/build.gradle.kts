plugins {
    alias(libs.plugins.drift.android.library)
    alias(libs.plugins.drift.hilt)
}

android {
    namespace = "app.drift.source.sleepasandroid"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(project(":testing"))
}
