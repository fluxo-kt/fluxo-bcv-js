// Matrix-interior smoke: Kotlin 2.2.21 (a 2.x stable line below the
// ceiling at `checks/latest`) + BCV 0.16.3 (first BCV that introduced
// the worker-isolation classpath boundary at #208/#256/#258). Catches
// drift between the matrix endpoints — between the floor
// (`checks/js-only`: Kotlin 1.7.22 + BCV 0.8.0) and the ceiling
// (`checks/latest`: newest Kotlin + BCV). Without this cell,
// regressions in the Kotlin 2.0→2.4 metadata rename or the BCV 0.14→0.16
// reflective task-surface change would only surface on the ceiling, by
// which point a bisect would have to cross multiple commits.

plugins {
    kotlin("multiplatform") version libs.versions.kotlinMiddle
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version libs.versions.bcvMiddle
    id("io.github.fluxo-kt.binary-compatibility-validator-js")
}

// `ExperimentalWasmDsl` lived in
// `org.jetbrains.kotlin.gradle.targets.js.dsl` until Kotlin 2.4 moved
// it to `org.jetbrains.kotlin.gradle`. Use the pre-2.4 path here.
@OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
kotlin {
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

// The `-PbcvVersion=0.14.0` lane row tests the `jvmApiCheckTsCompatCleaner`: BCV ≤ 0.14
// fails its check when `build/api` holds anything but the `.api` file, and a `.d.ts`
// left there by an earlier `tsApiBuild` survives an UP-TO-DATE `jvmApiBuild`. That
// BCV's kotlinx-metadata cannot read Kotlin 2 class metadata, hence 1.9 output; Kotlin
// 2.3+ no longer accepts language version 1.9, so the row lives in this 2.2 cell.
if (hasProperty("bcvVersion")) {
    kotlin.compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
    }
}

// The `-PembeddedRow` lane row tests embedded mode on KGP 2.2/2.3, where validation is
// switched on by `enabled` and `referenceDumpDir` lives under `legacyDump` (2.4 moved
// both). Only embedded mode puts `.d.ts` baselines in `abi/ts`, so the row asserts that
// path in the `tsApiDump` description. `preferEmbedded` stays unset: with both
// validators active the default must pick embedded on the oldest KGP that has it.
val embeddedRow = hasProperty("embeddedRow")
val embeddedDumpDir = layout.projectDirectory.dir("abi")
if (embeddedRow) {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    kotlin.abiValidation {
        enabled.set(true)
        legacyDump { referenceDumpDir.set(embeddedDumpDir) }
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
