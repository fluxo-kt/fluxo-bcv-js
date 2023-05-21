# [Fluxo](https://github.com/fluxo-kt/fluxo) BCV for JS

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/me.champeau.jmh?label=Gradle%20Plugin&logo=gradle)](https://plugins.gradle.org/plugin/me.champeau.jmh)
[![KotlinX BCV](http://img.shields.io/badge/KotlinX%20BCV-0.12.1-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B)](https://github.com/Kotlin/binary-compatibility-validator/releases/tag/0.13.1)

A tiny Gradle plugin that adds JS/TS API support to the KotlinX [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator).

The tool allows dumping TypeScript definitions of a JS part of a Kotlin multiplatform library that's public in the sense of npm package visibility, and ensures that the public definitions haven’t been changed in a way that makes this change binary incompatible.

Initially made for the [Fluxo](https://github.com/fluxo-kt/fluxo) state management framework, but then prepared for general use.

### Versioning

Uses [SemVer](http://semver.org/) for versioning.

### License

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

This project is licensed under the Apache License, Version 2.0 — see the
[license](LICENSE) file for details.
