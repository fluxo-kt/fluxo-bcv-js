@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    kotlin("js") version libs.versions.kotlinMin
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version libs.versions.bcvMin
    id("io.github.fluxo-kt.binary-compatibility-validator-js") version libs.versions.fluxoBcvJs
    alias(libs.plugins.deps.guard)
}

kotlin {
    js(IR) {
        binaries.executable()
        nodejs()
    }
}

// Proves a consumer build script compiles against the plugin's extension on the
// Gradle floor. Only build scripts: Gradle 7.6 compiles them without the Kotlin
// metadata version check, while a `kotlin-dsl` convention plugin on Gradle < 8.4
// rejects the extension's metadata 2.0 (see README "Compatibility").
fluxoBcvTs {
    wireToKgpAbi.set(false)
}

// Develocity 4.x DSL — see checks/latest/build.gradle.kts.
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

// A green `check` must mean the `.d.ts` baselines were compared: name the plugin's
// tasks explicitly so a silently skipped target fails task-graph resolution
// ("Task with path '…' not found") instead of passing on nothing.
tasks.named("check") { dependsOn("tsApiCheck") }
