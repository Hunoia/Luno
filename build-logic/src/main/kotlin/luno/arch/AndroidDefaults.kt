package luno.arch

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion

/**
 * application / library 共用的 Android 版本约定：compileSdk / buildTools / minSdk / JDK 17，
 * 以及 JVM 单测的 Android 桩策略。
 *
 * compileSdk 37.1（= compileSdk 37 + compileSdkMinor 1）：Compose foundation 1.13.0 的 AAR 声明
 * minCompileSdk=37 + minCompileMinorSdk=1，compileSdk 只写 37 会在合并资源阶段直接失败
 * （material3 1.5.0 只要求 minor 0，锚点是 Compose train）。build-tools 无 37.1.0，仍用 37.0.0。
 *
 * 两个约定插件只保留各自独有的配置，版本号不再各写一份，避免升级时漏改。
 */
fun CommonExtension.applyLunoAndroidDefaults() {
    compileSdk = 37
    compileSdkMinor = 1
    buildToolsVersion = "37.0.0"

    defaultConfig.minSdk = 33
    compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    compileOptions.targetCompatibility = JavaVersion.VERSION_17
    testOptions.unitTests.isReturnDefaultValues = true
}
