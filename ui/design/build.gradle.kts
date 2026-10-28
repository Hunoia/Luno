plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.compose)
}

android {
    namespace = "hunoia.luno.design"
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
    // 只依赖契约（TextIcon 等展示数据），不依赖任何 feature。
    api(project(":core:domain"))
    // 图标原子组件要读系统/启动器信息（只读查询，无业务）。
    api(project(":core:bridge"))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.collection)
    implementation(libs.coil.compose)
    implementation(libs.haze)
}
