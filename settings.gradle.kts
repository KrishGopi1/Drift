pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "drift"

include(
    ":app",
    ":core:model",
    ":core:engine",
    ":core:data",
    ":core:background",
    ":core:designsystem",
    ":core:charts",
    ":source:healthconnect",
    ":source:sleepasandroid",
    ":source:phone",
    ":feature:onboarding",
    ":feature:energy",
    ":feature:sleep",
    ":feature:settings",
    ":testing",
)
