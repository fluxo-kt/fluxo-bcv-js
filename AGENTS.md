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
  `fluxo-bcv-js/`, and the artifact is `plugin.api` / `plugin-*.jar`.
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

## CI / branches / release
- Workflows under `.github/workflows/`.
- `build.yml` runs on PR + push (skips `dev/feat*/fix/mr/pr/pull/wip` branches
  on push to avoid duplicate runs vs PR triggers).
- `release.yml` triggers on `v*` tags.
- `pr-fast-forward.yml` enables fast-forward merges via PR comment.
- `pr-baseline.yml` regenerates baselines from PR comment command.
- Flow: branch off `dev` → PR → ff-merge to `dev` → release PR `dev`→`main`.
- Conventional Commits enforced.
- Dependabot opens Gradle + GH-Actions bumps with `build(deps)` /
  `ci(GitHub)` prefixes. **When picking up a bump-PR**: if a Gradle dep
  changed (esp. KGP / BCV / `fluxo-kmp-conf`), run `./updateBaseline`
  before merge so `dependencies/*.txt` and any drifted api dumps are
  refreshed.

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
- **Sigstore signing is RELEASE-only** (1.1.0+). `dev.sigstore.sign`
  auto-wires `sigstoreSign*Publication` tasks into **every**
  `MavenPublication`'s publish chain — including
  `publishToMavenLocal`. Without a gate, the canonical local-consumer
  smoke test blocks on a browser-OIDC prompt at
  `oauth2.sigstore.dev/auth/...`. The script gates these tasks with
  `onlyIf { providers.environmentVariable("RELEASE").orNull == "true" }`
  AND `notCompatibleWithConfigurationCache(...)` (Sigstore 2.0.x
  `SigstoreSignFilesTask` captures a `DefaultProject` ref, which our
  strict CC config — `problems=fail max-problems=0` — would
  otherwise treat as fatal). The `RELEASE: true` env var is set at
  the workflow level in `release.yml`. To force-test locally:
  `RELEASE=true ./gradlew :plugin:publishToMavenLocal` (developer
  accepts the OIDC ceremony). Bundles ship to consumers as GitHub
  Release assets (Plugin Portal and Maven Central don't upload
  `.sigstore.bundle` siblings); `release.yml`'s `Attach Sigstore
  bundles` step does the upload via `gh release upload` and
  hard-fails if zero bundles are found (silent regression guard).
  Consumer verification path: `cosign verify-blob --bundle
  <name>.sigstore.bundle …` with identity anchored to
  `release.yml@refs/tags/v*`.
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
- **`dev.sigstore.sign 2.x` writes `.sigstore.json`, not
  `.sigstore.bundle`.** sigstore-java's bundle extension flipped
  between major versions: v0.x `.sigstore`, v1.x `.sigstore.bundle`,
  v2.x `.sigstore.json`. `release.yml`'s asset-attach `find` covers
  all three via alternation — keep the union when bumping the plugin,
  do not narrow.
- **`release.yml` is idempotent against Plugin Portal duplicates.**
  The "Probe Plugin Portal for existing version" step short-circuits
  `publishPlugins` when the marker POM already resolves at the
  tagged version. Sigstore signing still fires via the alternate
  "Sign artefacts (when publish was skipped)" path so re-tags
  produce valid bundles with the original `release.yml@refs/tags/v*`
  OIDC identity. The class of regression "tag pushed but downstream
  step failed → can't safely re-push the same tag" is eliminated:
  delete tag, re-tag, re-push — workflow auto-detects existing
  Portal publication and skips just that step.
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
- ⚠ **Hit something else surprising? Add it here and tell the user.**

## What's NOT in this repo
- No unit/integration tests for the plugin itself; coverage is the two
  `checks/*` composite builds. TestKit suite is in `ROADMAP.md`.
- No published Dokka site. Source-level KDoc only.
- No release-notes generator; `CHANGELOG.md` is hand-edited (Common
  Changelog style).

## External pointers
- BCV upstream: https://github.com/Kotlin/binary-compatibility-validator
- Originating issue: Kotlin/binary-compatibility-validator#42
- `fluxo-kmp-conf`: https://github.com/fluxo-kt/fluxo-kmp-conf
- Common Changelog: https://common-changelog.org/
