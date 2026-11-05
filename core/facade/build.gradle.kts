plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "hunoia.luno.core.facade"
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

/**
 * UI 可见的门面契约（architecture §8.1「domain 门面」的 Android 变体）。
 *
 * `:core:domain` 是纯 JVM，放不下带 `Context` / `Uri` 的门面签名；本模块只放**契约与 DTO**，
 * 实现一律在 `:app`，由 `:ui:design` 的 `LocalFacades` 注入到页面。
 */
dependencies {
    api(project(":core:common"))
    api(project(":core:domain"))
    api(project(":core:data"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.annotation)
}
