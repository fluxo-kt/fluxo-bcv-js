
@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    kotlin("multiplatform") version "1.8.21"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.13.1"
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
