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

// On Kotlin 2.4+ an empty `abiValidation { }` block switches validation on
// (2.4 removed `.enabled`); KGP then creates `:checkKotlinAbi`, which is what
// the plugin's embedded detection sees.
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
