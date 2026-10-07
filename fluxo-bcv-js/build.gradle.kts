import org.gradle.plugin.compatibility.compatibility

plugins {
    alias(libs.plugins.kotlin.jvm)
    // Sigstore keyless signing — applied here so it observes every
    // `MavenPublication` registered by `fkcSetupGradlePlugin` (the
    // plugin JAR AND the plugin marker POM). Hooks into `publish*` via
    // the underlying `sigstoreSign*Publication` tasks. Those tasks are
    // RELEASE-only gated below (see the `onlyIf` block near the bottom
    // of this file) so local `publishToMavenLocal` doesn't block on
    // browser-OIDC; in CI they use GitHub Actions OIDC.
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
    // `-Xjdk-release=1.8`: the compiler rejects JDK 9+ API calls, which would
    // otherwise compile fine on our JDK 21 and crash on consumers' Java 8-era
    // runtime paths.
    // No equivalent compile-time guard exists for the Kotlin stdlib: it comes
    // from `gradleApi()` (the BUILDING Gradle's embedded stdlib, 2.x), not from
    // a resolvable dependency, so it cannot be pinned to Gradle 7.6's 1.7.10.
    // Stdlib APIs newer than 1.7.10 are caught only by `checks/js-only`,
    // which runs the published jar on Gradle 7.6.
    useJdkRelease = true
    // fluxo-kmp-conf 0.13+ defaults both to false: without them `check` runs
    // neither Detekt (`detekt.yml`, `detekt-baseline.xml`) nor Android Lint
    // for JVM modules, and the CI SARIF uploads stay empty.
    setupVerification = true
    enableGenericAndroidLint = true
    setupCoroutines = false
    allWarningsAsErrors = false
    // Test-only flag (fluxo-kmp-conf `KotlinConfigSetup.kt`): gates the
    // `LATEST_KOTLIN_LANG_VERSION` overlay for `compileTestKotlin`,
    // NOT main compilation of the published JAR. Plugin has no tests
    // today, so the flag is a no-op now; disabled to defang the trap
    // that adding tests later would inherit RC-flavoured language
    // version by default.
    experimentalLatestCompilation = false

    publicationConfig {
        version = pluginVersion
        developerId = "amal"
        developerName = "Artyom Shendrik"
        developerEmail = "artyom.shendrik@gmail.com"
        inceptionYear = "2023"
        // This block alone turns on fluxo-kmp-conf's maven-publish setup
        // (0.16+, no Vanniktech): artifactId `fluxo-bcv-ts` (the plugin
        // name), the marker version, POM url/licence/developer/scm and
        // `gradlePlugin.{website,vcsUrl}` all derive from `githubProject`
        // and the values here. A non-file publish (incl. `publishPlugins`)
        // refuses to run without the SIGNING_KEY PGP key; `release.yml`
        // passes it.
    }

    apiValidation {
        nonPublicMarkers.add("kotlin.jvm.JvmSynthetic")
        // sealed classes constructors are not actually public
        ignoredClasses.add("kotlin.jvm.internal.DefaultConstructorMarker")
    }
}

// fluxo-kmp-conf sets the POM `<name>` to the artifactId; keep the human
// name that 1.0.x-1.1.x published. `projectName` would rename the artifact.
publishing.publications.withType<MavenPublication>().configureEach {
    pom { name.set("Fluxo BCV TS") }
}

// Build-local Maven repo that `checks/js-only` resolves this plugin from.
// The floor cell runs the oldest supported Gradle, which cannot BUILD this
// plugin (KGP 2.x and plugin-publish 2.x need newer Gradle), so
// it cannot `includeBuild` the sources like the other cells. Consuming the
// published marker + `.module` + jar instead also tests the published
// coordinates end to end. Filled by `publishAllPublicationsToChecksRepository`.
publishing.repositories.maven {
    name = "checks"
    url = uri(layout.buildDirectory.dir("checks-repo"))
}

val pluginExt = extensions
    .getByType(org.gradle.plugin.devel.GradlePluginDevelopmentExtension::class.java)

// Plugin Portal feature-compatibility declaration (DSL from Gradle's
// compatibility-plugin, applied by plugin-publish 2.2+).
// - configurationCache: every check cell runs under strict CC
//   (checks/gradle.properties: problems=fail, max-problems=0).
// - isolatedProjects: NOT supported; the plugin walks parent projects for the
//   BCV extension and reads other projects' state.
pluginExt.plugins.named("fluxo-bcv-ts") {
    compatibility {
        features {
            configurationCache.set(true)
            isolatedProjects.set(false)
        }
    }
}

