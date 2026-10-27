plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "hunoia.luno.search"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk = 33
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:domain"))
    api(project(":core:bridge"))
    api(project(":core:data"))
    // 图标原子组件（TextIconGlyph 等）；搜索本身不依赖动作模块，动作条目走 ActionSearchSource 契约。
    api(project(":ui:design"))
    api(project(":capability:launch"))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.material.icons.extended)
    implementation(libs.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.houbb.pinyin)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jetbrains.kotlin.serialization)
    testImplementation(libs.junit)
}
