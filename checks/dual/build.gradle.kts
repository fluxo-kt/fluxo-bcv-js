// Dual-mode coexistence smoke: BOTH external KotlinX BCV AND
// KGP-embedded `abiValidation` are active. This is the migration-
// window cell — what a 1.0.x user looks like the day they enable
// embedded validation without removing the external plugin.
//
// Exercises the path-selection contract by reading
// `-PpreferEmbedded={true|false|auto}` from the command line and
// forwarding it into `fluxoBcvTs { preferEmbedded.set(...) }`. `sweep`
// runs apiCheck once per preference and asserts the lifecycle line and
// the baseline dir: `api/` (external, BCV's COMMON layout) or
// `api/ts/` + `api/wasmTs/` (embedded, per-target). Both sets hold
// byte-identical baselines; only their location differs.

// Newest STABLE Kotlin (`kotlin`, the build-side version): latest/kgp-only
// already cover the preview, so this keeps a consumer cell on the release
// most users actually run.
plugins {
    kotlin("multiplatform") version libs.versions.kotlin
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version libs.versions.bcvLatest
    id("io.github.fluxo-kt.binary-compatibility-validator-js")
}

// Kotlin 2.4-RC API drift: `abiValidation` no longer exposes `.enabled`
// (see `CompatibilityUtils.kt` task-based detection). An empty block
// is the activation idiom; presence in the build script + KGP-created
// `:checkKotlinAbi` task is what makes our shim report Enabled.
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

// Path-selection knob — defaults to AUTO (Property unset = null = AUTO
// branch in the trigger). CI overrides via `-PpreferEmbedded=true|false`
// to exercise the explicit branches.
fluxoBcvTs {
    when (project.findProperty("preferEmbedded")?.toString()) {
        "true" -> preferEmbedded.set(true)
        "false" -> preferEmbedded.set(false)
        // null / "auto" / "" → leave unset (AUTO).
        else -> { }
    }
}

// See `checks/latest/build.gradle.kts` for the `publishing.onlyIf { false }`
// rationale — Develocity 4.x otherwise auto-publishes every build's scan
// to public URLs.
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
