pluginManagement {
    repositories {
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
include(":feature:search")
include(":feature:quicklaunch")
include(":feature:gesture")
include(":feature:extract")
include(":feature:floatball")
include(":feature:gridpanel")
 