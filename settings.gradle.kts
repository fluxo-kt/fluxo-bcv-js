pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("com.gradle.enterprise") version "3.16.1"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

logger.lifecycle("> Conf Gradle version is ${gradle.gradleVersion}")
logger.lifecycle("> Conf JRE version is ${System.getProperty("java.version")}")
logger.lifecycle("> Conf CPUs ${Runtime.getRuntime().availableProcessors()}")

rootProject.name = "fluxo-bcv-js"

// On module update, don't forget to update '.github/workflows/deps-submission.yml'!

include(":fluxo-bcv-js")
