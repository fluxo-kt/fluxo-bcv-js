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
- Workflows under `.github/workflows/`.
- `build.yml`: builds **every PR** (any base) + **push to `main`/`dev` only**
  (no tags — a `v*` tag sits on already-built `main` HEAD). PRs are the
  gateable pre-merge check; the concurrency group de-dupes. Do NOT re-add a
  `pull_request: branches-ignore` — those match the PR *base*, so ignoring
  `dev` (the default branch) silently disables builds for every feature PR.
- `codeql.yml`: SAST, **separate + advisory** (not a required check); builds
  ONLY `:plugin`. See "Surprises & gotchas" for why.
- `release.yml` triggers on `v*` tags.
- `pr-fast-forward.yml` enables fast-forward merges via PR comment.
- `pr-baseline.yml` regenerates baselines from PR comment command.
- Flow: branch off `dev` → PR → ff-merge to `dev` → release PR `dev`→`main`.
- Conventional Commits enforced.
- Dependabot opens Gradle + GH-Actions bumps with `build(deps)` /
  `ci(GitHub)` prefixes. **Two ecosystems, two handling paths:**
  - **Gradle bump-PR** → mergeable. If a Gradle dep changed (esp. KGP /
    BCV / `fluxo-kmp-conf`), run `./updateBaseline` before merge so
    `dependencies/*.txt` and any drifted api dumps are refreshed.
  - **GH-Actions bump-PR** → **NEVER merge directly.** Dependabot's
    `github-actions` ecosystem is kept ONLY as a *detection signal* (its
    PRs surface outdated pins). The authoritative update channel is the
    `actions-up` CLI (`actions-up --style sha --mode major --yes` — pins
    by SHA with a version comment, the provenance the bare dependabot
    `uses:` bump lacks). Apply via `actions-up`, push to `dev`, then
    **close** the corresponding dependabot PR. Never hand-edit `uses:`
    SHAs — the tool resolves tag→SHA from the GitHub API; a manual edit
    has no provenance trail and drifts from the tool's version comments.

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
  bundle siblings); `release.yml`'s `Attach Sigstore bundles` step
  does the upload via `gh release upload` and hard-fails if zero
  bundles are found (silent regression guard). Asset names are
  publication-prefixed (e.g. `PluginMaven-plugin-1.1.0.jar.sigstore.json`,
  `Fluxo-bcv-tsPluginMarkerMaven-pom-default.xml.sigstore.json`) so
  the marker-POM signature doesn't collide with the main-pub POM
  signature; sigstore-java v2.x writes `.sigstore.json`. Consumer
  verification path:
  `cosign verify-blob --bundle <PluginMaven-…>.sigstore.json …` with
  identity anchored to `release.yml@refs/tags/v*`.
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
- **Sigstore bundles need asset-name disambiguation when uploaded to
  GitHub Releases.** The `pluginMaven` and `fluxo-bcv-tsPluginMarkerMaven`
  publications each produce a `pom-default.xml.sigstore.json`. `gh
  release upload` derives asset name from each file's basename, and
  GitHub's `ReleaseAsset.name` is unique per release — same-basename
  files from distinct publications collide with HTTP 422
  ("ReleaseAsset.name already exists"). `--clobber` only resolves
  same-name conflicts across re-runs, NOT distinct-content same-basename
  uploads in one command. **Trap: `gh`'s `file#displayName` syntax does
  NOT solve this** — it sets the asset LABEL, not the NAME (the failed
  upload URL exposes the truth: `?label=…&name=<basename>`). The fix in
  `release.yml`'s attach step renames files on disk via `mv` to prefix
  every bundle with its publication name (derived from the parent
  `sigstoreSign…Publication` task dir), so the basename gh reads is the
  unique form. Prefixing is unconditional, not collision-driven, so
  adding future Sigstore outputs cannot regress the gate.
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
- **GitHub default branch is `dev`, NOT `main`** (`gh repo view`). Dependabot
  alerts + the dependency graph are scoped to the default branch ⇒ a CI/
  supply-chain fix CLEARS alerts once it ff-merges to `dev`; no `main` round-
  trip needed. `main` is the release branch (dev→main release PR).
- **CodeQL lives in its own advisory `codeql.yml`, building ONLY `:plugin`.**
  The java-kotlin extractor hard-fails (`KotlinVersionTooRecentError`) on
  Kotlin newer than its bundled ceiling — github/codeql pins one compiled
  extractor per Kotlin (`versions.bzl`), no skip switch. While CodeQL ran in
  `build.yml` it traced the `checks/*` smoke builds (which compile
  `kotlinLatest`, an RC) → killed the matrix for ~6mo. `:plugin` builds at
  repo Kotlin (under the ceiling); `checks/*` are fixtures, not scan targets.
  The build step needs `--no-daemon` + `-Pkotlin.compiler.execution.strategy=
  in-process` (both daemons escape tracing) + `--no-build-cache --rerun-tasks`
  (a cache hit ⇒ empty DB / "no source seen"). Action pinned by SHA but
  `tools:` omitted so the bundle (hence ceiling) floats. Advisory by design:
  goes red when repo Kotlin crosses the ceiling, never blocks merges.
