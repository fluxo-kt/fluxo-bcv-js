pluginManagement {
    repositories {
        // Google/Firebase/GMS/Androidx libraries
        // Don't use exclusiveContent for androidx libraries so that snapshots work.
        google {
            content {
                includeGroupByRegex("android.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        // For Gradle plugins only. Last because proxies to mavenCentral.
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.enterprise") version "3.16.2"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Google/Firebase/GMS/Androidx libraries
        // Don't use exclusiveContent for androidx libraries so that snapshots work.
        google {
            content {
                includeGroupByRegex("android.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "fluxo-bcv-js"

":fluxo-bcv-js".let {
    include(it)
    project(it).name = "plugin"
}
