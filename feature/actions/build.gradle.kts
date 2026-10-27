plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "hunoia.luno.actions"
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
    // 偏差（同 conditions/automation/link）：音量刮擦默认值等配置模型在 :core:data。
    api(project(":core:data"))
    api(project(":feature:execute"))
    // 动作展示 UI（图标/标题/行内组件）：动作扩展面自带的展示能力。
    api(project(":ui:design"))
    api(project(":core:runtime"))
    implementation(libs.jetbrains.kotlin.serialization)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    // ActionDefinition 仍带 ImageVector（纯元数据 + 图标映射的拆分留到 Phase 4）。
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.coil)
    implementation(libs.coil.compose)
}
