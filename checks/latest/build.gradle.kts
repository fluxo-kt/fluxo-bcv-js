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
    linuxX64()
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

// Kotlin/JS yarn-lock copy tasks (restore/store/upgrade) are off: the locks are never
// committed (AGENTS.md: kotlin-js-store), and the `.d.ts` baselines do not depend on
// npm package versions, so the tasks guard nothing here. On, they failed `check` with
// "Lock file was changed" after every Kotlin bump, and on Windows with Kotlin
// 2.5.0-Beta1 the wasm store task failed because yarn wrote no `build/wasm/yarn.lock`.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.LockCopyTask>()
    .configureEach { enabled = false }
