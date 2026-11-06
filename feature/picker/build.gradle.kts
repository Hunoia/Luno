plugins {
    id("luno.android.library")
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "hunoia.luno.picker"
}

/**
 * 动作选择器：跨 feature 的聚合页面。
 *
 * 它要同时读动作库、网格面板、快捷启动与动作面板的数据，因此单独成模块——
 * 塞进任何一个被它读取的 feature 都会造成反向依赖。
 */
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:bridge"))
    implementation(project(":core:data"))
    implementation(project(":core:runtime"))
    implementation(project(":core:facade"))
    implementation(project(":ui:design"))
    implementation(project(":feature:actions"))
    implementation(project(":feature:gridpanel"))
    implementation(project(":feature:gesture"))
    implementation(project(":feature:floatball"))
    implementation(project(":feature:quicklaunch"))
    implementation(project(":feature:search"))
    implementation(libs.jetbrains.kotlin.serialization)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.com.aaronzzx.fastcompose.compose) {
        exclude(group = "androidx.hilt")
        exclude(group = "com.google.dagger")
        exclude(group = "com.google.accompanist")
        exclude(group = "net.engawapg.lib")
    }
    testImplementation(libs.junit)
}
