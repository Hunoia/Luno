plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "hunoia.luno.core.common"
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
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.annotation)
}
