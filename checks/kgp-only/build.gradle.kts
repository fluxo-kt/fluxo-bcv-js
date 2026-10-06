// Embedded-mode smoke test: KGP-only ABI validation, NO external BCV.
// Exercises the dual-mode trigger from the embedded side. The
// composite includeBuild("../../") wires this against the plugin
// sources, so a regression in embedded detection or in its per-target
// layout fails here.

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
// Kotlin 2.4 removed the `enabled` property: calling `abiValidation { }`
// itself activates validation. `CompatibilityUtils.kt` reads `enabled` on
// 2.2/2.3 and KGP's ABI tasks on 2.4+.
//
// The dump dir is moved off KGP's default `api/` because the plugin's own
// fallback is also `api/`: only a moved dir shows that embedded mode keeps
// its `.d.ts` baselines in KGP's `referenceDumpDir`.
val abiDumpDir = layout.projectDirectory.dir("abi")

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        referenceDumpDir.set(abiDumpDir)
    }

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

// These cells run no npm: the plugin's `.d.ts` tasks need only the link tasks, and the
// npm toolchain feeds only JS/Wasm test tasks, which these cells lack. Left on, the yarn
// installs (`KotlinNpmInstallTask`, the wasm `KotlinToolingSetupTask`) were most of every
// warm CI lane: yarn re-downloads its packages on each fresh runner, and two installs
// serialise on yarn's machine-wide mutex. `download = false` on the Node.js/Yarn specs
// stops KGP fetching both distributions while configuring (disabling their setup tasks
// does not), which kept ~100 MB per lane in the size-capped CI cache. Binaryen stays on:
// wasm executable linking runs it. The yarn-lock copy tasks (`LockCopyTask`) go too: the
// locks are never committed (AGENTS.md: kotlin-js-store), the store task would read a
// `yarn.lock` no install wrote, and they failed `check` with "Lock file was changed"
// after every Kotlin bump.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.LockCopyTask>()
    .configureEach { enabled = false }
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask>()
    .configureEach { enabled = false }
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinToolingSetupTask>()
    .configureEach { enabled = false }
for (schema in extensions.extensionsSchema) {
    val spec = extensions.getByName(schema.name)
    if (spec is org.jetbrains.kotlin.gradle.targets.web.nodejs.BaseNodeJsEnvSpec) {
        spec.download.set(false)
    }
    if (spec is org.jetbrains.kotlin.gradle.targets.web.yarn.BaseYarnRootEnvSpec) {
        spec.download.set(false)
    }
}
