# Security Policy

## Supported versions

This is a stop-gap build-time Gradle plugin (see [`README.md`](README.md)).
Fixes land only on the latest published release; older versions are not
patched.

| Version        | Supported |
|----------------|:---------:|
| latest release |     ✅     |
| older          |     ❌     |

## Reporting a vulnerability

Report privately via GitHub: **Security → Advisories → _Report a
vulnerability_** (private vulnerability reporting is enabled on this repo).
Please do not open a public issue for security reports.

## Scope

The plugin runs only at **build time**. Its sole declared runtime
dependency is `io.github.java-diff-utils:java-diff-utils` — the Kotlin
runtime is supplied by Gradle, and `kotlin-stdlib*`/metadata are excluded
from the published POM and Gradle module metadata, so the build compiler
cannot leak downstream. Release artifacts carry Sigstore provenance
(bundles attached to each GitHub Release; verify with `cosign
verify-blob`).
