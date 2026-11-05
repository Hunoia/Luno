plugins {
    id("luno.android.library")
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.jetbrains.kotlin.compose)
}

android {
    namespace = "hunoia.luno.link"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:bridge"))
    implementation(project(":core:runtime"))
    implementation(project(":ui:design"))
    // 偏差（记录在 docs/archive.md §1 挂账）：链接分发仍通过 ConfigProvider 读写配置；
    // 纯规则模型已在 :core:domain，等存储门面端口化后删除这条边。
    implementation(project(":core:data"))
    implementation(libs.jetbrains.kotlin.serialization)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
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
