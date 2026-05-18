# Fluxo-BCV-TS
## Binary Compatibility Validator for Kotlin/TypeScript definitions (JS, WASM targets)

[![Gradle Plugin Portal][badge-plugin]][plugin]
[![JitPack][badge-jitpack]][jitpack]
[![Build](../../actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)
[![Common Changelog](https://common-changelog.org/badge.svg)](CHANGELOG.md)

[![KotlinX BCV Compatibility](http://img.shields.io/badge/KotlinX%20BCV-0.8%20--%200.18.1-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B)][bcv]
[![Kotlin Compatibility](http://img.shields.io/badge/Kotlin-1.7.22+-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B)](https://github.com/JetBrains/Kotlin)
[![Gradle Compatibility](http://img.shields.io/badge/Gradle-7.6+-f68244?logo=gradle&labelColor=2B2B2B)](https://gradle.org/releases/)

A tiny Gradle plugin that adds TypeScript (JS) API support to the
KotlinX [Binary Compatibility Validator][bcv] (BCV).

Can be used with any Gradle module with **Kotlin/JS** or **Kotlin/WASM** target.
Either [Kotlin Multiplatform][KMM] or [Kotlin/JS][KJS].

Doesn't conflict with the KLIB API dumps.

Integrates with the default BCV pipeline, providing more features for the same Gradle tasks.

As [mentioned](https://github.com/Kotlin/binary-compatibility-validator/issues/42#issuecomment-1435031047)
in the Kotlin/binary-compatibility-validator#42, the Kotlin team is not yet ready to accept
a contribution for JS/TS support, or even to do due diligence
and see if this is a reasonable addition for the future.

The tool allows dumping TypeScript definitions of a JS or WASM part
of a Kotlin multiplatform library that's public in the sense
of npm package visibility.
And ensures that the public definitions haven't been changed in a way
that makes this change binary incompatible.

This plugin will be supported until the official Kotlin/JS support is added to [BCV][bcv].

Initially [was made][fluxo-bcv-commit] for the [Fluxo][fluxo] state management framework,
but then published for general use.


### Compatibility

Kotlin has supported generation of TypeScript declarations [since 1.6.20](https://kotlinlang.org/docs/whatsnew1620.html#improvements-to-export-and-typescript-declaration-generation).
Compatibility tested with:

|  Version   |       BCV[^1]        | Kotlin  | Gradle |
|:----------:|:--------------------:|:-------:|:------:|
|  1.1.0[^2] | 0.8 - 0.18.1 OR KGP-embedded[^3] | 1.7.22+ |  7.6+  |
|   1.0.0    |      0.8 - 0.15      | 1.7.22+ |  7.6+  |
|   0.3.0    |      0.8 - 0.14      | 1.6.20+ |  7.6+  |
|   0.2.0    |      0.8 - 0.13      | 1.6.20+ |  7.4+  |

[^1]: "BCV" denotes the ABI-validation source — until 1.0.x only the external [KotlinX Binary Compatibility Validator][bcv]; in 1.1.0 also KGP-embedded `abiValidation { }` (Kotlin 2.2+).
[^2]: External KotlinX BCV is [frozen at 0.18.1](https://github.com/Kotlin/binary-compatibility-validator/tree/0.18.1) — `0.18.1` is the physical ceiling, not an arbitrary pin.
[^3]: KGP-embedded `abiValidation { }` activates the embedded mode without applying the external BCV plugin. Requires Kotlin 2.2+ and the consumer-side `@OptIn(ExperimentalAbiValidation::class)` ceremony. See [Dual-mode usage](#dual-mode-usage-110) below.


### How to use

[![Gradle Plugin Portal][badge-plugin]][plugin]

```kotlin
// in the `build.gradle.kts` of the target module.
plugins {
  kotlin("multiplatform") version "2.3.21" // <-- 1.7 .. 2.3 (CI also exercises 2.4.0-RC)
  id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1" // <-- 0.8 .. 0.18.1
  id("io.github.fluxo-kt.binary-compatibility-validator-js") version "1.1.0" // <-- add here
}
kotlin {
  js(IR) {
    binaries.executable() // required to generate TS definitions
    nodejs() // or browser()
  }
}
```

<details>
<summary>How to use snapshots from JitPack repository</summary>

[![JitPack][badge-jitpack]][jitpack]

```kotlin
// in the `build.gradle.kts` of the target module.
plugins {
  kotlin("multiplatform") version "2.3.21" // <-- 1.7 .. 2.3 (CI also exercises 2.4.0-RC)
  id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1" // <-- 0.8 .. 0.18.1
  id("io.github.fluxo-kt.binary-compatibility-validator-js") // <-- add here, no version needed for jitpack usage
}
kotlin {
  js(IR) {
    binaries.executable() // required to generate TS definitions
    nodejs() // or browser()
  }
}
```
```kotlin
// in the `settings.gradle.kts` of the project
pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://jitpack.io") // <-- add jitpack repo
  }
  resolutionStrategy.eachPlugin {
    if (requested.id.toString() == "io.github.fluxo-kt.binary-compatibility-validator-js")
      useModule("com.github.fluxo-kt.fluxo-bcv-js:plugin:dee48ac65c") // <-- specify a version, or a commit.
  }
}
```
</details>

Module examples for:
- [Latest setup](checks/latest/build.gradle.kts)
- [Oldest setup](checks/js-only/build.gradle.kts)
- [Embedded-only setup](checks/kgp-only/build.gradle.kts) (KGP `abiValidation`, no external BCV)
- [Dual-mode setup](checks/dual/build.gradle.kts) (both validators active)


### Dual-mode usage (1.1.0+)

The external [KotlinX BCV][bcv] plugin is [frozen since 0.18.1](https://github.com/Kotlin/binary-compatibility-validator/tree/0.18.1). To survive the transition to KGP-embedded `abiValidation { }`, the plugin now activates on **either** trigger source:

- **external mode** — the external `org.jetbrains.kotlinx.binary-compatibility-validator` plugin is applied (the 1.0.x behaviour).
- **embedded mode** — KGP-native `kotlin { abiValidation { } }` is configured (Kotlin 2.2+, opt-in via `@OptIn(ExperimentalAbiValidation::class)`).

When **both** are present, by default the external plugin is preferred for backward-compatibility; a one-shot lifecycle hint recommends opting into embedded mode. The path is selectable via the `fluxoBcvTs` extension:

```kotlin
// in the `build.gradle.kts` of the target module.
plugins {
  kotlin("multiplatform") version "2.3.21"
  id("io.github.fluxo-kt.binary-compatibility-validator-js") version "1.1.0"
  // NOTE: NO `org.jetbrains.kotlinx.binary-compatibility-validator` —
  // embedded mode replaces the external plugin entirely.
}

@OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
kotlin {
  abiValidation { } // activates KGP-embedded validation
  js(IR) {
    binaries.executable()
    nodejs()
  }
  // … other targets …
}

// Optional — only needed when BOTH external BCV and embedded
// `abiValidation` are applied simultaneously, e.g. during migration.
fluxoBcvTs {
  preferEmbedded.set(true)  // force the embedded trigger to win
  // wireToKgpAbi.set(true)   // also run `:apiCheck` when `:checkKotlinAbi` runs (default: false)
}
```

The plugin emits a single machine-parseable lifecycle line per build invocation indicating the resolved path:

```
[fluxo-bcv-ts] trigger=external|embedded preferEmbedded=auto|true|false
```

The `fluxoBcvTs` extension is marked `@Incubating`. It may change in any 1.x minor release; the stability commitment moment is targeted for 1.2.0. The KGP-side `abiValidation` extension is itself `@OptIn(ExperimentalAbiValidation::class)` — that ceremony is consumer-side and is independent of `fluxoBcvTs`.


### Versioning

Uses [SemVer](http://semver.org/) for versioning.


### License

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

This project is licensed under the Apache License, Version 2.0 — see the
[license](LICENSE) file for details.


[bcv]: https://github.com/Kotlin/binary-compatibility-validator

[KMM]: https://kotlinlang.org/docs/multiplatform-get-started.html
[KJS]: https://kotlinlang.org/docs/js-project-setup.html

[plugin]: https://plugins.gradle.org/plugin/io.github.fluxo-kt.binary-compatibility-validator-js
[badge-plugin]: https://img.shields.io/gradle-plugin-portal/v/io.github.fluxo-kt.binary-compatibility-validator-js?label=Gradle%20Plugin&logo=gradle

[jitpack]: https://www.jitpack.io/#fluxo-kt/fluxo-bcv-js
[badge-jitpack]: https://www.jitpack.io/v/fluxo-kt/fluxo-bcv-js.svg

[fluxo]: https://github.com/fluxo-kt/fluxo
[fluxo-bcv-commit]: https://github.com/fluxo-kt/fluxo/commit/252e5d859078ea28e5bf496067424b0b5b5c8f73
