# [Fluxo](https://github.com/fluxo-kt/fluxo) BCV for JS

[![Gradle Plugin Portal][badge-plugin]][plugin]
[![KotlinX BCV][badge-bcv]][bcv-tag]
[![Build](../../actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)

![KotlinX BCV Compatibility](http://img.shields.io/badge/KotlinX%20BCV-0.12%20--%200.13-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B)
![Kotlin Compatibility](http://img.shields.io/badge/Kotlin-1.4%20--%201.8-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B)
![Gradle Compatibility](http://img.shields.io/badge/Gradle-7.4%20--%208.1-f68244?logo=gradle)

A tiny Gradle plugin that adds JS/TS API support to the
KotlinX [Binary Compatibility Validator][bcv].

The tool allows dumping TypeScript definitions of a JS part of a Kotlin multiplatform library
that's public in the sense of npm package visibility, and ensures that the public definitions
haven't been changed in a way that makes this change binary incompatible.

Initially made for the [Fluxo][fluxo] state management framework,
but then prepared for general use.

### Compatibility

| Version |     BCV     |  Kotlin   |  Gradle   |
|:-------:|:-----------:|:---------:|:---------:|
|  0.0.1  | 0.12 - 0.13 | 1.4 - 1.8 | 7.4 - 8.1 |

### Versioning

Uses [SemVer](http://semver.org/) for versioning.

### License

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

This project is licensed under the Apache License, Version 2.0 — see the
[license](LICENSE) file for details.


[bcv]: https://github.com/Kotlin/binary-compatibility-validator
[bcv-tag]: https://github.com/Kotlin/binary-compatibility-validator/releases/tag/0.12.1
[badge-bcv]: http://img.shields.io/badge/KotlinX%20BCV-0.12.1-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B

[plugin]: https://plugins.gradle.org/plugin/fluxo.kotlinx.binary-compatibility-validator.js
[badge-plugin]: https://img.shields.io/gradle-plugin-portal/v/fluxo.kotlinx.binary-compatibility-validator.js?label=Gradle%20Plugin&logo=gradle

[fluxo]: https://github.com/fluxo-kt/fluxo
