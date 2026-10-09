plugins {
    alias(libs.plugins.drift.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    api(project(":core:model"))
    testImplementation(project(":testing"))
}

// Engine replay CLI (plan 4 / 9.2): ./gradlew :core:engine:run --args="<export.json>"
application {
    mainClass.set("app.drift.engine.replay.ReplayCliKt")
}
