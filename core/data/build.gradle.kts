plugins {
    alias(libs.plugins.drift.android.library)
    alias(libs.plugins.drift.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.drift.data"
    // Exported Room schemas feed MigrationTestHelper in unit tests.
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(project(":testing"))
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.turbine)
}

room {
    schemaDirectory("$projectDir/schemas")
}
