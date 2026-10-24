import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    id("kotlin-parcelize")
}

android {
    namespace = "hunoia.luno"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "hunoia.luno"
        minSdk = 33
        targetSdk = 36
        versionCode = 10607
        versionName = "1.6.7"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

    }
    signingConfigs {
        val localProperties = project.rootProject.file("local.properties")
        if (localProperties.exists()) {
            val properties = Properties()
            localProperties.inputStream().use { properties.load(it) }
            val storeFileName = properties.getProperty("STORE_FILE_NAME")?.trim().orEmpty()
            val keystorePassword = properties.getProperty("KEYSTORE_PASSWORD")?.trim().orEmpty()
            val storeAlias = properties.getProperty("STORE_ALIAS")?.trim().orEmpty()
            val keyPasswordValue = properties.getProperty("KEY_PASSWORD")?.trim().orEmpty()
            val hasCompleteSigningFields =
                storeFileName.isNotEmpty() &&
                        keystorePassword.isNotEmpty() &&
                        storeAlias.isNotEmpty() &&
                        keyPasswordValue.isNotEmpty()
            val releaseStoreFile = if (hasCompleteSigningFields) {
                file(storeFileName)
            } else {
                null
            }
            if (releaseStoreFile?.exists() == true) {
                register("release") {
                    storeFile = releaseStoreFile
                    storePassword = keystorePassword
                    keyAlias = storeAlias
                    keyPassword = keyPasswordValue
                    enableV1Signing = false
                    enableV2Signing = false
                    enableV3Signing = true
                    enableV4Signing = false
                }
            }
        }
    }
    testOptions {
        // JVM 单测直接调用 AutomationEngine 等生产代码，其中 android.util.Log 需返回默认值而非抛 "not mocked"。
        unitTests.isReturnDefaultValues = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = false
        lintConfig = file("lint.xml")
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
        aidl = true
    }
    androidResources {
        localeFilters += listOf("en", "zh-rCN", "zh-rTW")
    }
    packaging {
        dex {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/**/LICENSE*"
            excludes += "/META-INF/**/NOTICE*"
            excludes += "/cncity_fulllist.txt"
            excludes += "/okhttp3/internal/publicsuffix/*"
            // houbb 拼音裁剪：App 只用 PinyinHelper.toPinyinList(char, NORMAL) 单字查询，
            // 实测仅读 pinyin_dict_char.txt（+ 空的 _define 占位）。短语/分词词典在单字
            // 路径上永不加载（JVM 对照验证：裁剪前后 16 字读音含多音字完全一致）。
            // 注意：pinyin_dict_char_define.txt 虽为空但会被 getCharMap() 主动读取，必须保留。
            excludes += "/pinyin_dict_phrase.txt"
            excludes += "/nlp/word_freq_dict.txt"
            excludes += "/nlp/chinese_ts_char.txt"
        }
    }
}

configurations.all {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
}

// Compose 编译器度量：默认不产出，只有显式 -PlunoComposeMetrics=true 时才写报告。
// 用法：./gradlew :app:assembleDebug -PlunoComposeMetrics=true
// 产物：app/build/compose-metrics/{*-composables.txt, *-classes.txt, *-reports.csv}
// 读法：composables.txt 里看 ResultRow 是否 restartable skippable、参数里是否还有 unstable。
composeCompiler {
    if (providers.gradleProperty("lunoComposeMetrics").orNull == "true") {
        val out = layout.buildDirectory.dir("compose-metrics")
        metricsDestination.set(out)
        reportsDestination.set(out)
        includeSourceInformation.set(true)
    }
}

dependencies {
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.runtime.saveable)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.geometry)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.unit)
    implementation(libs.androidx.compose.ui.util)
    // 版本钉住：material3 1.4.0 会把 material-ripple 拖到 1.8.1，显式声明拉到 composeTrain。
    implementation(libs.androidx.compose.material.ripple)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.com.aaronzzx.fastcompose.compose)
    implementation(libs.com.aaronzzx.fastcompose.compose.accessibility)
    // App.attachBaseContext 里 Reflection.unseal() 解封隐藏 API，Shizuku 反射调用要用。
    implementation(libs.com.tiann.freereflection)
    implementation(libs.jetbrains.kotlin.serialization)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.collection)
    implementation(libs.material3)
    implementation(libs.haze)
    // color-picker removed; replaced with in-app HsvRectPicker
    implementation(libs.material.icons.extended)
    implementation(libs.houbb.pinyin)
    testImplementation("junit:junit:4.13.2")
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)
    implementation(libs.hiddenapibypass)
}
