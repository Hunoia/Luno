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
    // 3 个脚本模型全部打包：离线可用，不依赖 GMS 下载（见 docs/ocr-mlkit-plan-2026-10-29.md §2）。
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.text.recognition.korean)
    testImplementation(libs.junit)
}
