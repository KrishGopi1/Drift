plugins {
    alias(libs.plugins.drift.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    api(project(":core:model"))
    // Used only by the replay file format and CLI (app.drift.engine.replay); the analysis models do no I/O.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":testing"))
}

// Engine replay CLI (plan 4 / 9.2): ./gradlew :core:engine:run --args="<export.json>"
application {
    mainClass.set("app.drift.engine.replay.ReplayCliKt")
}
