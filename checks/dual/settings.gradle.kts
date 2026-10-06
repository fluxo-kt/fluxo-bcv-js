pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    includeBuild("../../")
    // `sweep` swaps in versions no other cell runs: an old BCV (`-PbcvVersion`)
    // and an older Kotlin (`-PkotlinVersion`), for configuration-only checks.
    val versionOverrides = mapOf(
        "org.jetbrains.kotlinx.binary-compatibility-validator" to "bcvVersion",
        "org.jetbrains.kotlin.multiplatform" to "kotlinVersion",
    ).mapNotNull { (id, key) -> providers.gradleProperty(key).orNull?.let { id to it } }.toMap()
    resolutionStrategy.eachPlugin {
        versionOverrides[requested.id.id]?.let { useVersion(it) }
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

rootProject.name = "check-dual"
