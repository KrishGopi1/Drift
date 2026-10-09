plugins {
    alias(libs.plugins.drift.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.test)
}
