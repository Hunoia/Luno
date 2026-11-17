plugins {
    id("luno.android.library")
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.jetbrains.kotlin.compose)
}

android {
    namespace = "hunoia.luno.capability.appcatalog"

    buildFeatures {
        buildConfig = true
    }
}

/**
 * 应用目录与匹配：已安装应用清单、模糊匹配、抽屉分区、应用排序。
 *
 * 独立成模块（不复用 `:capability:launch`）：搜索/选择页/网格面板/快速启动都读同一份
 * 应用目录，且匹配逻辑（AppSearch）是纯算法，不应藏在启动能力里。
 */
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:bridge"))
    implementation(project(":core:data"))
    // AppInfo 的 Compose 图标投影（rememberAppDrawable）依赖设计系统。
    implementation(project(":ui:design"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.collection)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.houbb.pinyin)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.jetbrains.kotlin.serialization)
    testImplementation(libs.junit)
}
