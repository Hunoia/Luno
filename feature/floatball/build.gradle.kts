plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.compose)
}

android {
    namespace = "hunoia.luno.floatball"
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
    api(project(":core:bridge"))
    api(project(":core:data"))
    api(project(":core:runtime"))
    api(project(":ui:design"))
    api(project(":feature:conditions"))
    api(project(":feature:automation"))
    api(project(":feature:gesture"))
    api(project(":feature:link"))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.com.aaronzzx.fastcompose.compose) {
        exclude(group = "androidx.hilt")
        exclude(group = "com.google.dagger")
        exclude(group = "com.google.accompanist")
        exclude(group = "net.engawapg.lib")
    }
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.coil.compose)
}
