import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")
        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            defaultConfig.consumerProguardFiles("consumer-rules.pro")
        }
        extensions.configure<KotlinAndroidProjectExtension> { compilerOptions.driftDefaults() }
        addTestDependencies()
        dependencies.add("testImplementation", libs.lib("robolectric"))
        dependencies.add("testImplementation", libs.lib("junit4"))
        dependencies.add("testImplementation", libs.lib("androidx-test-ext-junit"))
    }
}
