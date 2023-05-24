@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    kotlin("js") version libs.versions.kotlinMin
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version libs.versions.bcvMin
    id("io.github.fluxo-kt.binary-compatibility-validator-js")
}

kotlin {
    js(IR) {
        binaries.executable()
        nodejs()
    }
}

if (hasProperty("buildScan")) {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}
