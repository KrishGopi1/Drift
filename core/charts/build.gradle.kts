plugins {
    alias(libs.plugins.drift.android.library)
    alias(libs.plugins.drift.android.compose)
}

android {
    namespace = "app.drift.charts"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    testImplementation(project(":testing"))
}
