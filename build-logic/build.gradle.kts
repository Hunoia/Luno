plugins {
    `kotlin-dsl`
}

dependencies {
    // AGP 类型只在编译 convention plugin 时需要，不打包进构建逻辑
    compileOnly(libs.android.gradlePlugin)
}
