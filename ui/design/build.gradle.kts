plugins {
    id("luno.android.library")
    alias(libs.plugins.jetbrains.kotlin.compose)
}

android {
    namespace = "hunoia.luno.design"
}

dependencies {
    implementation(project(":core:common"))
    // 只依赖契约（TextIcon 等展示数据），不依赖任何 feature。
    implementation(project(":core:domain"))
    api(project(":core:facade"))
    // 图标原子组件要读系统/启动器信息（只读查询，无业务）。
    implementation(project(":core:bridge"))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.collection)
    implementation(libs.coil.compose)
    // LunoScreen/LunoSheet 的公开参数含 HazeState：属于契约性再导出
    api(libs.haze)
    // 连续曲率圆角：Dimension.kt 公开 token 的类型是 SquircleShape，需再导出。
    // 排除 JetBrains 的 material3：它会把 M3 从 1.4.0 抬到 1.5.0-alpha17，而 alpha 版
    // 是按 foundation 1.11 编的，与本仓 foundation 1.12.1 的 CustomStyle 签名不兼容 ——
    // 表现为带输入框的页面一渲染就 AbstractMethodError（OutlinedTextFieldDefaults）。
    api(libs.squircle.shape) {
        exclude(group = "org.jetbrains.compose.material3")
    }
    // 仅 debug：形状软度画廊（src/debug/.../ShapeGalleryPreviews.kt）只在 Android Studio 预览生效
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
}
