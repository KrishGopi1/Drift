plugins {
    alias(libs.plugins.drift.jvm.library)
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.test)
    // JSON writer for the replay format (app.drift.testing.synthetic.ReplayJson).
    implementation(libs.kotlinx.serialization.json)
}
