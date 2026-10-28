plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    id("kotlin-parcelize")
}

android {
    namespace = "hunoia.luno.core.bridge"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk = 33
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.unit)
    implementation(libs.rikka.shizuku.api)
    implementation(libs.hiddenapibypass)
    implementation(libs.jetbrains.kotlin.serialization)
}
