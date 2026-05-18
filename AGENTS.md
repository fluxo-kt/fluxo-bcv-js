# AGENTS.md — fluxo-bcv-js

Tiny single-purpose Gradle plugin: adds TypeScript `.d.ts` API dump/check
support to KotlinX Binary Compatibility Validator (BCV) for Kotlin/JS and
Kotlin/Wasm-JS. Stop-gap until upstream BCV ships JS support
(Kotlin/binary-compatibility-validator#42). Public surface is exactly one
class — `fluxo.bcvts.FluxoBcvTsPlugin`. Plugin ID:
`io.github.fluxo-kt.binary-compatibility-validator-js`.

## Vibe & principles
- **Compatibility is the product.** Must work across BCV 0.8–0.15.x ×
  Kotlin 1.7.22–2.0.x × Gradle 7.6+. Reflection + `safe { }` shims in
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
- `checks/latest/` — composite-build smoke test, newest Kotlin+BCV, KMP
  (`jvm + linuxX64 + js + wasmJs + wasmWasi`). `includeBuild("../../")`.
- `checks/js-only/` — composite-build smoke test, oldest pinned Kotlin+BCV
  (`bcvMin`, `kotlinMin`), legacy `kotlin("js")` plugin (no KMP).
- `gradle/libs.versions.toml` — version matrix. `bcv`/`bcvMin`/`bcvLatest`
  and `kotlin`/`kotlinMin`/`kotlinLatest` drive both the plugin and the
  check modules.
- `updateBaseline` (sh, executable) — single canonical baseline refresh.
  **Recurses** into `checks/latest` and `checks/js-only`.
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
CI matrix runs **JDK 17 (Temurin)** on macOS/Ubuntu/Windows for `build.yml`.
`release.yml` and `pr-baseline.yml` use **JDK 22**. JitPack uses **JDK 21**.
Local dev: 17+ should work; 22 matches release/baseline.
`fkcSetupGradlePlugin` is configured `useJdkRelease = false`; bytecode
target is `javaLangTarget=1.8` (aligned with BCV).

## Compatibility matrix
Single source of truth: `gradle/libs.versions.toml`. Plugin tested against
the matrix in `README.md`. **Don't widen `kotlinMin`/`bcvMin` casually** —
the reflective compat layer's value is precisely that range. If you bump,
verify both `checks/latest` and `checks/js-only` still build.

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
- **`DBG` left at 1** flips lifecycle logging to verbose. Always reset to
  0 before commit unless explicitly debugging.
- **`DirConfig.COMMON` cleaner ordering** is load-bearing — preserve
  `mustRunAfter(bcvCheckCleaner/bcvBuild/bcvCheck)` or BCV's `apiCheck`
  fails with "extra files in buildDir".
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
