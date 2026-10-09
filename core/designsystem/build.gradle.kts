plugins {
    alias(libs.plugins.drift.android.library)
    alias(libs.plugins.drift.android.compose)
}

android {
    namespace = "app.drift.designsystem"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
}
