import com.android.build.api.dsl.ApplicationExtension

/**
 * Luno 的 Android application 约定：只统一 SDK / JDK 版本。
 * applicationId、versionCode、签名、buildTypes 仍留在 `:app`（它们是发布配置，不是约定）。
 */
plugins {
    id("com.android.application")
}

extensions.configure<ApplicationExtension> {
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
