// Embedded-mode smoke test: KGP-only ABI validation, NO external BCV.
// Exercises the new dual-mode trigger from the embedded side. The
// composite includeBuild("../../") wires this against the plugin
// sources, so a regression in the embedded trigger fails the floor
// of the dual-mode contract (commit 21 reflective shim, commit 23
// DirConfig short-circuit).

plugins {
    kotlin("multiplatform") version libs.versions.kotlinLatest
    // NO `org.jetbrains.kotlinx.binary-compatibility-validator` —
    // that's the whole point of this smoke cell. Only KGP-embedded
    // `abiValidation { }` is configured below.
    id("io.github.fluxo-kt.binary-compatibility-validator-js")
}

// KGP 2.3+: `abiValidation` extension exists but is gated by
// `@OptIn(ExperimentalAbiValidation::class)` until JetBrains commits
// to stability. The opt-in is consumer-side — applied at the call
// site, not inherited from our plugin's metadata.
//
// Kotlin 2.4-RC API drift: the `enabled: Property<Boolean>` property
// was deprecated and removed; calling `abiValidation { }` itself now
// activates validation. This block intentionally passes no body —
// activation is the call itself. The reflective shim in
// `CompatibilityUtils.kt` bridges both shapes (2.2/2.3 reads
// `.enabled`; 2.4+ treats reachable extension as enabled).
@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation { }

    jvm()
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
}

// See `checks/latest/build.gradle.kts` for the rationale on
// `publishing.onlyIf { false }` — Develocity 4.x otherwise auto-
// publishes every build's scan to public URLs.
develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
        publishing.onlyIf { false }
    }
}
