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
    // Gradle Enterprise was renamed to Develocity; the legacy id
    // `com.gradle.enterprise` emits an "incompatible with Gradle 10"
    // deprecation on Gradle 9. v4.x is the canonical line going forward;
    // its minimum Gradle is 5.x, so the checks/js-only floor is unaffected.
    id("com.gradle.develocity") version "4.6.0"
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
