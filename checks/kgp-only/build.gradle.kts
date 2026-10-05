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

// The Kotlin/JS npm toolchain tasks are off. The yarn installs (`KotlinNpmInstallTask`)
// and the wasm tooling install (`KotlinToolingSetupTask`) feed only the JS/Wasm test
// tasks, and these cells have no tests; the plugin's `.d.ts` tasks need only the link
// tasks. On, the installs were most of every warm CI lane: yarn re-downloads its packages
// on each fresh runner, and two installs serialise on yarn's machine-wide mutex.
// The yarn-lock copy tasks (`LockCopyTask`: restore/store/upgrade) go with them: the locks
// are never committed (AGENTS.md: kotlin-js-store), the store task would read a
// `yarn.lock` no install wrote, and they failed `check` with "Lock file was changed"
// after every Kotlin bump.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.LockCopyTask>()
    .configureEach { enabled = false }
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask>()
    .configureEach { enabled = false }
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinToolingSetupTask>()
    .configureEach { enabled = false }
