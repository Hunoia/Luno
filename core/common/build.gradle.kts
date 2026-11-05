plugins {
    id("luno.android.library")
}

android {
    namespace = "hunoia.luno.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
}
