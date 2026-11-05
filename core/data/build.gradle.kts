plugins {
    id("luno.android.library")
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "hunoia.luno.core.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:bridge"))
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.compose.ui.geometry)
    implementation(libs.jetbrains.kotlin.serialization)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.annotation)
}
