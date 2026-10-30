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
    // PP-OCRv5：LiteRT CompiledModel + Qualcomm NPU dispatch；模型为 host AOT 包，用户导入。
    implementation(libs.litert)
    implementation(libs.litert.npu.qualcomm)
    implementation(libs.qnn.runtime)
    testImplementation(libs.junit)
    // 单测里 OcrModelStore 解析 manifest.json；Android 的 org.json 在 JVM 单测中是空实现。
    testImplementation(libs.org.json)
}
