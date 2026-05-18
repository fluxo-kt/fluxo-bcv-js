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
    useJdkRelease = false
    setupCoroutines = false
    allWarningsAsErrors = false
    // Test-only flag (Pass-8 source-trace of fluxo-kmp-conf
    // `KotlinConfigSetup.kt:91-93`): gates the
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
        // NB: `projectUrl`/`publicationUrl` are deliberately NOT set
        // here. fluxo-kmp-conf 0.14.x propagates those into
        // `gradlePlugin.{website,vcsUrl}` ONLY when the Vanniktech
        // maven-publish plugin is applied (`SetupVanniktechPublication
        // .kt:136-146`). We don't apply Vanniktech (no Maven Central
        // path today), so `setupPublication` exits via
        // `loadPluginStaticallyError` (`LoadAndApplyPluginIfNotApplied
        // .kt:269`) which just LOGS a warning — no exception. Setting
        // them here would be dead code under the live config. Direct
        // extension-level wiring lives below alongside the POM/
        // artifactId workarounds; verifyPluginPortalMetadata gates
        // both classes.
    }

    apiValidation {
        nonPublicMarkers.add("kotlin.jvm.JvmSynthetic")
        // sealed classes constructors are not actually public
        ignoredClasses.add("kotlin.jvm.internal.DefaultConstructorMarker")
    }
}

// Project-level `version` MUST be set AFTER `fkcSetupGradlePlugin`:
// fluxo-kmp-conf 0.14.x configures its own `publicationConfig.version`
// (which targets only the main `PluginMavenPublication`) but does NOT
// propagate the value back to `project.version`. The
// `com.gradle.plugin-publish` plugin then auto-generates a SECOND
// publication — the plugin MARKER POM (used by Plugin Portal to
// resolve `plugins { id("...") }` requests) — and that one reads
// `project.version` directly, falling back to the literal
// "unspecified" (Gradle's default). Without this assignment, the
// marker POM AND its `<dependency><version>` line ship as
// "unspecified" — a broken release at the Plugin Portal contract.
// Reproducer: remove this line, run `./gradlew
// :plugin:publishToMavenLocal`, inspect
// `~/.m2/.../io.github.fluxo-kt.binary-compatibility-validator-js.gradle.plugin/`
// — the only subdirectory is `unspecified/`.
// TODO: upstream to fluxo-kmp-conf so `publicationConfig.version`
// also writes through to `project.version`.
version = pluginVersion

// Restore the 1.0.x main-publication artifactId AND the POM metadata
// (name/description/url/licenses/developers/scm) that fluxo-kmp-conf
// 0.14.x stopped wiring into the `PluginMavenPublication`. Two
// related regressions from the same upstream surface change:
//
// 1. artifactId — fluxo-kmp-conf's `SetupPublication.kt:559` rewrites
//    `artifactId = projectName`, so our coord became `plugin` (from
//    `settings.gradle.kts`'s `project(":fluxo-bcv-js").name = "plugin"`
//    rename). 1.0.x shipped as `io.github.fluxo-kt:fluxo-bcv-ts`; we
//    restore that contract here. `plugin` is a meaningless coord that
//    would collide with anything else in the same group.
// 2. POM metadata — 1.0.x's published POM at
//    plugins.gradle.org/m2/io/github/fluxo-kt/fluxo-bcv-ts/1.0.0/
//    has full <name>/<description>/<url>/<licenses>/<developers>/<scm>;
//    fluxo-kmp-conf 0.14.x's PluginMavenPublication ships them as
//    empty (the marker POM has them because `com.gradle.plugin-publish`
//    populates the marker itself from `displayName`/`description`).
//    Plugin Portal accepts incomplete POMs but Maven Central / OSSRH
//    do not — restoring keeps the door open for §1.2.0 Central
//    publishing and is industry-best-practice regardless.
//
// Both consume metadata already known to the script (description,
// pluginVersion) plus a small constant block — no duplication of
// truth. Reproducer for either regression: comment out the relevant
// `pom { … }` line OR the `artifactId = …` line, run
// `./gradlew :plugin:publishToMavenLocal`, inspect the produced
// .pom — the missing field reappears as blank or the dir is `plugin/`.
// TODO: upstream so fluxo-kmp-conf preserves both contracts.
val publishedArtifactId = "fluxo-bcv-ts"
val projectUrl = "https://github.com/fluxo-kt/fluxo-bcv-js"
publishing.publications.withType<MavenPublication>().configureEach {
    if (name == "pluginMaven") artifactId = publishedArtifactId
    pom {
        if (name.orNull.isNullOrBlank()) name.set("Fluxo BCV TS")
        if (description.orNull.isNullOrBlank()) {
            description.set(project.description)
        }
        url.set(projectUrl)
        inceptionYear.set("2023")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("amal")
                name.set("Artyom Shendrik")
                email.set("artyom.shendrik@gmail.com")
            }
        }
        scm {
            url.set(projectUrl)
            connection.set("scm:git:https://github.com/fluxo-kt/fluxo-bcv-js.git")
            developerConnection
                .set("scm:git:ssh://git@github.com/fluxo-kt/fluxo-bcv-js.git")
            tag.set("v${project.version}")
        }
    }
}

