# Roadmap, ideas, and notes

<details>
  <summary>Show</summary>

Highest consequence first.

- `kotlinLangVersion = 2.0` warns "deprecated, will be removed" on Kotlin
  2.4.20, and becomes a build blocker on the Kotlin release that removes it.
  Raising it changes the Kotlin metadata that Gradle 7.6's embedded Kotlin
  1.7 must read for the `fluxoBcvTs { }` DSL, so `checks/js-only` (the
  Gradle 7.6 floor) decides whether a raise keeps the floor.
- When Kotlin 2.5.0 goes GA: move build-side `kotlin` onto it and
  `kotlinLatest` to the next preview, then run `./updateBaseline`
  (Dependabot's bump PR cannot refresh baselines; procedure in AGENTS.md
  "Compatibility matrix"). Check that CodeQL's bundled Kotlin ceiling
  covers 2.5 first: CodeQL builds the plugin at build-side Kotlin.
- Flip the dual-mode AUTO default to embedded (`preferEmbedded` unset +
  both validators active) in the first minor after KGP drops the
  `@ExperimentalAbiValidation` opt-in (KT-71172). External stays the default
  until then because switching moves every consumer's `.d.ts` baselines.
- Remove `@Incubating` from `FluxoBcvTsExtension` in 1.3.0 (1.2.0 changed
  what `preferEmbedded` does, so the stability promise moved one minor).
- CI guard that `./gradlew check --dry-run` still lists `:plugin:detektMain`
  and `:plugin:lint`: fluxo-kmp-conf 0.13+ turns both off by default, so
  losing either opt-in leaves `check` green while running neither. New
  enforcement, so it needs a maintainer decision first.
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
