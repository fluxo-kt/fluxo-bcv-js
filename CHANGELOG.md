# Changelog [^1]


## Unreleased

[//]: # (Changed, Added, Removed, Fixed, Updated)


## [1.1.0] - 2026-05-18

### Added
- **Dual-mode support** — the plugin now activates on **either**
  trigger source: the external KotlinX [BCV][bcv] plugin (1.0.x
  behaviour) **or** KGP-embedded `kotlin { abiValidation { } }`
  (Kotlin 2.2+, opt-in via `@OptIn(ExperimentalAbiValidation::class)`).
  See README's "Dual-mode usage" section for the embedded-only setup.
- New public `FluxoBcvTsExtension` (DSL key `fluxoBcvTs { }`, marked
  `@Incubating`) with two `Property<Boolean>` knobs:
  - `preferEmbedded` — null/AUTO (default) prefers external on
    coexistence with a one-shot migrate-to-embedded recommendation;
    `true` forces embedded; `false` forces external. Falls back
    silently if the preferred source isn't available.
  - `wireToKgpAbi` — when `true`, registers this plugin's umbrella
    `:apiCheck` as a dependency of KGP's `:checkKotlinAbi`. Default
    `false` (the umbrella `:check` task already triggers `:apiCheck`).
- Machine-parseable lifecycle observable:
  `[fluxo-bcv-ts] trigger=external|embedded preferEmbedded=auto|true|false`
  printed exactly once per build invocation when the pipeline fires.
- SLSA provenance via [Sigstore](https://www.sigstore.dev/) keyless
  signing — every Plugin Portal release now ships `.sigstore.bundle`
  siblings for both the plugin JAR and its marker POM;
  cosign-verifiable identity-anchored to
  `release.yml@refs/tags/v*`.
- OSSF [Scorecard](https://github.com/ossf/scorecard) workflow —
  weekly + push-to-main analysis; SARIF results surface in GitHub
  Code Scanning.
- Three new composite-build smoke modules:
  - `checks/kgp-only/` — KGP-embedded path only, no external BCV.
  - `checks/dual/` — both validators active; parametrised
    `preferEmbedded` sweep (auto/true/false) in CI.
  - `checks/middle/` — matrix interior (Kotlin 2.2.21, BCV 0.16.3).
- `actionlint`, `dependency-review`, and `dependency-submission`
  workflows (sibling-aligned with `fluxo-kmp-conf`).

### Changed
- **Compat matrix**: external BCV now supports the full _0.8 - 0.18.1_
  span (frozen ceiling — external BCV is [no longer maintained
  upstream](https://github.com/Kotlin/binary-compatibility-validator/tree/0.18.1)).
  Kotlin still _1.7.22+_; Gradle consumer floor _7.6+_. CI exercises
  the matrix top against `Kotlin 2.4.0-RC` via `checks/latest`.
- Reflective KGP/BCV compat layer (`CompatibilityUtils.kt`) is now
  fully reflective for `KotlinJsIrLink.mode`, `JsIrBinary.generateTs`,
  and `modeProperty` — survives the Kotlin 2.3 ERROR-level deprecation
  and the likely Kotlin 2.4+ physical-symbol removal.
- `safe { }` shim narrowed from `catch (Throwable)` to
  `catch (LinkageError | ReflectiveOperationException | RuntimeException)`
  — VM-fatal errors (OOM, StackOverflow, AssertionError) now propagate.
- KGP-embedded `abiValidation` detection is **task-based** (probes
  `tasks.names` for `checkKotlinAbi`/`updateKotlinAbi`) rather than
  extension-shape-based, surviving the Kotlin 2.4-RC removal of the
  `abiValidation.enabled` property.
- Migrated `com.gradle.enterprise` → `com.gradle.develocity 4.4.1`
  (lazy `termsOfUse*.set()` DSL, `publishing.onlyIf { false }`
  restores opt-in scan publishing).
- All GitHub Actions SHA-pinned via `actions-up`; third-party actions
  pinned at their resolved commits; CodeQL action bumped to v4.35.5;
  CI matrix JDK 17 → 21 LTS.
- `gradle.properties` now enforces `kotlin.jvm.target.validation.mode=error`
  — Kotlin/Java JVM target mismatch is a build-time hard fail at
  config time, protecting the `javaLangTarget=1.8` invariant for
  the `kotlinMin=1.7.22` consumer floor.
- CI hardening sweep (sibling-aligned with `fluxo-kmp-conf`):
  - `build.yml`: `permissions.contents` downgraded from `write` to
    `read`; new workflow-level `concurrency` block cancels superseded
    matrix runs (saves runner minutes on rapid PR iteration).
  - `pr-baseline.yml`: replaced `git push --force` with
    `--force-with-lease=ref:sha` — race-protected against concurrent
    contributor pushes between workflow checkout and baseline push.
  - `clear_cache.yml`: added `step-security/harden-runner` with
    `egress-policy: block` allowlisting only `api.github.com:443`.
  - `pr-clean-cache.yml`: pinned the `gh-actions-cache` extension
    install to `--pin v1.0.4` (removes a silent supply-chain
    surface).
  - New `dependency-submission.yml` workflow: dedicated graph
    submission on default-branch pushes only. Decouples
    graph-submit failures from the build-matrix outcome and
    enabled the build.yml `contents:read` tightening above.

### Fixed
- **Publish-time main POM regression** — fluxo-kmp-conf 0.14.x's
  `PluginMavenPublication` stopped wiring
  `<name>/<description>/<url>/<inceptionYear>/<licenses>/<developers>/<scm>`
  into the main POM (1.0.x shipped with all of them). Restored
  inline via `publishing.publications.withType<MavenPublication> {
  pom { ... } }`. Plugin Portal accepts incomplete POMs but Maven
  Central / Sonatype OSSRH do not — restoring keeps the door open
  for a 1.2.0 Central publication path and is industry-best-practice
  regardless.
- **Main artifactId restored to `fluxo-bcv-ts`** — same fluxo-kmp-conf
  0.14.x surface change started rewriting `artifactId = projectName`,
  which would have shipped 1.1.0 as `io.github.fluxo-kt:plugin`
  (a meaningless coordinate that could collide with anything in the
  group). 1.0.x's `io.github.fluxo-kt:fluxo-bcv-ts` contract is
  preserved. Plugin-block consumers are unaffected (marker
  indirection routes the resolution); direct-Maven-coord consumers
  stay aligned with 1.0.x.
- **`project.version` propagation fix** — fluxo-kmp-conf 0.14.x
  configures its own `publicationConfig.version` (which targets the
  main publication only) but does NOT propagate the value back to
  `project.version`. The auto-generated plugin marker POM (which
  Plugin Portal uses to resolve `plugins { id("...") }` requests)
  reads `project.version` directly, so without an explicit
  assignment the marker shipped as `<version>unspecified</version>`.
  Set `version = pluginVersion` after `fkcSetupGradlePlugin`.
- **Sigstore signing local-publish blocker** — `dev.sigstore.sign`
  auto-wires `sigstoreSign*Publication` tasks into every
  `MavenPublication`'s publish chain, which made
  `./gradlew :plugin:publishToMavenLocal` block on a browser-OIDC
  prompt. Gated the tasks on `RELEASE=true` env var (matching
  release.yml) and marked them
  `notCompatibleWithConfigurationCache(...)` because the upstream
  v2.0.x task captures a `DefaultProject` reference. Local dev can
  force-test signing via `RELEASE=true ./gradlew ...`.
- Version-parser bug in `isVersionGreaterThanOrEqual` — pre-release
  suffixes like `1.7.22-Beta1` were incorrectly rejected against
  the floor when the suffix sat on the differentiating numeric
  position. Replaced the bespoke parser with
  `org.gradle.util.GradleVersion.baseVersion`.
- `KOTLIN_MIN_VERSION` is now code-generated from
  `libs.versions.toml::kotlinMin` — drift between source and matrix
  is impossible by construction.
- Wasm-JS target's `.d.mts` (ECMAScript Module form) declarations are
  now recognised alongside `.d.ts` — Kotlin/Wasm-JS started emitting
  `.d.mts` somewhere in the 2.x lineage; the dump output stays
  canonical `.d.ts`.
- `jitpack.yml` and the README's JitPack snippet referenced the
  pre-rename subproject path (`:fluxo-bcv-js:publishToMavenLocal`,
  `useModule("...:fluxo-bcv-js:...")`); both now use `:plugin:`.
- Obsolete `systemProp.org.gradle.internal.publish.checksums.insecure`
  flag removed (originated from a 2023 Sonatype OSSRH workaround,
  no longer applicable). `gradle.properties` cleaned of redundant
  Kotlin incremental flags and the deprecated `org.gradle.unsafe.watch-fs`
  alias of `vfs.watch`.

### Removed
- The `KotlinApiCompareTask`-wrapping branch in `ConfigureTsApiTasks.kt`
  — replaced by exclusive use of the plugin's own `KotlinTsApiCompareTask`
  (a JVM-side `java-diff-utils`-based comparator that works against
  every supported BCV version). The wrapping path was unsound across
  the BCV 0.8 - 0.18 surface drift.
- `DBG` debug-logging constant — diagnostics now route through
  Gradle's logger (`./gradlew apiCheck --debug` surfaces drift).
- `gradle-wrapper-validation.yml` workflow — folded into
  `gradle/actions/setup-gradle@v6` (`validate-wrappers: true`).

### Updated
- **bump Kotlin to _2.3.21_** (build-side; `kotlinLatest = 2.4.0-RC`
  drives the matrix top via `checks/latest`).
- **bump latest supported [BCV][bcv] to _0.18.1_** (frozen ceiling).
- bump Gradle wrapper to _9.5.1_ (SHA-pinned).
- bump `fluxo-kmp-conf` to _0.14.1_; bump `Dokka` to _2.2.0_;
  bump `gradle-plugin-publish` to _2.1.1_; bump `gradle-doctor` to
  _0.12.1_; bump `android-lint` from _8.6.0-alpha05_ to _9.2.1_
  stable.
- Tightened CC: `org.gradle.configuration-cache.problems=fail`,
  `max-problems=0` — the root build now refuses to ship with any
  configuration-cache violation.


## [1.0.0] - 2024-06-10

### Updated
- **bump Kotlin to _2.0.0_**.
- **bump minimal supported Kotlin version to _1.7.22_**!
- **bump latest supported [BCV][bcv] to _0.15.0-Beta.2_**.

### Changed
- change JS in the plugin name and class package to TS. Plugin ID is unchanged!

### Added
- add support for the configurable api dump directory appeared in [BCV][bcv] _0.14.0_.

### Changed
- Rename "JS" references to "TS" to avoid collisions with [BCV][bcv] _0.15.0_.
  Changed task names, log messages, and documentation.


## [0.3.0] - 2024-04-28

### Updated
- **bump [BCV][bcv] to _0.14.0_**.
- bump Kotlin to _1.9.23_.
- bump Gradle to _8.7_.
- bump `dependency-guard` to _0.5.0_.
- bump `fluxo-kmp-conf` to _0.8.0_.

### Changed
- change the minimum tested Gradle version to `7.6`.


## [0.2.0] - 2023-09-16

_Minor update release._

### Changed
- Update the [README.md](README.md).
- Bump build and CI dependencies.


## [0.1.0] - 2023-05-25

🌱 _First stable release._

### Added
- Add Kotlin/TypeScript API support to the KotlinX [Binary Compatibility Validator][bcv] based on the generated TS definitions.
  Fixes [Kotlin/binary-compatibility-validator#42](https://github.com/Kotlin/binary-compatibility-validator/issues/42)
- Add support for Kotlin/JS non-KMP projects.
- Add compatibility with [BCV][bcv] 0.8+
- Add line endings normalization for TS definition files.
- Add documentation and usage examples.


## [0.0.1-rc] - 2023-05-25

🌱 _Initial `fluxo-bcv-js` pre-release in the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.fluxo-kt.binary-compatibility-validator-js)._


## Notes

[1.1.0]: https://github.com/fluxo-kt/fluxo-bcv-js/releases/tag/v1.1.0
[1.0.0]: https://github.com/fluxo-kt/fluxo-bcv-js/releases/tag/v1.0.0
[0.3.0]: https://github.com/fluxo-kt/fluxo-bcv-js/releases/tag/v0.3.0
[0.2.0]: https://github.com/fluxo-kt/fluxo-bcv-js/releases/tag/v0.2.0
[0.1.0]: https://github.com/fluxo-kt/fluxo-bcv-js/releases/tag/v0.1.0
[0.0.1-rc]: https://github.com/fluxo-kt/fluxo-bcv-js/releases/tag/v0.0.1-rc

[bcv]: https://github.com/Kotlin/binary-compatibility-validator

[^1]: Uses [Common Changelog style](https://common-changelog.org/) [^2]
[^2]: https://github.com/vweevers/common-changelog#readme
