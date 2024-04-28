pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.enterprise") version "3.16.1"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "fluxo-bcv-js"

":fluxo-bcv-js".let {
    include(it)
    project(it).name = "plugin"
}
