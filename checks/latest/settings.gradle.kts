pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    includeBuild("../../")
}

plugins {
    id("com.gradle.develocity") version "4.4.1"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "check-latest"
