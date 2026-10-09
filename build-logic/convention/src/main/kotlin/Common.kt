import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

internal object DriftSdk {
    const val COMPILE = 36
    const val TARGET = 36
    const val MIN = 28
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal fun KotlinJvmCompilerOptions.driftDefaults() {
    jvmTarget.set(JvmTarget.JVM_17)
    allWarningsAsErrors.set(false)
    freeCompilerArgs.addAll("-Xjsr305=strict", "-opt-in=kotlin.RequiresOptIn")
}

internal fun Project.configureAndroidCommon(ext: CommonExtension<*, *, *, *, *, *>) {
    ext.apply {
        compileSdk = DriftSdk.COMPILE
        defaultConfig.minSdk = DriftSdk.MIN
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.unitTests.isReturnDefaultValues = true
        lint {
            abortOnError = true
            warningsAsErrors = false
            checkDependencies = false
        }
    }
    tasks.withType<Test>().configureEach {
        maxHeapSize = "1g"
        // Robolectric tests are JUnit4; vintage engine runs them on the JUnit Platform.
        useJUnitPlatform()
    }
}

/** Standard test dependencies for every module (JUnit 5 + vintage for JUnit4/Robolectric). */
internal fun Project.addTestDependencies() {
    dependencies.apply {
        add("testImplementation", dependencies.platform(libs.lib("junit-bom")))
        add("testImplementation", libs.lib("junit-jupiter"))
        add("testImplementation", libs.lib("truth"))
        add("testImplementation", libs.lib("kotest-property"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testRuntimeOnly", libs.lib("junit-platform-launcher"))
        add("testRuntimeOnly", libs.lib("junit-vintage-engine"))
    }
}
