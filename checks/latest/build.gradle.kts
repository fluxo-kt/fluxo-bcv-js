@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    kotlin("multiplatform") version libs.versions.kotlinLatest
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version libs.versions.bcvLatest
    id("io.github.fluxo-kt.binary-compatibility-validator-js")
}

kotlin {
    jvm()
    js(IR) {
        binaries.executable()
        nodejs()
        generateTypeScriptDefinitions()
    }
}
