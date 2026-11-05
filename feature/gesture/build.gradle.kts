plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.compose)
}

android {
    namespace = "hunoia.luno.gesture"
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
        // JVM 单测直接调用生产代码，其中 android.util.Log 需返回默认值而非抛 "not mocked"。
        unitTests.isReturnDefaultValues = true
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:bridge"))
    implementation(project(":core:data"))
    implementation(project(":core:runtime"))
    implementation(project(":ui:design"))
    implementation(project(":feature:actions"))
    implementation(project(":feature:link"))
    implementation(project(":capability:ocr"))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.com.aaronzzx.fastcompose.compose) {
        exclude(group = "androidx.hilt")
        exclude(group = "com.google.dagger")
        exclude(group = "com.google.accompanist")
        exclude(group = "net.engawapg.lib")
    }
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
