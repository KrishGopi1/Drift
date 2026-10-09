plugins {
    `kotlin-dsl`
}

group = "app.drift.buildlogic"

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("jvmLibrary") { id = "drift.jvm.library"; implementationClass = "JvmLibraryConventionPlugin" }
        register("androidLibrary") { id = "drift.android.library"; implementationClass = "AndroidLibraryConventionPlugin" }
        register("androidCompose") { id = "drift.android.compose"; implementationClass = "AndroidComposeConventionPlugin" }
        register("androidFeature") { id = "drift.android.feature"; implementationClass = "AndroidFeatureConventionPlugin" }
        register("androidApplication") { id = "drift.android.application"; implementationClass = "AndroidApplicationConventionPlugin" }
        register("hilt") { id = "drift.hilt"; implementationClass = "HiltConventionPlugin" }
    }
}
