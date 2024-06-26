# Changelog [^1]


## Unreleased

[//]: # (Changed, Added, Removed, Fixed, Updated)


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

[1.0.0]: https://github.com/fluxo-kt/fluxo-bcv-js/releases/tag/v1.0.0
[0.3.0]: https://github.com/fluxo-kt/fluxo-bcv-js/releases/tag/v0.3.0
[0.2.0]: https://github.com/fluxo-kt/fluxo-bcv-js/releases/tag/v0.2.0
[0.1.0]: https://github.com/fluxo-kt/fluxo-bcv-js/releases/tag/v0.1.0
[0.0.1-rc]: https://github.com/fluxo-kt/fluxo-bcv-js/releases/tag/v0.0.1-rc

[bcv]: https://github.com/Kotlin/binary-compatibility-validator

[^1]: Uses [Common Changelog style](https://common-changelog.org/) [^2]
[^2]: https://github.com/vweevers/common-changelog#readme
