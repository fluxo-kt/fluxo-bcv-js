# Fluxo-BCV-JS
## Binary Compatibility Validator for Kotlin/JS by [Fluxo][fluxo]

[![Gradle Plugin Portal][badge-plugin]][plugin]
[![JitPack][badge-jitpack]][jitpack]
[![Build](../../actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)
[![Common Changelog](https://common-changelog.org/badge.svg)](CHANGELOG.md)

[![KotlinX BCV Compatibility](http://img.shields.io/badge/KotlinX%20BCV-0.8%20--%200.13-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B)][bcv]
[![Kotlin Compatibility](http://img.shields.io/badge/Kotlin-1.6.20+-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B)](https://github.com/JetBrains/Kotlin)
[![Gradle Compatibility](http://img.shields.io/badge/Gradle-7.4+-f68244?logo=gradle&labelColor=2B2B2B)](https://gradle.org/releases/)

A tiny Gradle plugin that adds JS/TS API support to the
KotlinX [Binary Compatibility Validator][bcv] (BCV).

Can be used with any Gradle module with **Kotlin/JS** target.
Either [Kotlin Multiplatform][KMM] or [Kotlin/JS][KJS].

Integrates well with the default BCV pipeline, providing more features with same Gradle tasks.

As [mentioned](https://github.com/Kotlin/binary-compatibility-validator/issues/42#issuecomment-1435031047)
in the Kotlin/binary-compatibility-validator#42, the Kotlin team is not yet ready to accept
a contribution for JS/TS support, or even to do due diligence
and see if this is a reasonable addition for the future.

The tool allows dumping TypeScript definitions of a JS part of a Kotlin multiplatform library
that's public in the sense of npm package visibility, and ensures that the public definitions
haven't been changed in a way that makes this change binary incompatible.

This plugin will be supported until the official Kotlin/JS support is added to [BCV][bcv].

Initially [was made][fluxo-bcv-commit] for the [Fluxo][fluxo] state management framework,
but then published for general use.


### Compatibility

Kotlin supports generation of TypeScript declarations [since 1.6.20](https://kotlinlang.org/docs/whatsnew1620.html#improvements-to-export-and-typescript-declaration-generation)
Compatibility tested with:

| Version |    BCV     | Kotlin  | Gradle |
|:-------:|:----------:|:-------:|:------:|
|  0.1.0  | 0.8 - 0.13 | 1.6.20+ |  7.4+  |


### How to use

[![Gradle Plugin Portal][badge-plugin]][plugin]

```kotlin
// in the `build.gradle.kts` of the target module
plugins {
  kotlin("multiplatform") version "1.9.10" // <-- multiplatform or js, versions from 1.6.20 to 1.9
  id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.13.2" // <-- 0.8 .. 0.13
  id("io.github.fluxo-kt.binary-compatibility-validator-js") version "0.1.0" // <-- add here
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
// in the `build.gradle.kts` of the target module
plugins {
  kotlin("multiplatform") version "1.9.10" // <-- multiplatform or js, versions from 1.6.20 to 1.9
  id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.13.2" // <-- 0.8 .. 0.13
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
      useModule("com.github.fluxo-kt.fluxo-bcv-js:fluxo-bcv-js:e072120070") // <-- specify version or commit
  }
}
```
</details>

Module examples for:
- [Kotlin Multiplatform](checks/latest/build.gradle.kts)
- [Kotlin/JS](checks/js-only/build.gradle.kts)


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
