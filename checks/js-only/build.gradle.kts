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

// Proves consumer Kotlin DSL compiles against the plugin's extension on the
// Gradle floor (7.6 embeds Kotlin 1.7 and reads Kotlin metadata 1.x only).
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
