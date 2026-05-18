plugins {
    alias(libs.plugins.kotlin.jvm)
    // Sigstore keyless signing — applied here so it observes every
    // `MavenPublication` registered by `fkcSetupGradlePlugin` (the
    // plugin JAR AND the plugin marker POM). Hooks into `publish*` via
    // the underlying `sigstoreSign*Publication` tasks; locally these
    // require browser-OIDC, in CI they use GitHub Actions OIDC.
    alias(libs.plugins.sigstore.sign)
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

// Workaround for fluxo-kmp-conf 0.14.x:
// `fkcSetupGradlePlugin` invokes `gradlePlugin.plugins.maybeCreate(name)`
// and then runs `if (id.isNullOrBlank()) { id = pluginId }`. Under
// Gradle 8.x/9.x the `maybeCreate` already pre-fills `id` with the
// plugin's NAME, so the guard reads `id="fluxo-bcv-ts"` (non-blank) and
// silently skips overwriting with the real `pluginId`. The resulting
// `META-INF/gradle-plugins/<id>.properties` ships under the wrong name
// and composite-build plugin resolution fails (see `checks/latest`).
// Forcing the id post-hoc restores the public contract without forking
// fluxo-kmp-conf. Upstream issue: TODO file at fluxo-kt/fluxo-kmp-conf.
extensions.getByType(org.gradle.plugin.devel.GradlePluginDevelopmentExtension::class.java)
    .plugins.getByName("fluxo-bcv-ts").id = pluginId

// Wire Sigstore bundle production into the Gradle Plugin Portal
// publication chain. `publishPlugins` (the `com.gradle.plugin-publish`
// task) is NOT a standard `PublishToMavenRepository` subclass, so the
// Sigstore plugin's default `withType<...>` hook misses it. Force the
// dependency explicitly so the upload includes the `.sigstore.bundle`
// siblings for BOTH the plugin JAR and its marker POM. Local
// `publishToMavenLocal` is unaffected — Sigstore tasks run only when
// publishPlugins runs, which only happens in `release.yml` with OIDC
// credentials available. `tasks.matching { }` is lazy and CC-safe;
// `configureEach` ensures the wiring fires regardless of registration
// order between sigstore-sign and gradle-plugin-publish.
tasks.matching { it.name == "publishPlugins" }.configureEach {
    dependsOn(tasks.matching { it.name.startsWith("sigstoreSign") })
}

configurations.implementation {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    // Kotlin 2.0 renamed the metadata-jvm artifact: old coordinate
    // `org.jetbrains.kotlinx:kotlinx-metadata-jvm` was moved into the
    // main `org.jetbrains.kotlin` group. BCV ≤ 0.15 still pulls the
    // legacy coordinate; BCV 0.16+ (#255) pulls the renamed one. Both
    // excludes are required to cover the supported compat matrix
    // (bcvMin = 0.8.0 floor up to bcvLatest).
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-metadata-jvm")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-metadata-jvm")
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
val genKotlinMinVersion by tasks.registering {
    // Capture script-level Providers into local vals BEFORE doLast so
    // configuration-cache serialisation doesn't try to walk back to
    // the Build_gradle script object (per AGENTS.md gotcha).
    val kotlinMin: Provider<String> = libs.versions.kotlinMin
    val outDir: Provider<Directory> =
        project.layout.buildDirectory.dir("generated/sources/kotlinMin/kotlin")
    inputs.property("kotlinMin", kotlinMin)
    outputs.dir(outDir)
    doLast {
        val out = outDir.get().file("fluxo/bcvts/KotlinMinVersion.kt").asFile
        out.parentFile.mkdirs()
        out.writeText(
            "package fluxo.bcvts\n\n" +
                "internal const val KOTLIN_MIN_VERSION: String = \"${kotlinMin.get()}\"\n",
        )
    }
}
// Pass the TaskProvider so Gradle infers the producer relation for
// every Kotlin compile task consuming `main` sources — including
// `compileExperimentalLatestKotlin` from fluxo-kmp-conf's overlay.
kotlin.sourceSets["main"].kotlin.srcDir(genKotlinMinVersion)
