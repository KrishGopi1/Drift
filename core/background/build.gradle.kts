plugins {
    alias(libs.plugins.drift.android.library)
    alias(libs.plugins.drift.android.compose)
    alias(libs.plugins.drift.hilt)
}

android {
    namespace = "app.drift.background"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:charts"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(project(":testing"))
    testImplementation(libs.androidx.work.testing)
}