// Pre-flight Plugin Portal metadata gate. Sibling-aligned with
// fluxo-kmp-conf's `VerifyPluginPortalMetadataTask` (its
// `build.gradle.kts:617-640`). Eliminates the class of regression
// where a fluxo-kmp-conf API gap or upstream surface change leaves
// a publish-time required field blank — historically (1.1.0 release
// attempt #1) `gradlePlugin.{website,vcsUrl}` were left null by
// fluxo-kmp-conf 0.14+, surfacing only inside `release.yml`'s
// `publishPlugins` execution AFTER the signed tag had been pushed.
// The fields are plugin-publish 2.x's runtime validation plus
// `project.version` (the marker POM's version). Failing this task is a hard build-gate
// for both `:check` (PR/push) and `:publishPlugins` (release).
// Reuses `pluginExt` declared above (single extension lookup).
val pluginDecl = pluginExt.plugins.named("fluxo-bcv-ts")
val verifyPluginPortalMetadata = tasks.register("verifyPluginPortalMetadata") {
    group = "verification"
    description = "Pre-flight Plugin Portal metadata gate."

    // Lazy Provider captures — Gradle CC serializes them via
    // `inputs.property`. No eager reads of extensions/project at
    // config-time, no `project` capture inside `doLast`.
    // Scope matches sibling's `VerifyPluginPortalMetadataTask`:
    // `gradlePlugin` + `PluginDeclaration` only. POM/artifactId checks
    // skipped because `MavenPublication` ('pluginMaven') is registered
    // by `java-gradle-plugin` AFTER script eval, so an eager `named()`
    // throws here. The artifactId is gated elsewhere: `checks/js-only`
    // resolves the published marker, and its dependency-guard baseline
    // (`dependencies/classpath.txt`) names `fluxo-bcv-ts`.
    val website = pluginExt.website.orElse("")
    val vcsUrl = pluginExt.vcsUrl.orElse("")
    val actualId = pluginDecl.map { it.id.orEmpty() }
    val displayName = pluginDecl.map { it.displayName.orEmpty() }
    val description = pluginDecl.map { it.description.orEmpty() }
    val implClass = pluginDecl.map { it.implementationClass.orEmpty() }
    val tags = pluginDecl.flatMap { it.tags }
    val versionProv = project.provider { project.version.toString() }
    val expectedId = pluginId

    inputs.property("website", website)
    inputs.property("vcsUrl", vcsUrl)
    inputs.property("actualId", actualId)
    inputs.property("displayName", displayName)
    inputs.property("description", description)
    inputs.property("implClass", implClass)
    inputs.property("tags", tags)
    inputs.property("version", versionProv)
    inputs.property("expectedId", expectedId)
    // Verification is never up-to-date — semantics demand re-check.
    outputs.upToDateWhen { false }

    doLast {
        val errors = mutableListOf<String>()
        fun req(field: String, value: String) {
            if (value.isBlank() || value == "unspecified") {
                errors += "$field is blank/unspecified"
            }
        }
        req("gradlePlugin.website", website.get())
        req("gradlePlugin.vcsUrl", vcsUrl.get())
        val actual = actualId.get()
        if (actual != expectedId) errors += "plugin.id='$actual', expected='$expectedId'"
        req("plugin.displayName", displayName.get())
        req("plugin.description", description.get())
        req("plugin.implementationClass", implClass.get())
        if (tags.get().isEmpty()) errors += "plugin.tags is empty"
        req("project.version", versionProv.get())
        if (errors.isNotEmpty()) {
            throw GradleException(
                "Plugin Portal metadata validation failed:\n" +
                    errors.joinToString("\n") { "  - $it" } +
                    "\nFix in fluxo-bcv-js/build.gradle.kts or upstream " +
                    "fluxo-kmp-conf integration; see AGENTS.md > " +
                    "\"Surprises & gotchas\" (fluxo-kmp-conf publication setup).",
            )
        }
    }
}

// Wire as a dependency of every publish-side task so regressions fail
// fast (before network) AND of `:check` so PR/push CI gates it. Without
// the `:check` wiring, the class-of-error stays release-time-only.
tasks.matching { it.name == "publishPlugins" }.configureEach {
    dependsOn(verifyPluginPortalMetadata)
    dependsOn(tasks.matching { it.name.startsWith("sigstoreSign") })
}
tasks.matching { it.name.startsWith("sigstoreSign") }.configureEach {
    dependsOn(verifyPluginPortalMetadata)
}
tasks.named("check") { dependsOn(verifyPluginPortalMetadata) }

// Named so `check` fails ("Task with path 'detektMain' not found") when a
// fluxo-kmp-conf change stops creating Detekt or Lint tasks; it once turned
// both off by default and `check` stayed green while running neither.
tasks.named("check") { dependsOn("detektMain", "lint") }

// The shipped classes must stay class-file major 52 (Java 8): Gradle 7.6, the supported
// floor, still runs on Java 8, and no lane does. fluxo-kmp-conf applies `javaLangTarget`
// (version catalog) and has changed defaults silently before; `useJdkRelease` does not
// stop a raised target (`javaLangTarget = "11"` compiles cleanly with it on).
// A build-cache hit skips this action, but a changed target changes the cache key.
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    val classesDir = destinationDirectory
    doLast {
        val raised = classesDir.get().asFileTree.matching { include("**/*.class") }.filter { f ->
            f.inputStream().use { it.skip(6); (it.read() shl 8) or it.read() } != 52
        }.files
        check(raised.isEmpty()) {
            "Class files above Java 8 (major 52): $raised. " +
                "Keep `javaLangTarget = 1.8` in gradle/libs.versions.toml."
        }
    }
}

// Gate Sigstore signing on the `RELEASE` env var so it fires ONLY in
// `release.yml` (which already sets `RELEASE: true`). The Sigstore
// plugin auto-wires its `sigstoreSign*Publication` tasks into every
// `MavenPublication`'s publish chain — without this gate, a developer
// running `./gradlew :plugin:publishToMavenLocal` would block on a
// browser-OIDC prompt at `oauth2.sigstore.dev/auth/...`. To force-test
// the Sigstore path locally, run with `RELEASE=true ./gradlew ...`
// (opt-in; the developer accepts the OIDC ceremony).
//
// CC: the provider is captured in a block-local val. A lambda here that
// reads `providers` (or any script-level val) captures the build-script
// object and, through it, the Project, so strict CC refuses to store the
// sign tasks; do not hide that behind `notCompatibleWithConfigurationCache`.
// Verify with `RELEASE=true ./gradlew
// :plugin:publishAllPublicationsToChecksRepository --dry-run`.
tasks.matching { it.name.startsWith("sigstoreSign") }.configureEach {
    val release = providers.environmentVariable("RELEASE")
    onlyIf { release.orNull == "true" }
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
val genKotlinMinVersion = tasks.register("genKotlinMinVersion") {
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
