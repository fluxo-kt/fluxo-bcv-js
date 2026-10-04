pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    includeBuild("../../")
}

plugins {
    id("com.gradle.develocity") version "4.6.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "check-dual"
