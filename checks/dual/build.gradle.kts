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
// the plugin's embedded detection sees. Kotlin 2.2/2.3 need the `enabled` flag,
// set only by the sweep's Kotlin 2.3 run (`-PabiEnabledFlag`); on 2.4+ its
// getter throws. `-PexternalOnly` leaves the block out, so the sweep can prove a
// project without it is not mistaken for embedded (which would move its baselines).
val abiEnabledFlag = hasProperty("abiEnabledFlag")

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    if (!hasProperty("externalOnly")) {
        @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
        abiValidation {
            if (abiEnabledFlag) {
                @Suppress("DEPRECATION_ERROR")
                enabled.set(true)
            }
        }
    }

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
// Embedded mode must ignore BCV's opt-outs. BCV is switched off exactly when
// embedded mode is asked for, so the `.d.ts` tasks vanish (and the sweep
// fails) if the plugin still obeyed BCV there.
apiValidation {
    validationDisabled = project.findProperty("preferEmbedded")?.toString() == "true"
}

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

// No npm in this cell: see `checks/latest/build.gradle.kts` for why each switch is off.
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
