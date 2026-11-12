plugins {
    id("luno.android.library")
    alias(libs.plugins.jetbrains.kotlin.compose)
}

android {
    namespace = "hunoia.luno.appcontrol"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:bridge"))
    implementation(project(":core:data"))
    implementation(project(":core:facade"))
    implementation(project(":ui:design"))
    // 应用清单、禁用状态与写入点都在启动能力库；页面不自建第二份 PackageManager 扫描。
    implementation(project(":capability:launch"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
