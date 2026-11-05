plugins {
    id("luno.android.library")
}

android {
    namespace = "hunoia.luno.capability.ocr"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.core)
    // PP-OCRv6：LiteRT CompiledModel + GPU delegate（OpenCL）；fp32 tflite 原始包，用户导入。
    implementation(libs.litert)
    implementation(libs.litert.gpu)
    testImplementation(libs.junit)
    // 单测里 OcrModelStore 解析 manifest.json；Android 的 org.json 在 JVM 单测中是空实现。
    testImplementation(libs.org.json)
}
