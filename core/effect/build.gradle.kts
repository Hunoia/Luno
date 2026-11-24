plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

/**
 * 效果播放入口契约（纯 JVM，可跨进程序列化）。
 *
 * 只放 Request / Handle / 接口与枚举，不依赖 Android、配置或渲染实现：
 * `:core:facade` 用它做门面签名，`:feature:floatball` 用它接收请求，
 * `:app` 用它做实现选择（服务转发 / 本进程托管）。
 */
dependencies {
    api(libs.jetbrains.kotlin.serialization)
    testImplementation(libs.junit)
}