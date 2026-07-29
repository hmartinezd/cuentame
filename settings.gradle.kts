pluginManagement {
    includeBuild("build-logic")
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Cuentame"
include(":app")
include(":core:common")
include(":core:model")
include(":core:domain")
include(":core:presentation")
include(":core:designsystem")
include(":core:data")
include(":core:backup")
include(":core:testing")
include(":feature:onboarding")
include(":feature:home")
include(":feature:inventory")
include(":feature:purchases")
include(":feature:counts")
include(":feature:waste")
include(":feature:reports")
include(":feature:settings")

 