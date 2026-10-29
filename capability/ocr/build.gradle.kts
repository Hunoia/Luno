plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "hunoia.luno.capability.ocr"
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
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.core)
    // unbundled：模型由 GMS 按需下载，APK 不打包模型（见 docs/ocr-mlkit-plan-2026-10-29.md §2）。
    implementation(libs.play.mlkit.text.recognition.chinese)
    implementation(libs.play.mlkit.text.recognition.japanese)
    implementation(libs.play.mlkit.text.recognition.korean)
    testImplementation(libs.junit)
}
