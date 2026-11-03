plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.jetbrains.kotlin.compose)
}

android {
    namespace = "hunoia.luno.link"
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
    api(project(":core:runtime"))
    api(project(":ui:design"))
    // 偏差（记录在 refactor-progress.md）：链接分发仍通过 ConfigProvider 读写配置；
    // 纯规则模型已在 :core:domain，等存储门面端口化后删除这条边。
    api(project(":core:data"))
    implementation(libs.jetbrains.kotlin.serialization)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)
}
