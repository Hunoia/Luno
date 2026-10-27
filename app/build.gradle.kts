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
        versionCode = 10608
        versionName = "1.6.8"

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
        // en 暂无对应 res 目录，英文资源后续补；先声明避免以后加 values-en 时遗漏过滤配置。
        localeFilters += listOf("en", "zh-rCN")
    }
    packaging {
        dex {
            // 压缩 dex：下载体积更小、安装后占用更大（有意的体积取舍）。
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/**/LICENSE*"
            excludes += "/META-INF/**/NOTICE*"
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

// kotlin-android-extensions-runtime:1.3.72 仍被某传递依赖拖入（配 exclude 前不下载，缓存里看不到），
// 其 kotlinx.android.parcel.* 类与 kotlin-parcelize-runtime 同名冲突，靠这条 exclude 消解——不能删。
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
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:bridge"))
    implementation(project(":core:data"))
    implementation(project(":core:runtime"))
    implementation(project(":feature:execute"))
    implementation(project(":feature:actions"))
    implementation(project(":feature:conditions"))
    implementation(project(":feature:automation"))
    implementation(project(":feature:link"))
    implementation(project(":ui:design"))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    // 版本钉住：material3 1.4.0 会把 material-ripple 拖到 1.8.1，显式声明拉到 composeTrain。
    implementation(libs.androidx.compose.material.ripple)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    // coil 直接 import：AsyncImage 在 compose 包，imageLoader/ImageLoaderFactory 在单例包，两个都要。
    // 此前全靠 fastcompose 传递带入，显式声明防其变更时断裂。
    implementation(libs.coil)
    implementation(libs.coil.compose)
    // fastcompose 挂名的传递依赖 app 字节码零引用（hilt/accompanist/zoomable），排除收敛解析图；
    // utilcodex（UDFComponentDefaults 基类）与 coil 为 fastcompose 类真实引用，不能排。
    // com.google.android.material 不能排：app 启动图标的 ic_launcher_background res 引用其
    // material_dynamic_* 颜色（字节码虽零引用，但 res 链接需要）。
    // androidx.constraintlayout 不能排：material 的 timepicker layout 引用其 constraint 属性。
    implementation(libs.com.aaronzzx.fastcompose.compose) {
        exclude(group = "androidx.hilt")
        exclude(group = "com.google.dagger")
        exclude(group = "com.google.accompanist")
        exclude(group = "net.engawapg.lib")
    }
    implementation(libs.com.aaronzzx.fastcompose.compose.accessibility) {
        exclude(group = "androidx.hilt")
        exclude(group = "com.google.dagger")
        exclude(group = "com.google.accompanist")
        exclude(group = "net.engawapg.lib")
    }
    // App.attachBaseContext 里 Reflection.unseal() 解封隐藏 API，Shizuku 反射调用要用。
    implementation(libs.com.tiann.freereflection)
    implementation(libs.jetbrains.kotlin.serialization)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.collection)
    implementation(libs.material3)
    implementation(libs.haze)
    implementation(libs.material.icons.extended)
    implementation(libs.houbb.pinyin)
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)
    implementation(libs.hiddenapibypass)
    testImplementation(libs.junit)
}
