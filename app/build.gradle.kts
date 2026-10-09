import java.util.Properties

plugins {
    alias(libs.plugins.drift.android.application)
    alias(libs.plugins.drift.android.compose)
    alias(libs.plugins.drift.hilt)
}

// Release signing (docs/qa/release-signing.md). Without keystore.properties the release APK is unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "app.drift"
    defaultConfig {
        applicationId = "app.drift"
        versionCode = 1
        versionName = "0.1.0"
    }
    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
            // minSdk 28: the legacy JAR scheme (v1) is not needed; v2 + v3 are required (plan 9.3).
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        release {
            signingConfig = if (keystoreProperties.isNotEmpty()) signingConfigs.getByName("release") else null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:engine"))
    implementation(project(":core:data"))
    implementation(project(":core:background"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:charts"))
    implementation(project(":source:healthconnect"))
    implementation(project(":source:sleepasandroid"))
    implementation(project(":source:phone"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:energy"))
    implementation(project(":feature:sleep"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.core.splashscreen)
    testImplementation(project(":testing"))
}