- **Scorecard's open alerts are advisory FPs by design — don't chase the
  score.** `TokenPermissions` (the bulk): every workflow already declares
  a minimal, documented top-level `permissions:`; the findings only flag
  top-level `write`, but every flagged workflow is single-job (top-level
  *is* job-level) except `release.yml`, whose **both** jobs genuinely need
  `contents: write` — so pushing `write` down to job scope is zero
  security delta, pure pattern-matching. `BinaryArtifacts` = the committed
  `gradle-wrapper.jar`s, required by Gradle and checksum-verified by
  build.yml's `wrapper-validation` step (FP). `CodeReview`/`BranchProtection`
  = solo-founder direct-push + a ruleset (not classic protection, which
  Scorecard can't always read). `Fuzzing`/`CIIBestPractices` = N/A for a
  tiny plugin. `SecurityPolicy` is cleared by `SECURITY.md` + enabled
  private vulnerability reporting. Re-deriving this costs ~6 `gh api`
  calls; that's why it's written down.
- **Never commit `checks/*/kotlin-js-store/yarn.lock`** (gitignored). A bare
  `yarn.lock` (no package.json) makes GitHub raise npm Dependabot alerts on
  Kotlin/JS dev-toolchain transitives that are NEVER shipped (plugin runtime
  = java-diff-utils) + aborts security-update runs. The `.d.ts` baselines are
  compiler-derived (npm-toolchain-independent), so pinning protects nothing
  the checks assert. Kotlin regenerates the store per build; `updateBaseline`
  therefore has NO `kotlinUpgradeYarnLock`/`kotlinStoreYarnLock`. Escape hatch
  if a floating npm transitive breaks a check: rename via
  `YarnRootExtension.lockFileName` (unindexed name) and re-commit.
- **Maven Dependabot alerts on build tooling ⇒ fix the dep-graph filter, not
  the deps.** `dependency-submission.yml` submits `include-configurations:
  "^runtimeClasspath$"` (positive allow-list = shipped deps only). A negative
  name filter (the old `^(?!(classpath)).*`) leaks the settings plugin-
  resolution classpath (Develocity→protobuf/grpc; Sigstore/kmp-conf→
  BouncyCastle; commons-io) because it isn't named `classpath`. If a NEW
  never-shipped coord alerts under `manifest=settings.gradle.kts`, the filter
  regressed — don't bump/dismiss the dep.
- **`dependency-submission.yml` MUST keep its `workflow_dispatch` + `schedule`
  triggers.** Default-branch merges land via the `/ff` bot, whose push uses
  `GITHUB_TOKEN`; GitHub does NOT fire push-triggered workflows on GITHUB_TOKEN
  pushes (recursion guard), so the `push:` trigger never runs on a bot-merged
  `dev`. Without the other two triggers the submitted dependency graph silently
  rots → stale transitives → lingering/late Dependabot alerts. After a
  dep-changing merge, refresh now via `gh workflow run
  dependency-submission.yml --ref dev` (dispatched on your own token, so not
  suppressed); the weekly `schedule` is the hands-off backstop (the graph
  feeds Dependabot **security alerts**, so freshness matters even absent a
  merge). build.yml needs no equivalent — required status checks read the
  PR-run contexts already attached to the merged SHA, so its suppressed
  dev-push run is redundant (and the README build badge reads those same
  per-SHA contexts, NOT build.yml's run history — see the badge gotcha below).
- **`pr-clean-cache.yml` only reaps MANUALLY-closed PRs, never `/ff` merges —
  by the same GITHUB_TOKEN recursion guard.** It triggers on
  `pull_request: closed`, but a PR auto-closed by the `/ff` bot's GITHUB_TOKEN
  fast-forward push does NOT fire that event (verified: bot-merged PRs produce
  no run). So `/ff`-merged PRs' `refs/pull/N/merge` Gradle caches are reaped by
  GitHub's **7-day unused-cache eviction**, not this workflow. That is
  sufficient and intentional: cleanup is storage hygiene only (usage stays well
  under the 10 GB cap at this repo's velocity), and it does NOT affect CI
  warmth — the `dev` baseline cache is evicted by its own 7-day inactivity, and
  LRU evicts least-recently-*accessed* first, so the frequently-restored
  baseline is the last victim, not the first. **Do NOT "fix" this** by adding a
  `gh cache delete` step to `pr-fast-forward.yml` (marginal storage gain the
  usage doesn't justify) or by pushing `/ff` via a PAT (long-lived credential
  liability). The workflow uses native `gh cache delete --all --ref …` (the old
  `actions/gh-actions-cache` extension's binary download is egress-blocked at
  `release-assets.githubusercontent.com`; native needs only api.github.com and
  drops a supply-chain dep).
- **README build badge = shields.io check-runs badge, NOT the Actions workflow
  badge.** The workflow badge (`build.yml/badge.svg`) shows the latest *run on
  the branch*; under bot-`/ff` no `build.yml` run ever lands on protected `dev`
  (GITHUB_TOKEN push suppression), so it stayed frozen on the pre-recovery RED
  run while every PR was green. The shields badge reads dev HEAD's per-SHA
  check status — the SAME source the ruleset gates on, always fresh, zero
  compute:
  `…/github/check-runs/<owner>/<repo>/dev?nameFilter=Build%20and%20check%20on%20ubuntu&label=Build`.
  `nameFilter` is EXACT-match (no substring/regex) → pins ONE context: ubuntu is
  the strictest cell (only it runs `check-dual`) and all three OS contexts are
  ruleset-required on an immutable-between-merges `dev`, so ubuntu-green ⟺
  all-green — a faithful proxy that EXCLUDES advisory CodeQL/Scorecard (no
  false-red in the mode they're designed to tolerate). Couples to the exact job
  name (same string as the ruleset's required contexts): a rename detaches the
  badge — it renders grey "unknown status" (NOT red), so update the badge
  `nameFilter` in lockstep when renaming a matrix job. No machine guard for
  this — the failure is cosmetic AND a job rename also breaks the ruleset's
  required contexts (blocking merges loudly), so it can't slip by unnoticed.
  Tracks `dev` (integration line), not `main` (lags until the release PR).
- **`${{ !env.X }}` / `env.X` in a boolean position is a constant, not a
  condition.** GitHub coerces a non-empty string to boolean `true` — and an
  `env:` value is ALWAYS a string, so the literal `"false"` is truthy. A guard
  like `cache-read-only: ${{ !env.IS_DEFAULT_BRANCH }}` therefore pinned to a
  constant `false` (every PR wrote a Gradle cache — `actions/caches` showed
  42 `refs/pull/57/merge` entries vs 1 on `dev`), and
  `if: … || env.IS_DEFAULT_BRANCH` was always-true. actionlint does NOT flag
  this (valid GitHub syntax, just dead semantics). Use the boolean-producing
  comparison directly (`github.ref == format('refs/heads/{0}', …)`) or
  `env.X == 'true'`; never negate/branch on a bare string env. The fix removed
  the variable entirely — a correct gate is one line away if genuinely needed
  (YAGNI). NB: here the constant-`false` was benign by accident — under
  bot-`/ff` the default-branch build run is suppressed, so PR-writable caches
  are what we actually want; see the `cache-read-only` comment in build.yml.
- **`dev`+`main` are protected by a branch ruleset
  (`protect-integration-branches`), the recurrence-prevention from the
  CI-recovery work.** It has NO in-repo file (GitHub-side config) — query it via
  `gh api repos/<owner>/<repo>/rulesets` (don't hardcode its numeric id; that
  changes on recreate). Required
  contexts are EXACTLY build.yml's matrix job names `Build and check on
  {ubuntu,macos,windows}`, each pinned to `integration_id: 15368` (github-actions)
  so a same-named context from another app can't satisfy the gate. It uses the
  `non_fast_forward` rule (blocks force-push), NOT the `pull_request` rule — so
  the `/ff` bot's legitimate ff-push of an already-green SHA still merges.
  `bypass_actors` is `[{OrganizationAdmin, always}]`: the solo founder (org
  owner) direct-pushes `dev`/`main` with no PR ceremony, while the `/ff` bot (a
  github-actions *app*, not an org-admin user) and external contributors stay
  gated by required checks — so a red dependabot/feature *auto-merge* is still
  blocked, the recurrence target. The badge surfaces any red the owner pushes
  directly. Reset `bypass_actors` to `[]` to re-impose PR-only on everyone.
  CodeQL + Scorecard are deliberately NOT required (advisory). **Corollary trap — never add a
  file-path filter (`paths-ignore` OR `paths`) to build.yml's `pull_request`
  trigger:** an `on:`-level path filter emits no check runs for a non-matching
  PR, so the required contexts never report and every docs-only PR is permanently
  BLOCKED from merge (reproduced on PR #48). Both keys skip identically —
  `paths-ignore` on PRs touching only ignored globs, `paths` on PRs touching none
  of the listed globs — so the invariant is "no path filter on the required
  trigger", machine-enforced by build.yml's "Forbid path filters on the
  pull_request trigger" step (a `paths-ignore`-only guard would miss the `paths` half).
  Docs PRs must build in full. The `push` trigger has NO `paths-ignore` either:
  with the OrganizationAdmin bypass, owner direct-pushes are the only pushes it
  sees (`/ff` is GITHUB_TOKEN-suppressed), and the badge reads dev HEAD's build
  check-run — so a docs-only push must still build or the badge blanks to "no
  check runs". If you change a build.yml matrix job NAME, update the ruleset's
  required contexts in lockstep or the gate silently stops matching.
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
