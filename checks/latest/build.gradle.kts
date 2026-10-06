plugins {
    kotlin("multiplatform") version libs.versions.kotlinLatest
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version libs.versions.bcvLatest
    id("io.github.fluxo-kt.binary-compatibility-validator-js")
    alias(libs.plugins.deps.guard)
}

// `ExperimentalWasmDsl` moved package between Kotlin versions:
// pre-2.4: `org.jetbrains.kotlin.gradle.targets.js.dsl`
// 2.4+:    `org.jetbrains.kotlin.gradle`
// The new location subsumes the old via type alias in some 2.x lines,
// but Kotlin 2.4-RC requires the canonical 2.4 path.
@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    jvm()
    // No native target: the plugin skips non-JS/Wasm targets (jvm and wasmWasi cover
    // that), and BCV's KLIB dump already spans js/wasmJs/wasmWasi. A native target only
    // made every CI run download the Kotlin/Native toolchain (~/.konan is not cached),
    // most slowly on Windows.
    // The build script enables TS generation itself, as real consumers do
    // (e.g. fluxo). KGP's `generateTypeScriptDefinitions()` is not idempotent,
    // so this cell covers the plugin's second call on an already-enabled target.
    // Library + executable together: the plugin must link only the library.
    js {
        nodejs()
        browser()
        binaries.library()
        binaries.executable()
        generateTypeScriptDefinitions()
    }
    wasmJs {
        nodejs()
        browser()
        binaries.library()
        binaries.executable()
        generateTypeScriptDefinitions()
    }
    wasmWasi {
        nodejs()
        binaries.executable()
    }
}

// Develocity 4.x: `buildScan` lives under `develocity { }`,
// `termsOfService*` renamed `termsOfUse*` (lazy `Property<String>`
// — must use `.set(...)`, not direct assignment). Terms are accepted
// unconditionally so the plugin doesn't complain on every build.
// `publishing.onlyIf { false }` restores the Gradle Enterprise 3.x
// opt-in behaviour: scans publish only when explicitly requested via
// `--scan` or the `buildScanPublishPrevious` task. Without this gate,
// Develocity 4.x auto-publishes on EVERY build — leaking task graphs,
// timings, and dependency info to public scan URLs.
develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
        publishing.onlyIf { false }
    }
}

dependencyGuard {
    configuration("classpath")
}

apiValidation {
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}

// A green `check` must mean the `.d.ts` baselines were compared: name the plugin's
// tasks explicitly so a silently skipped target fails task-graph resolution
// ("Task with path '…' not found") instead of passing on nothing.
tasks.named("check") { dependsOn("tsApiCheck", "wasmTsApiCheck") }

// Library first: with both binaries declared, the `.d.ts` tasks must link only the
// library. A wrong pick passes every baseline (both emit the same declarations),
// so check the link tasks the `.d.ts` build tasks depend on.
gradle.taskGraph.whenReady {
    val graph = this
    for (task in graph.allTasks.filter { it.name == "tsApiBuild" || it.name == "wasmTsApiBuild" }) {
        val links = graph.getDependencies(task).map { it.name }
            .filter { it.startsWith("compileProduction") }
        check(links.isNotEmpty() && links.all { it.startsWith("compileProductionLibrary") }) {
            "${task.name} must link only the library binary, but depends on $links"
        }
    }
}

// These cells run no npm: the plugin's `.d.ts` tasks need only the link tasks, and the
// npm toolchain feeds only JS/Wasm test tasks, which these cells lack. Left on, the yarn
// installs (`KotlinNpmInstallTask`, the wasm `KotlinToolingSetupTask`) dominated warm CI
// lanes: yarn re-downloads its packages on each fresh runner, and two installs serialise
// on yarn's machine-wide mutex. `download = false` on the Node.js/Yarn specs stops KGP
// fetching both distributions while configuring (disabling their setup tasks does not),
// which keeps them out of the size-capped CI cache. Binaryen stays on:
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
