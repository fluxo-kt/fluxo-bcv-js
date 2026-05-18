// Matrix-interior smoke: Kotlin 2.2.21 (last 2.x stable line before
// the 2.4-RC at `checks/latest`) + BCV 0.16.3 (first BCV that introduced
// the worker-isolation classpath boundary at #208/#256/#258). Catches
// drift between the matrix endpoints — between the floor
// (`checks/js-only`: Kotlin 1.7.22 + BCV 0.8.0) and the ceiling
// (`checks/latest`: Kotlin 2.4.0-RC + BCV 0.18.1). Without this cell,
// regressions in the Kotlin 2.0→2.4 metadata rename or the BCV 0.14→0.16
// reflective task-surface change would only surface on the ceiling, by
// which point a bisect would have to cross multiple commits.

plugins {
    kotlin("multiplatform") version "2.2.21"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.16.3"
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
