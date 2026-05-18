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
    // `com.gradle.enterprise` still works on Gradle 9.5.1 but emits a
    // "incompatible with Gradle 10" deprecation. v4.x is the canonical
    // line going forward; minimum Gradle is 5.x so our floor (Gradle
    // 8.6 in checks/js-only) is unaffected.
    id("com.gradle.develocity") version "4.4.1"
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
