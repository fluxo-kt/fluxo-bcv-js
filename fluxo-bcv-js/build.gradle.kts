plugins {
    alias(libs.plugins.kotlin.jvm)
}

val pluginId = "io.github.fluxo-kt.binary-compatibility-validator-js"
val pluginVersion = libs.versions.fluxoBcvJs.get()

group = "io.github.fluxo-kt"
description = "TypeScript API support for KotlinX Binary Compatibility Validator" +
    " (JS, WASM targets)." +
    "\nAllows dumping TypeScript definitions of a JS or WASM part" +
    " of a Kotlin multiplatform library" +
    " that's public in the sense of npm package visibility," +
    " and ensures that the public definitions haven’t been changed in a way" +
    " that makes this change binary incompatible."

fkcSetupGradlePlugin(
    pluginId = pluginId,
    pluginName = "fluxo-bcv-ts",
    pluginClass = "fluxo.bcvts.FluxoBcvTsPlugin",
    displayName = "Fluxo BCV TS",
    tags = listOf(
        "kotlin",
        "kotlin-multiplatform",
        "kotlin-js",
        "api-management",
        "binary-compatibility",
        "javascript",
        "typescript",
    ),
    kotlin = {
        compilerOptions.freeCompilerArgs.add("-Xskip-metadata-version-check")
    },
) {
    githubProject = "fluxo-kt/fluxo-bcv-js"
    useJdkRelease = false
    setupCoroutines = false
    allWarningsAsErrors = false
    experimentalLatestCompilation = true

    publicationConfig {
        version = pluginVersion
        developerId = "amal"
        developerName = "Artyom Shendrik"
        developerEmail = "artyom.shendrik@gmail.com"
    }

    apiValidation {
        nonPublicMarkers.add("kotlin.jvm.JvmSynthetic")
        // sealed classes constructors are not actually public
        ignoredClasses.add("kotlin.jvm.internal.DefaultConstructorMarker")
    }
}

configurations.implementation {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-metadata-jvm")
    exclude(group = "org.ow2.asm")
}

dependencies {
    compileOnly(libs.plugin.kotlin)
    compileOnly(libs.plugin.binCompatValidator)
    implementation(libs.diffutils)
}

// Single source of truth for the supported-Kotlin floor: read
// `kotlinMin` from the version catalog and emit a generated
// `internal const val KOTLIN_MIN_VERSION` so source code and the
// matrix can never drift apart.
val generatedKotlinSrc = layout.buildDirectory.dir("generated/sources/kotlinMin/kotlin")
val genKotlinMinVersion by tasks.registering {
    val kotlinMin: Provider<String> = libs.versions.kotlinMin
    val out = generatedKotlinSrc.map { it.file("fluxo/bcvts/KotlinMinVersion.kt") }
    inputs.property("kotlinMin", kotlinMin)
    outputs.file(out)
    doLast {
        out.get().asFile.also { it.parentFile.mkdirs() }.writeText(
            "package fluxo.bcvts\n\n" +
                "internal const val KOTLIN_MIN_VERSION: String = \"${kotlinMin.get()}\"\n",
        )
    }
}
kotlin.sourceSets["main"].kotlin.srcDir(generatedKotlinSrc)
tasks.named("compileKotlin") { dependsOn(genKotlinMinVersion) }
