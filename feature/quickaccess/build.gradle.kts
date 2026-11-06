plugins {
    id("luno.android.library")
    alias(libs.plugins.jetbrains.kotlin.compose)
}

android {
    namespace = "hunoia.luno.quickaccess"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":capability:launch"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:bridge"))
    implementation(project(":core:data"))
    implementation(project(":core:runtime"))
    implementation(project(":ui:design"))
    implementation(project(":feature:actions"))
    implementation(project(":feature:search"))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.coil.compose)
    // coil3.imageLoader / Keyer / ImageLoader 在单例包（coil），coil-compose 不带
    implementation(libs.coil)
    // QuickSearchKeys 等纯逻辑单测（feature/quickaccess/src/test）。
    testImplementation(libs.junit)
}
