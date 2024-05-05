plugins {
    kotlin("multiplatform") version libs.versions.kotlinLatest
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version libs.versions.bcvLatest
    id("io.github.fluxo-kt.binary-compatibility-validator-js")
    alias(libs.plugins.deps.guard)
}

@OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
kotlin {
    jvm()
    linuxX64()
    js {
        nodejs()
        browser()
    }
    wasmJs {
        nodejs()
        browser()
    }
    wasmWasi {
        nodejs()
        binaries.executable()
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

apiValidation {
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}
