import com.android.build.api.dsl.ApplicationExtension
import luno.arch.applyLunoAndroidDefaults

/**
 * Luno 的 Android application 约定：SDK / JDK 版本走 `AndroidDefaults.kt`。
 * applicationId、versionCode、签名、buildTypes 仍留在 `:app`（它们是发布配置，不是约定）。
 */
plugins {
    id("com.android.application")
}

extensions.configure<ApplicationExtension> {
    applyLunoAndroidDefaults()
    lint {
        // 关键 issue（lint.xml 里设为 error）阻断构建；baseline 抑制存量，门禁只拦新增。
        abortOnError = true
        lintConfig = rootProject.layout.projectDirectory.file("lint.xml").asFile
        baseline = file("lint-baseline.xml")
        enable += "UnusedResources"
    }
}
