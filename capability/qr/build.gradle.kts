plugins {
    id("luno.android.library")
}

android {
    namespace = "hunoia.luno.capability.qr"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.core)
    // ZXing 纯本地解码（无 GMS）：识别二维码 + 常用一维码。
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
}
