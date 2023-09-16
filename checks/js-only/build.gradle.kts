@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    kotlin("js") version libs.versions.kotlinMin
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version libs.versions.bcvMin
    id("io.github.fluxo-kt.binary-compatibility-validator-js")
    alias(libs.plugins.deps.guard)
}

kotlin {
    js(IR) {
        binaries.executable()
        nodejs()

        compilations.all {
            kotlinOptions.freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
        }
    }
}

if (hasProperty("buildScan")) {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

dependencyGuard {
    configuration("classpath")
}
