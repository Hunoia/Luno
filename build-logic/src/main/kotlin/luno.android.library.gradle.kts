import com.android.build.api.dsl.LibraryExtension

/**
 * Luno 的 Android library 约定：
 * compileSdk / buildTools / minSdk / JDK 17 由这里统一，模块只声明 namespace 与自己的 buildFeatures。
 *
 * 新增 library 模块用它，不要再复制那五行。
 */
plugins {
    id("com.android.library")
}

extensions.configure<LibraryExtension> {
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
