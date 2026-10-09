import org.gradle.api.Plugin
import org.gradle.api.Project

/** :feature:* modules: Android library + Compose + Hilt + the shared UI/model deps. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("drift.android.library")
        pluginManager.apply("drift.android.compose")
        pluginManager.apply("drift.hilt")
        dependencies.apply {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:charts"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("androidx-hilt-navigation-compose"))
            add("implementation", libs.lib("kotlinx-coroutines-android"))
            add("testImplementation", project(":testing"))
            add("testImplementation", libs.lib("turbine"))
        }
    }
}
