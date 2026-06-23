# Security Policy

## Supported versions

This is a stop-gap build-time Gradle plugin (see [`README.md`](README.md)).

| Version        | Supported |
|----------------|:---------:|
| latest release |     ✅     |
| older          |     ❌     |

## Reporting a vulnerability

Report privately via GitHub: **Security → Advisories → _Report a
vulnerability_** (private vulnerability reporting is enabled on this repo).
Please do not open a public issue for security reports.

## Scope

This plugin runs only at **build time**; its sole runtime dependency is
`java-diff-utils` (the Kotlin runtime is Gradle-supplied, not bundled).
Release artifacts are Sigstore-signed (bundles on each GitHub Release).
