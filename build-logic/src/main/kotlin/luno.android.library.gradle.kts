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
    lint {
        // 关键 issue（lint.xml 里设为 error）阻断构建；baseline 抑制存量，门禁只拦新增。
        abortOnError = true
        lintConfig = rootProject.layout.projectDirectory.file("lint.xml").asFile
        baseline = file("lint-baseline.xml")
        enable += "UnusedResources"
    }
}
