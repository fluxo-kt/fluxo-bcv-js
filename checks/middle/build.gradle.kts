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
