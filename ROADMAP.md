# Roadmap, ideas, and notes

<details>
  <summary>Show</summary>

Highest consequence first.

- `kotlinLangVersion = 2.0` warns "deprecated, will be removed" on Kotlin
  2.4.20, and becomes a build blocker on the Kotlin release that removes it.
  Raising it to 2.1+ breaks the Gradle 7.6 floor (reason in
  `gradle/libs.versions.toml`), so that release forces a choice: keep the
  build-side compiler on the last Kotlin accepting 2.0, raise the Gradle
  floor (needs a ruling; first find the lowest Gradle whose embedded Kotlin
  reads the new module file), or ship the jar without
  `META-INF/*.kotlin_module` (only internal top-level functions need it;
  unproven).
- When Kotlin 2.5.0 goes GA: move build-side `kotlin` onto it and
  `kotlinLatest` to the next preview, then run `./updateBaseline`
  (Dependabot's bump PR cannot refresh baselines; procedure in AGENTS.md
  "Compatibility matrix"). Check that CodeQL's bundled Kotlin ceiling
  covers 2.5 first: CodeQL builds the plugin at build-side Kotlin.
- Remove `@Incubating` from `FluxoBcvTsExtension` in 1.3.0 (1.2.0 changed
  what `preferEmbedded` does, so the stability promise moved one minor).
  The unreleased default flip changes it again, so by the same reason the
  target is open: decide 1.3.0 or later when that release is cut.
- AGP Kotlin Multiplatform Android target lane: a single BCV-platform target
  without BCV tasks must not fail configuration (`androidApiBuild` not
  found). Covered only by code today; a lane costs an Android SDK in CI.
- Support BCV applied only to the root project (BCV's documented layout):
  today the plugin sees BCV only where it is applied, so such subprojects get
  embedded mode or nothing. Supporting it means detecting BCV through the
  ancestors' extension and extending the BCV < 0.15 refusal there too (that
  BCV's root-configured `apiDump` would still delete per-target baselines);
  needs a multi-project cell.
- Integration tests — incl. resolving the *published* plugin end-to-end
  from the Plugin Portal (`plugins{}`) and JitPack (`useModule`).
  `checks/js-only` resolves the published marker + jar from a local repo,
  but no lane hits either real repository, so a wrong coordinate in the
  README or the published metadata ships undetected
- Pull requests for
  - ★4000 https://github.com/square/wire
  - ★49 https://github.com/DrewCarlson/mobius.kt
  - ★23 https://github.com/Liftric/cognito-idp
  - https://github.com/search?q=binaries.executable+path%3A*.kts&type=code&ref=opensearch
- JAR shrinking via ProGuard/R8 — prototype archived as tag
  `archive/feat-shrinking` (`git switch -c feat/shrinking
  archive/feat-shrinking` to resume). Deferred: a plugin JAR is
  build-time-only (never shipped into consumer apps), so size is
  near-irrelevant; the ~1k-line `.pro` ruleset isn't worth the upkeep.
  Revisit only if the JAR grows materially.

</details>
