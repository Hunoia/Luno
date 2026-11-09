import com.android.build.api.dsl.LibraryExtension
import luno.arch.applyLunoAndroidDefaults

/**
 * Luno 的 Android library 约定：
 * compileSdk / buildTools / minSdk / JDK 17 统一在 `AndroidDefaults.kt`，
 * 模块只声明 namespace 与自己的 buildFeatures。
 *
 * 新增 library 模块用它，不要再复制那五行。
 */
plugins {
    id("com.android.library")
}

extensions.configure<LibraryExtension> {
    applyLunoAndroidDefaults()
}
