# AGENTS.md — fluxo-bcv-js

Tiny single-purpose Gradle plugin: adds TypeScript `.d.ts` API dump/check
support to KotlinX Binary Compatibility Validator (BCV) for Kotlin/JS and
Kotlin/Wasm-JS. Stop-gap until upstream BCV ships JS support
(Kotlin/binary-compatibility-validator#42). Public surface is exactly one
class — `fluxo.bcvts.FluxoBcvTsPlugin`. Plugin ID:
`io.github.fluxo-kt.binary-compatibility-validator-js`.

## Vibe & principles
- **Compatibility is the product.** Must work across BCV 0.8–0.18.1
  (upstream frozen at 0.18.1) × Kotlin 1.7.22–2.4 (`kotlinLatest` is
  bleeding-edge in `checks/latest`) × Gradle 7.6+. Plus the embedded
  KGP `abiValidation { }` path since 1.1.0 (Kotlin 2.2+).
  Reflection + `safe { }` shims in
  `CompatibilityUtils.kt` are *intentional, not a smell*. New KGP/BCV API
  access → add a `*Compat` accessor with `safe { }` fallbacks and a KDoc
  `@see` to the upstream symbol; never call newer APIs directly.
- **Stop-gap by design.** Built originally for the Fluxo state-management
  framework, then published. Should be deletable the day upstream BCV adds
  JS support — keep the seam clean, don't grow scope. *Exit path*: when
  upstream covers JS/Wasm-JS, this plugin should become a thin delegator
  or be removed — verify the eventual upstream API before deciding.
- **Don't fight BCV; integrate.** Re-use BCV task names/groups; piggy-back
  on `apiDump`/`apiCheck` lifecycle. Custom tasks only where reflection
  into `KotlinApiCompareTask` would be too brittle.
- **CC-friendly Kotlin DSL.** Configuration cache is on. Capture
  providers/values into local vals; never close over `Project`/`Task` in
  `doLast` blocks.
- **Conventional Commits, flat history, `--ff-only` merges.** See
  `CONTRIBUTING.md` for full rules.
- ⚠ **If something surprises you while working here: tell the user AND add
  a bullet to "Surprises & gotchas" below**, so the next agent doesn't
  re-pay the cost.

## Layout (load-bearing only)
- `fluxo-bcv-js/` — the plugin module. `settings.gradle.kts` does
  `project(":fluxo-bcv-js").name = "plugin"`, so **Gradle path is
  `:plugin`** (verified via `./gradlew projects`). The dir is still
  `fluxo-bcv-js/`; local build outputs are `plugin.api` / `plugin-*.jar`.
  **The *published* Maven artifactId is `fluxo-bcv-ts`** (`build.gradle.kts`
  `artifactId = publishedArtifactId`), so the JitPack coordinate is
  `…fluxo-bcv-js:fluxo-bcv-ts:<ver>`, NOT `:plugin:` (that's the Gradle *task*
  path / local jar name). README's JitPack `useModule` must use the published
  artifactId — `:plugin:` 404s on JitPack (shipped broken in 1.1.0). The Portal
  consumer uses `plugins { id(…) version }` (the plugin id), never this raw
  coordinate.
- `fluxo-bcv-js/src/main/kotlin/fluxo/bcvts/` — all sources, single package.
- `fluxo-bcv-js/api/plugin.api` — JVM API baseline of the plugin itself.
- `checks/latest/` — composite-build smoke, newest Kotlin+BCV, KMP
  (`jvm + linuxX64 + js + wasmJs + wasmWasi`), Gradle 9.5.1.
- `checks/middle/` — matrix interior (Kotlin 2.2.21 + BCV 0.16.3,
  Gradle 8.14.5). Catches drift between floor and ceiling.
- `checks/kgp-only/` — embedded-only path: KGP `abiValidation { }`,
  NO external BCV. Validates dual-mode trigger + `DirConfig.TARGET_DIR`.
- `checks/dual/` — both validators active; CI sweeps
  `-PpreferEmbedded={auto,true,false}` and asserts the lifecycle
  observable contract.
- `checks/js-only/` — floor smoke (`bcvMin`, `kotlinMin`,
  legacy `kotlin("js")` plugin, Gradle 8.6).
- All composite modules `includeBuild("../../")` against the root build.
- **`checks/{latest,dual,kgp-only}` symlink `gradle`/`gradlew`/`gradlew.bat`
  → their `../../` root copies** (all are Gradle 9.5.1, so they share root's
  wrapper — no committed jar of their own); `js-only` (8.6) and `middle`
  (8.14.5) pin different Gradle versions so keep their own wrappers. The
  3 symlinked modules look wrapper-less in `git ls-files` but run fine —
  NEVER "fix" that by committing jars (duplicate binary, desyncs their
  Gradle version from root).
- `gradle/libs.versions.toml` — version matrix. `bcv`/`bcvMin`/`bcvLatest`
  and `kotlin`/`kotlinMin`/`kotlinLatest` drive both the plugin and the
  check modules.
- `updateBaseline` (sh, executable) — single canonical baseline refresh.
  **Recurses** into all `checks/*` modules; for `checks/kgp-only` it
  also refreshes KGP's KLIB ABI via `updateKotlinAbi`.
- `detekt.yml`, `.editorconfig` — style. `ktlint_official`, 100-col Kotlin,
  4-space indent, trailing comma allowed.

## Source files (key ones)
- `FluxoBcvTsPlugin.kt` — entry. Waits for BCV plugin + (KMP|legacy KJS),
  defers to `configureTsApiTasks()` in `afterEvaluate`. Logs errors when
  prerequisites missing.
- `ConfigureTsApiTasks.kt` — the brains. Builds task graph, target
  detection, `DirConfig` selection, BCV `COMMON`-strategy workaround. Top
  of file has `internal const val DBG`: 0 = silent, 1 = trace logs.
  **Reset to 0 before commit.** There is a `FIXME` to make target wiring
  lazy (currently uses `afterEvaluate`).
- `CompatibilityUtils.kt` — reflective compat shims for KGP/BCV API drift.
  All access wrapped in `safe { }`. Add new shims here, not inline.
- `KotlinTsApiBuildTask.kt` — collects emitted `.d.ts` from
  `KotlinJsIrLink.destinationDirectory`, normalises CRLF → LF, ensures
  single trailing newline. `@DisableCachingByDefault` (output is cheap).
- `KotlinTsApiCompareTask.kt` — `@CacheableTask`. Compares via
  `java-diff-utils` unified diff; uses `lines()` (CRLF-safe). The nullable
  `projectApiFile` + `nonExistingProjectApiFile` pair is a documented
  workaround for gradle/gradle#2016.
- `GradleUtils.kt` — `namedCompat()`, `maybeRegister()`, `task<T>()`. Use
  these (Gradle 8.6+ adds lazy name filtering; fallback for older).
- `ValidateKotlinVersion.kt`, `FluxoBcvTsState.kt`, `DirConfig.kt`,
  `TargetConfig.kt` — small/structural; purpose evident from filename.

## Tasks the plugin creates
- Umbrella: `apiBuild` / `apiDump` / `apiCheck` (auto-wired into `check`).
- Per JS/Wasm target: `${targetTsName}ApiBuild`, `…ApiDump`, `…ApiCheck`.
  `targetTsName` = `ts` for bare `js`; replaces `js`→`Ts` in mixed names;
  else appends `Ts`.
- Special (only in `DirConfig.COMMON`): `${bcvCheck}TsCompatCleaner` —
  removes the `.d.ts` from buildDir before BCV's `apiCheck`, with
  `mustRunAfter` ordering. Load-bearing; preserve.

## Common commands
```sh
./gradlew check                  # build + lint + all api checks (root)
./gradlew :plugin:apiCheck       # plugin's own JVM API baseline
./gradlew :plugin:apiDump        # refresh plugin.api after API change

# Composite-build smoke tests — must cd in
(cd checks/latest && ./gradlew apiCheck)
(cd checks/js-only && ./gradlew apiCheck)

./updateBaseline                 # refresh ALL baselines (root + checks/*)
                                 # api dumps + dependency-guard
                                 # + detekt + lint
```
**Local consumer test**: bump `fluxoBcvJs` in `gradle/libs.versions.toml`,
`./gradlew :plugin:publishToMavenLocal`, then in the consumer use
`mavenLocal()` and the snapshot version.

## JDKs
CI matrix runs **JDK 21 (Temurin)** on macOS/Ubuntu/Windows for `build.yml`.
`release.yml` and `pr-baseline.yml` also use **JDK 21**. JitPack uses
**openjdk21**. Local dev: 17+ should work; 21 matches all CI surfaces.
`fkcSetupGradlePlugin` is configured `useJdkRelease = false`; bytecode
target is `javaLangTarget=1.8` (aligned with BCV).

## Compatibility matrix
Single source of truth: `gradle/libs.versions.toml`. Plugin tested against
the matrix in `README.md`. **Don't widen `kotlinMin`/`bcvMin` casually** —
the reflective compat layer's value is precisely that range. If you bump,
verify **all four** smoke modules still build: `checks/js-only` (floor),
`checks/middle` (interior), `checks/latest` (ceiling), `checks/kgp-only`
(embedded-only), and `checks/dual` (coexistence).

1.1.0 added the dual-mode contract: the plugin activates on EITHER the
external BCV plugin (1.0.x behaviour) OR KGP-embedded `abiValidation { }`
(Kotlin 2.2+). External BCV is **frozen** upstream at 0.18.1 — the
matrix ceiling is the physical upstream ceiling, not an arbitrary pin.

**Bumping build-side `kotlin` (the published-JAR compiler) is
consumer-invisible — but FALSIFY across four independent channels, never
just one.** The published POM **and** Gradle `.module` exclude
`kotlin-stdlib*`/`kotlin-metadata` (Gradle supplies the Kotlin runtime to
plugin consumers), so `java-diff-utils` is the *only* declared runtime dep
and the compiler version cannot leak downstream. Before concluding
"tooling-only, no release" on a `kotlin` bump, diff all four against the
pre-bump revision: (1) `:plugin:apiCheck` (JVM ABI — signatures only);
(2) `:plugin:generatePomFileForPluginMavenPublication` (POM dep contract);
(3) `:plugin:generateMetadataFileForPluginMavenPublication` (`.module` —
the *primary* contract for Gradle consumers; the POM defers to it); and
(4) the emitted **bytecode major version** (`javap -v` on
`build/classes/kotlin/main`, must stay `52`/Java 8 — the basis of the
Gradle-7.6+/old-runtime compat claim). `apiCheck` alone is insufficient:
it sees neither the POM/metadata dep declarations nor the bytecode target.
A new Kotlin minor can trip a `[fluxo-kmp-conf] … JVM target may be
silently capped … extend the table` warning — benign **only** because we
target the 1.8 floor (capping limits the ceiling, not the floor); verify
channel (4), don't trust the warning's "silently". Pair the bump with
`kotlinLatest`: when the RC it tracked
goes GA, move build-side onto the GA **and** advance `kotlinLatest` to the
next preview (or the GA itself if none exists yet) so `checks/latest`
keeps testing the newest available, not a superseded prerelease. Refresh
dependency-guard baselines with `./updateBaseline` (a `kotlin` bump
touches only the root classpath txts; a `kotlinLatest` bump only
`checks/latest`).

## CI / branches / release
Workflows in `.github/workflows/`. **Workflow/release/Sigstore specifics,
dependabot+`actions-up` protocol, branch ruleset, build badge + their hard-won
gotchas → `.github/AGENTS.md`** (auto-loads on `.github/` edits). Universal
essentials: default branch = `dev` (NOT `main`; `main` is release-only); flow =
branch `dev` → PR → `/ff`-merge `dev` → release PR `dev`→`main`; Conventional
Commits, flat `--ff-only` (`CONTRIBUTING.md`).

## Surprises & gotchas (read before debugging)
- **Gradle path is `:plugin`, not `:fluxo-bcv-js`** (the dir name).
  Subproject was renamed in 1.0.0; `jitpack.yml` and the README JitPack
  snippet were updated in 1.1.0. Watch for new artefacts (Dependabot
  configs, badges) that drift back to the dir-name path.
- **Plugin ID still says "js" not "ts"** despite 1.0.0 internal rename.
  Public contract; don't fix.
- **Build wrapped by `fluxo-kmp-conf`** (`fkcSetupGradlePlugin`). Standard
  `kotlin { jvmToolchain(...) }` won't be where you expect.
- **`afterEvaluate` is required** — KMP target collection isn't ready
  earlier. Code has a `FIXME` to make this lazy; respect it.
- **`DBG` constant is gone** (1.1.0). Diagnostics route through Gradle's
  logger — `./gradlew apiCheck --debug` surfaces compat-shim drift.
- **`DirConfig.COMMON` cleaner ordering** is load-bearing — preserve
  `mustRunAfter(bcvCheckCleaner/bcvBuild/bcvCheck)` or BCV's `apiCheck`
  fails with "extra files in buildDir". In embedded-only mode (no
  external BCV plugin applied) the dirConfig provider short-circuits
  to `DirConfig.TARGET_DIR`, so the cleaner branch is unreachable —
  this is intentional, do NOT remove the gate at `ConfigureTsApiTasks.kt`.
- **Dual-mode trigger** (1.1.0+): the pipeline fires on **either**
  external `org.jetbrains.kotlinx.binary-compatibility-validator` OR
  KGP-embedded `kotlin { abiValidation { } }`. Detection of the
  embedded path is **task-based** (`tasks.names` contains
  `checkKotlinAbi`/`updateKotlinAbi`) — extension-shape probes break
  on the Kotlin 2.4-RC `.enabled`-property removal. If a future KGP
  renames `checkKotlinAbi`, update `CompatibilityUtils.kt`'s
  `CHECK_KOTLIN_ABI_TASK`/`UPDATE_KOTLIN_ABI_TASK` constants.
- **`safe { }` is narrow** (1.1.0): catches `LinkageError`,
  `ReflectiveOperationException`, `RuntimeException`. VM-fatal errors
  (`OutOfMemoryError`, `StackOverflowError`, `AssertionError`) now
  propagate. Don't widen back to `Throwable`.
- **Lifecycle observable** is part of the integration-test contract:
  `[fluxo-bcv-ts] trigger=external|embedded preferEmbedded=auto|true|false`
  is emitted exactly once per build invocation and asserted by
  `checks/dual`. Keep the format stable; `checks/dual`'s CI step
  greps for it.
- **`FluxoBcvTsExtension` is an `@Incubating` `interface`** (1.1.0).
  Managed type with abstract `Property<T>` getters — Gradle's
  ManagedFactory synthesizes the impl. Stability commitment moment
  is targeted for 1.2.0 (remove `@Incubating`). Until then any 1.x
  minor may break the extension shape.
- **Sigstore signing is RELEASE-only** (1.1.0+). `dev.sigstore.sign` auto-wires
  `sigstoreSign*Publication` into **every** `MavenPublication`'s publish chain —
  incl. `publishToMavenLocal`, so without a gate the canonical local-consumer
  smoke test blocks on a browser-OIDC prompt. `build.gradle.kts` gates with
  `onlyIf { providers.environmentVariable("RELEASE").orNull == "true" }` AND
  `notCompatibleWithConfigurationCache(...)` (Sigstore 2.0.x
  `SigstoreSignFilesTask` captures a `DefaultProject` ref → fatal under our
  strict CC `problems=fail max-problems=0`). Force-test: `RELEASE=true ./gradlew
  :plugin:publishToMavenLocal`. Release-side bundle attach/naming/verification →
  `.github/AGENTS.md`.
- **`project.version` MUST be assigned AFTER `fkcSetupGradlePlugin`**
  (`fluxo-bcv-js/build.gradle.kts`, near `version = pluginVersion`).
  fluxo-kmp-conf 0.14.x configures `publicationConfig.version`
  (which only flows to the main `PluginMavenPublication`) but does
  NOT propagate the value back to `project.version`. The
  `com.gradle.plugin-publish` plugin then auto-generates a SECOND
  publication — the plugin MARKER POM that Plugin Portal uses to
  resolve `plugins { id("...") }` requests — and that one reads
  `project.version` directly. Without the post-hoc assignment the
  marker ships as `<version>unspecified</version>` (Gradle's
  default), breaking the Plugin Portal contract. Reproducer: remove
  the line, run `./gradlew :plugin:publishToMavenLocal`, and inspect
  `~/.m2/.../io.github.fluxo-kt.binary-compatibility-validator-js.gradle.plugin/`
  — the only subdirectory will be `unspecified/`. Assigning earlier
  (e.g. next to `group =`) is silently overwritten by
  `fkcSetupGradlePlugin`'s internal configuration. Upstream fix is
  TODO at fluxo-kmp-conf.
- **fluxo-kmp-conf 0.14.x silently no-ops publication setup when
  Vanniktech isn't applied.** `setupPublication` defaults
  `useVanniktechPublish = true`. With no Vanniktech maven-publish
  plugin in our `plugins {}` block, fluxo-kmp-conf takes the Vanniktech
  branch, calls `loadPluginStaticallyError` (just `logger.e(…)`, **does
  NOT throw**), and the entire publication-setup path silently exits
  — `gradlePlugin.{website,vcsUrl}`, POM metadata, artifactId, all
  unwired. That's why our `build.gradle.kts` carries direct extension-
  level workarounds for ALL of these (`pom { … }` block,
  `pluginExt.website.set(…)`, `artifactId = "fluxo-bcv-ts"`,
  `version = pluginVersion`). Reproducer: comment out
  `pluginExt.website.set(projectUrl)`, run
  `./gradlew :plugin:verifyPluginPortalMetadata` — that gate now
  catches it locally (sibling-aligned defense-in-depth task; runs as
  a `:check` dep so PR/push CI gates it). Without the verify task,
  the next regression class would only surface inside
  `release.yml`'s `publishPlugins` execution, AFTER a signed tag is
  pushed (how 1.1.0 release attempt #1 failed). The upstream fix is
  TODO at fluxo-kmp-conf — `setupPublication` should fail loud
  (throw) when its configured publish backend isn't loadable.
- **POM metadata audits MUST cover the `gradlePlugin` extension too,
  not just POM XML.** plugin-publish 2.x validates
  `gradlePlugin.{website,vcsUrl}` independently of any POM `<url>` /
  `<scm>`. These are two distinct metadata channels: POM XML feeds
  Maven repositories; `gradlePlugin` feeds the Plugin Portal listing
  page. An audit that only inspects published `.pom` files will miss
  the plugin-publish 2.x gate (which fires at task-execution time, so
  also invisible to `:publishToMavenLocal`).
- **Reflection failures are silently swallowed by `safe { }`.** If
  something silently no-ops on a new Kotlin/BCV, suspect the compat shim
  first.
- **`hasGenerateTypeScriptDefinitions`** flag toggles error message in
  `KotlinTsApiBuildTask`. Don't remove without updating the task.
- **wasmWasi is intentionally skipped** (`ALLOW_WASM_WASI=false`). Don't
  enable until Kotlin emits `.d.ts` for it.
- **`updateBaseline` is a shell script, not a Gradle task.** Calling
  `./gradlew apiDump` from root only refreshes the plugin's own dump,
  not the `checks/*` ones.
- **Configuration cache is on** — `Project`/`Task` capture in `doLast`
  fails. Capture providers/values into local vals first.
- **`DSL_SCOPE_VIOLATION` suppress** in `checks/js-only/build.gradle.kts`
  is for old Gradle <8 catalog access. Keep it.
- **Never commit `checks/*/kotlin-js-store/yarn.lock`** (gitignored). A bare
  `yarn.lock` (no package.json) makes GitHub raise npm Dependabot alerts on
  Kotlin/JS dev-toolchain transitives that are NEVER shipped (plugin runtime
  = java-diff-utils) + aborts security-update runs. The `.d.ts` baselines are
  compiler-derived (npm-toolchain-independent), so pinning protects nothing
  the checks assert. Kotlin regenerates the store per build; `updateBaseline`
  therefore has NO `kotlinUpgradeYarnLock`/`kotlinStoreYarnLock`. Escape hatch
  if a floating npm transitive breaks a check: rename via
  `YarnRootExtension.lockFileName` (unindexed name) and re-commit.
- ⚠ **Hit something else surprising? Add it here and tell the user.**

## What's NOT in this repo
- No unit/integration tests for the plugin itself; coverage is the two
  `checks/*` composite builds. TestKit suite is in `ROADMAP.md`.
- No published Dokka site. Source-level KDoc only.
- No release-notes generator; `CHANGELOG.md` is hand-edited, Common
  Changelog *style* with two deliberate house deviations — do NOT
  "correct" them: (1) a recurring `### Updated` category for version/
  dependency bumps (not a canonical Common Changelog category);
  (2) released entries are treated as IMMUTABLE history — fix a
  released entry only to correct a factual error (as `1e264ab` did
  for the Sigstore bullet), never to re-categorise/re-order. A
  consumer-invisible change (CI/infra/tooling/build-compiler bump
  proven across the 4 channels above) gets NO entry and leaves
  `## Unreleased` empty — that emptiness is correct, not an omission;
  the changelog is a *consumer* document.

## External pointers
- BCV upstream: https://github.com/Kotlin/binary-compatibility-validator
- Originating issue: Kotlin/binary-compatibility-validator#42
- `fluxo-kmp-conf`: https://github.com/fluxo-kt/fluxo-kmp-conf
- Common Changelog: https://common-changelog.org/
