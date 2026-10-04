// The floor Gradle cannot build the plugin, so unlike the other cells this
// one consumes its PUBLISHED artifacts from the build-local repo filled by
// `../../gradlew :plugin:publishAllPublicationsToChecksRepository`.
// `exclusiveContent` makes a missing local publish fail resolution instead
// of silently testing the Plugin Portal's release of the same version.
pluginManagement {
    repositories {
        exclusiveContent {
            forRepository {
                maven { url = uri("../../fluxo-bcv-js/build/checks-repo") }
            }
            filter { includeGroupByRegex("io\\.github\\.fluxo-kt.*") }
        }
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.develocity") version "4.6.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "check-js-only"
