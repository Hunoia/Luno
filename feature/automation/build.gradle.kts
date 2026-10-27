plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "hunoia.luno.automation"
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
    // 偏差（记录在 refactor-progress.md）：规则模型（AutomationRule / RuleStep / 节流）在 :core:data。
    api(project(":core:data"))
    api(project(":core:runtime"))
    api(project(":feature:conditions"))
    api(project(":feature:execute"))
    implementation(libs.jetbrains.kotlin.serialization)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
}
