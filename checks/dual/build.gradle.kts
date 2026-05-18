// Dual-mode coexistence smoke: BOTH external KotlinX BCV AND
// KGP-embedded `abiValidation` are active. This is the migration-
// window cell — what a 1.0.x user looks like the day they enable
// embedded validation without removing the external plugin.
//
// Exercises the path-selection contract by reading
// `-PpreferEmbedded={true|false|auto}` from the command line and
// forwarding it into `fluxoBcvTs { preferEmbedded.set(...) }`. CI
// invokes apiCheck three times (once per preference) and greps the
// lifecycle observable. Baseline `.d.ts` byte content is identical
// across all three sub-cases — the *path* is observable, the *output*
// is not.

plugins {
    kotlin("multiplatform") version libs.versions.kotlinLatest
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
