plugins {
    alias(libs.plugins.drift.android.application)
    alias(libs.plugins.drift.android.compose)
    alias(libs.plugins.drift.hilt)
}

android {
    namespace = "app.drift"
    defaultConfig {
        applicationId = "app.drift"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signing config is wired by A6 from keystore.properties (gitignored).
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
    testImplementation(project(":testing"))
}
