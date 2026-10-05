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
    // Library-only binaries: the npm-library setup gets its own lane (middle and
    // dual cover executable-only, latest covers both together).
    js {
        nodejs()
        browser()
        binaries.library()
    }
    wasmJs {
        nodejs()
        browser()
        binaries.library()
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

// A green `check` must mean the `.d.ts` baselines were compared: name the plugin's
// tasks explicitly so a silently skipped target fails task-graph resolution
// ("Task with path '…' not found") instead of passing on nothing.
tasks.named("check") { dependsOn("tsApiCheck", "wasmTsApiCheck") }

// Kotlin/JS yarn-lock copy tasks (restore/store/upgrade) are off: the locks are never
// committed (AGENTS.md: kotlin-js-store), and the `.d.ts` baselines do not depend on
// npm package versions, so the tasks guard nothing here. On, they failed `check` with
// "Lock file was changed" after every Kotlin bump, and on Windows with Kotlin
// 2.5.0-Beta1 the wasm store task failed because yarn wrote no `build/wasm/yarn.lock`.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.LockCopyTask>()
    .configureEach { enabled = false }
