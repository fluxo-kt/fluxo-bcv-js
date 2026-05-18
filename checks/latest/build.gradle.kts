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

// Develocity 4.x: `buildScan` lives under `develocity { }`,
// `termsOfService*` renamed `termsOfUse*` (lazy `Property<String>`
// — must use `.set(...)`, not direct assignment). Terms are accepted
// unconditionally so the plugin doesn't complain on every build.
// `publishing.onlyIf { false }` restores the Gradle Enterprise 3.x
// opt-in behaviour: scans publish only when explicitly requested via
// `--scan` or the `buildScanPublishPrevious` task. Without this gate,
// Develocity 4.x auto-publishes on EVERY build — leaking task graphs,
// timings, and dependency info to public scan URLs.
develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
        publishing.onlyIf { false }
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
