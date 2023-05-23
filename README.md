# Binary Compatibility Validator for Kotlin/JS by [Fluxo][fluxo]

[![Gradle Plugin Portal][badge-plugin]][plugin]
[![JitPack][badge-jitpack]][jitpack]
[![Build](../../actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)

[![KotlinX BCV Compatibility](http://img.shields.io/badge/KotlinX%20BCV-0.12%20--%200.13-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B)][bcv]
[![Kotlin Compatibility](http://img.shields.io/badge/Kotlin-1.4+-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B)](https://github.com/JetBrains/Kotlin)
[![Gradle Compatibility](http://img.shields.io/badge/Gradle-7.4+-f68244?logo=gradle&labelColor=2B2B2B)](https://gradle.org/releases/)

A tiny Gradle plugin that adds JS/TS API support to the
KotlinX [Binary Compatibility Validator][bcv].

The tool allows dumping TypeScript definitions of a JS part of a Kotlin multiplatform library
that's public in the sense of npm package visibility, and ensures that the public definitions
haven't been changed in a way that makes this change binary incompatible.

Initially was made for the [Fluxo][fluxo] state management framework,
but then prepared for general use.

As [mentioned](https://github.com/Kotlin/binary-compatibility-validator/issues/42#issuecomment-1435031047)
in the Kotlin/binary-compatibility-validator#42, the Kotlin team is not yet ready to accept
a contribution for JS/TS support, or even to do due diligence
and see if this is a reasonable addition for the future.

This plugin will be supported until the official Kotlin/JS support is added to [BCV][bcv].


### Compatibility

| Version |     BCV     | Kotlin | Gradle |
|:-------:|:-----------:|:------:|:------:|
|  0.0.1  | 0.12 - 0.13 |  1.4+  |  7.4+  |


### How to use

[![JitPack][badge-jitpack]][jitpack]

Plugin can be used from the [JitPack][jitpack] like this:

```kotlin
// in the `build.gradle.kts` of the target module
plugins {
    id("io.github.fluxo-kt.binary-compatibility-validator-js")
}
```
```kotlin
// in the `settings.gradle.kts` of the project
pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://jitpack.io")
  }
  resolutionStrategy.eachPlugin {
    if (requested.id.toString() == "io.github.fluxo-kt.binary-compatibility-validator-js")
      useModule("com.github.fluxo-kt.fluxo-bcv-js:fluxo-bcv-js:56f7ac7af7") // Specify version
  }
}
```


### Versioning

Uses [SemVer](http://semver.org/) for versioning.


### License

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

This project is licensed under the Apache License, Version 2.0 — see the
[license](LICENSE) file for details.


[bcv]: https://github.com/Kotlin/binary-compatibility-validator
[bcv-tag]: https://github.com/Kotlin/binary-compatibility-validator/releases/tag/0.12.1
[badge-bcv]: http://img.shields.io/badge/KotlinX%20BCV-0.12.1-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B

[plugin]: https://plugins.gradle.org/plugin/io.github.fluxo-kt.binary-compatibility-validator-js
[badge-plugin]: https://img.shields.io/gradle-plugin-portal/v/io.github.fluxo-kt.binary-compatibility-validator-js?label=Gradle%20Plugin&logo=gradle

[jitpack]: https://www.jitpack.io/#fluxo-kt/fluxo-bcv-js
[badge-jitpack]: https://www.jitpack.io/v/fluxo-kt/fluxo-bcv-js.svg

[fluxo]: https://github.com/fluxo-kt/fluxo
