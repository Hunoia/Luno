plugins {
    id("luno.android.library")
}

android {
    namespace = "hunoia.luno.core.common"
    buildFeatures {
        // LunoLog 用生成常量区分 release/debug：release 关闭 INFO 及以下，
        // 与 R8 对 LunoLog.i 的裁剪互相兜底。
        buildConfig = true
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
}
