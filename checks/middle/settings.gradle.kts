pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    includeBuild("../../")
    // `-PbcvVersion=<v>` swaps the external BCV for a lane row (see build.gradle.kts).
    val bcvId = "org.jetbrains.kotlinx.binary-compatibility-validator"
    val bcvVersion = providers.gradleProperty("bcvVersion").orNull
    resolutionStrategy.eachPlugin {
        if (bcvVersion != null && requested.id.id == bcvId) useVersion(bcvVersion)
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

rootProject.name = "check-middle"
