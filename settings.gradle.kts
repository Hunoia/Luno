pluginManagement {
    includeBuild("build-logic")
    repositories {
        // 国内镜像优先，原仓库兜底
        maven {
            setUrl("https://maven.aliyun.com/repository/google")
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 与 pluginManagement 一致：google 镜像只接 Android/Google/AndroidX 的坐标。
        // 不加 content 过滤时，Gradle 会把 org.jetbrains.kotlin 之类的坐标也 HEAD 到 google 镜像上，
        // 该镜像对不存在的路径常直接掐 TLS，导致整条解析链（如 kotlin-compiler-embeddable）失败。
        maven {
            setUrl("https://maven.aliyun.com/repository/google")
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://maven.aliyun.com/repository/public")
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "Luno"
include(":app")
include(":core:common")
include(":core:domain")
include(":core:bridge")
include(":core:data")
include(":core:runtime")
include(":core:effect")
include(":core:facade")
include(":feature:execute")
include(":feature:actions")
include(":feature:link")
include(":ui:design")
include(":capability:launch")
include(":capability:notify")
include(":capability:ocr")
include(":capability:qr")
include(":capability:appcatalog")
include(":feature:search")
include(":feature:quickaccess")
include(":feature:picker")
include(":feature:gesture")
include(":feature:floatball")
include(":feature:gridpanel")
include(":feature:widget")
include(":feature:annotate")
include(":feature:appcontrol")
include(":feature:clipboard")
 