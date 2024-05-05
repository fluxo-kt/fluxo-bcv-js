pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    includeBuild("../../")
}

plugins {
    id("com.gradle.enterprise") version "3.15"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "check-latest"
