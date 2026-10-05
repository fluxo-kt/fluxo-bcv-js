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

// Kotlin/JS yarn-lock copy tasks (restore/store/upgrade) are off: the locks are never
// committed (AGENTS.md: kotlin-js-store), and the `.d.ts` baselines do not depend on
// npm package versions, so the tasks guard nothing here. On, they failed `check` with
// "Lock file was changed" after every Kotlin bump, and on Windows with Kotlin
// 2.5.0-Beta1 the wasm store task failed because yarn wrote no `build/wasm/yarn.lock`.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.LockCopyTask>()
    .configureEach { enabled = false }
