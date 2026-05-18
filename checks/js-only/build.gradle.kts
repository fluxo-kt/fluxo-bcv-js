@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    kotlin("js") version libs.versions.kotlinMin
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version libs.versions.bcvMin
    id("io.github.fluxo-kt.binary-compatibility-validator-js")
    alias(libs.plugins.deps.guard)
}

kotlin {
    js(IR) {
        binaries.executable()
        nodejs()
    }
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