// Workarounds for fluxo-kmp-conf 0.14.x publication-setup gaps. The
// same root cause underlies BOTH:
// fluxo-kmp-conf's `setupPublication` (`SetupPublication.kt:89`)
// reads `useVanniktechPublish` (default `true`) and routes to either
// the Vanniktech path or the legacy `setupPublicationGradlePlugin`.
// In OUR build, Vanniktech is not applied AND `useVanniktechPublish`
// is left at default — so the wrong path is attempted, returns via
// `loadPluginStaticallyError` (`LoadAndApplyPluginIfNotApplied.kt:269`,
// just `logger.e(…)`, no exception), and ALL publication setup is
// silently skipped: artifactId, POM metadata, website, vcsUrl, etc.
// We restore each manually here. Upstream fix is TODO at
// fluxo-kt/fluxo-kmp-conf (`setupPublication` should fail loud).
val pluginExt = extensions
    .getByType(org.gradle.plugin.devel.GradlePluginDevelopmentExtension::class.java)
// 1. Plugin id — `fkcSetupGradlePlugin` invokes
//    `gradlePlugin.plugins.maybeCreate(name)` then guards
//    `if (id.isNullOrBlank()) { id = pluginId }`. Under Gradle 8/9
//    `maybeCreate` pre-fills `id` with the plugin's NAME, so the
//    guard reads `id="fluxo-bcv-ts"` (non-blank) and skips overwriting
//    with the real `pluginId`. The resulting
//    `META-INF/gradle-plugins/<id>.properties` would ship under the
//    wrong name and composite-build resolution would fail.
pluginExt.plugins.getByName("fluxo-bcv-ts").id = pluginId
// 2. `com.gradle.plugin-publish` 2.x dropped the legacy `pluginBundle`
//    extension and requires `website` + `vcsUrl` as `Property<String>`
//    on `GradlePluginDevelopmentExtension` itself. Missing either
//    fails `publishPlugins` at task-execution time with
//    `IllegalArgumentException: Website URL not set` — a CI-only
//    surface (`:publishToMavenLocal` doesn't trip the validator),
//    which is why the 1.1.0 release attempt #1 surfaced this only
//    after the signed tag was already pushed. The
//    `verifyPluginPortalMetadata` task wired below catches the class
//    of regression at config-time so `:check` gates it from PRs.
pluginExt.website.set(projectUrl)
pluginExt.vcsUrl.set("$projectUrl/tree/v${project.version}")

// Pre-flight Plugin Portal metadata gate. Sibling-aligned with
// fluxo-kmp-conf's `VerifyPluginPortalMetadataTask` (its
// `build.gradle.kts:617-640`). Eliminates the class of regression
// where a fluxo-kmp-conf API gap or upstream surface change leaves
// a publish-time required field blank — historically (1.1.0 release
// attempt #1) `gradlePlugin.{website,vcsUrl}` were left null by
// fluxo-kmp-conf 0.14.x, surfacing only inside `release.yml`'s
// `publishPlugins` execution AFTER the signed tag had been pushed.
// All fields here correspond either to plugin-publish 2.x's runtime
// validation OR to manual workarounds wired above (project.version,
// artifactId, POM metadata). Failing this task is a hard build-gate
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
    // throws here. Our publish block uses `withType.configureEach`
    // which IS lazy and applies the artifactId / POM contract whenever
    // the publication appears — a defense-in-depth gate for THAT
    // surface is a follow-up if and when it regresses.
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
                    "\"Surprises & gotchas\" for fluxo-kmp-conf 0.14.x gaps.",
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

// Gate Sigstore signing on the `RELEASE` env var so it fires ONLY in
// `release.yml` (which already sets `RELEASE: true`). The Sigstore
// plugin auto-wires its `sigstoreSign*Publication` tasks into every
// `MavenPublication`'s publish chain — without this gate, a developer
// running `./gradlew :plugin:publishToMavenLocal` would block on a
// browser-OIDC prompt at `oauth2.sigstore.dev/auth/...`. To force-test
// the Sigstore path locally, run with `RELEASE=true ./gradlew ...`
// (opt-in; the developer accepts the OIDC ceremony).
//
// `notCompatibleWithConfigurationCache` is also required:
// `dev.sigstore.sign 2.0.x` (`SigstoreSignFilesTask`) captures a
// `DefaultProject` reference, which Gradle's configuration cache
// refuses to serialize. With `org.gradle.configuration-cache.problems=fail`
// + `max-problems=0` (gradle.properties), a fresh local
// `publishToMavenLocal` would CC-fail at config-store time — BEFORE
// `onlyIf` is even evaluated. Marking the task CC-incompatible bypasses
// CC for just these tasks; the rest of the build still benefits from
// CC. release.yml already passes `--no-configuration-cache`, so CI is
// unaffected either way.
tasks.matching { it.name.startsWith("sigstoreSign") }.configureEach {
    onlyIf { providers.environmentVariable("RELEASE").orNull == "true" }
    notCompatibleWithConfigurationCache(
        "dev.sigstore.sign 2.0.x captures Project reference — upstream CC violation",
    )
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
