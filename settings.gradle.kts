pluginManagement {
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
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
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
include(":feature:execute")
include(":feature:actions")
include(":feature:conditions")
include(":feature:automation")
include(":feature:link")
include(":ui:design")
include(":capability:launch")
include(":capability:ocr")
include(":feature:search")
include(":feature:quicklaunch")
include(":feature:gesture")
include(":feature:floatball")
include(":feature:gridpanel")
 