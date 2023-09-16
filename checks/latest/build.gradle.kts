plugins {
    kotlin("multiplatform") version libs.versions.kotlinLatest
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version libs.versions.bcvLatest
    id("io.github.fluxo-kt.binary-compatibility-validator-js")
    alias(libs.plugins.deps.guard)
}

kotlin {
    jvm {
        compilations.all {
            "17".let { jvmTarget ->
                kotlinOptions.jvmTarget = jvmTarget
                compileJavaTaskProvider?.configure {
                    sourceCompatibility = jvmTarget
                    targetCompatibility = jvmTarget
                }
            }
        }

        val main by compilations.getting {}

        compilations.create("experimentalTest") {
            kotlinOptions {
                languageVersion = "2.1"
                apiVersion = "2.1"

            }

            defaultSourceSet.dependsOn(main.defaultSourceSet)

            tasks.named("check") {
                dependsOn(compileTaskProvider)
            }
        }
    }
    js(IR) {
        binaries.executable()
        nodejs()
        browser()

        compilations.all {
            kotlinOptions {
                languageVersion = "2.1"
                apiVersion = "2.1"
            }
        }
    }

    targets.all {
        compilations.all {
            kotlinOptions.freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
        }
    }
}

if (hasProperty("buildScan")) {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

dependencyGuard {
    configuration("classpath")
}
