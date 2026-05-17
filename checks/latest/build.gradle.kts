plugins {
    kotlin("multiplatform") version libs.versions.kotlinLatest
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version libs.versions.bcvLatest
    id("io.github.fluxo-kt.binary-compatibility-validator-js")
    alias(libs.plugins.deps.guard)
}

// `ExperimentalWasmDsl` moved package between Kotlin versions:
// pre-2.4: `org.jetbrains.kotlin.gradle.targets.js.dsl`
// 2.4+:    `org.jetbrains.kotlin.gradle`
// The new location subsumes the old via type alias in some 2.x lines,
// but Kotlin 2.4-RC requires the canonical 2.4 path.
@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    jvm()
    linuxX64()
    js {
        nodejs()
        browser()
        binaries.executable()
    }
    wasmJs {
        nodejs()
        browser()
        binaries.executable()
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
