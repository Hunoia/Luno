plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "hunoia.luno.core.data"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk = 33
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:domain"))
    api(project(":core:bridge"))
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.compose.ui.geometry)
    implementation(libs.jetbrains.kotlin.serialization)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.annotation)
}
