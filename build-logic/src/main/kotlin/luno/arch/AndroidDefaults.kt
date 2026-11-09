package luno.arch

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion

/**
 * application / library 共用的 Android 版本约定：compileSdk / buildTools / minSdk / JDK 17，
 * 以及 JVM 单测的 Android 桩策略。
 *
 * 两个约定插件只保留各自独有的配置，版本号不再各写一份，避免升级时漏改。
 */
fun CommonExtension.applyLunoAndroidDefaults() {
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig.minSdk = 33
    compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    compileOptions.targetCompatibility = JavaVersion.VERSION_17
    testOptions.unitTests.isReturnDefaultValues = true
}
