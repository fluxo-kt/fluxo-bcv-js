# AGENTS.md — fluxo-bcv-js CI / workflows / release

Workflow, release & supply-chain specifics. **Auto-loads only when touching
`.github/`** — root `../AGENTS.md` carries plugin/build/`checks/` knowledge.
CI here is the **recurrence-prevention** from a ~6-month CI outage; the gotchas
below are the scars. ⚠ **Hit something surprising in CI? Add it here AND tell
the user.**

## Workflows
- `build.yml`: builds EVERY PR (any base) + push to `main`/`dev` only (no tags —
  a `v*` tag sits on already-built `main` HEAD). PRs are the gateable pre-merge
  check; concurrency group de-dupes. **NEVER re-add `pull_request:
  branches-ignore`** — it matches the PR *base*, so ignoring `dev` (default
  branch) silently disables builds for every feature PR.
- `codeql.yml`: SAST, advisory, NOT required; builds ONLY `:plugin` (see below).
- `release.yml`: `v*` tags. `pr-fast-forward.yml`: `/ff` merge via PR comment.
  `pr-baseline.yml`: baseline regen via comment. `dependency-submission.yml`:
  submits dep graph that feeds Dependabot alerts.
- Flow: branch off `dev` → PR → `/ff`-merge to `dev` → release PR `dev`→`main`.
  Conventional Commits enforced.

## Default branch = `dev`, NOT `main`
Dependabot alerts + dep graph are scoped to the default branch ⇒ a CI/
supply-chain fix CLEARS alerts once it ff-merges to `dev`; no `main` round-trip.
`main` is the release branch (dev→main release PR).

## Dependabot — two ecosystems, two paths
- **Gradle bump-PR** → mergeable. If a Gradle dep changed (esp. KGP / BCV /
  `fluxo-kmp-conf`), run `./updateBaseline` before merge (refreshes
  `dependencies/*.txt` + any drifted api dumps).
- **GH-Actions bump-PR** → **NEVER merge directly.** Kept ONLY as a *detection
  signal*. Authoritative channel = `actions-up` CLI (`actions-up --style sha
  --mode major --yes` — SHA-pins with a version comment, the provenance a bare
  dependabot `uses:` bump lacks). Apply, push to `dev`, then **close** the
  dependabot PR. Never hand-edit `uses:` SHAs (no provenance trail).

## Branch ruleset `protect-integration-branches`
Protects `dev`+`main`. **NO in-repo file** (GitHub-side config) — query via
`gh api repos/<owner>/<repo>/rulesets` (don't hardcode the numeric id; it
changes on recreate). Required contexts = EXACTLY build.yml matrix job names
`Build and check on {ubuntu,macos,windows}`, each pinned `integration_id: 15368`
(github-actions) so a same-named context from another app can't satisfy the
gate. Uses the `non_fast_forward` rule (blocks force-push), NOT `pull_request`
— so the `/ff` bot's legit ff-push of an already-green SHA still merges.
`bypass_actors=[{OrganizationAdmin, always}]`: the solo founder (org owner)
direct-pushes `dev`/`main` with no PR ceremony, while the `/ff` bot (a
github-actions *app*, not an org-admin user) + external contributors stay gated
by required checks ⇒ a red dependabot/feature *auto-merge* is still blocked (the
recurrence target). Reset `bypass_actors=[]` to re-impose PR-only on everyone.
CodeQL is deliberately NOT required (advisory).
- **Corollary trap — NEVER add a file-path filter (`paths-ignore` OR `paths`)
  to build.yml's `pull_request` trigger.** An `on:`-level path filter emits no
  check runs for a non-matching PR → the required contexts never report → every
  docs-only PR is PERMANENTLY BLOCKED from merge (reproduced PR #48). Both keys
  skip identically ⇒ the invariant is "no path filter on the required trigger",
  machine-enforced by build.yml's "Forbid path filters on the pull_request
  trigger" step. The `push` trigger has NO `paths-ignore` either (owner
  direct-pushes are its only pushes — `/ff` is GITHUB_TOKEN-suppressed; the
  badge reads dev HEAD's build check-run → a docs-only push must still build).
  Rename a build.yml matrix job NAME ⇒ update the ruleset's required contexts in
  lockstep, or the gate silently stops matching.

## README build badge = shields.io check-runs badge, NOT the Actions badge
The workflow badge (`build.yml/badge.svg`) shows the latest *run on the branch*;
under bot-`/ff` no build.yml run ever lands on protected `dev` (GITHUB_TOKEN
push suppression) → it froze on a stale RED run while every PR was green. The
shields badge reads dev HEAD's per-SHA check status (same source the ruleset
gates on, always fresh, zero compute):
`…/github/check-runs/<owner>/<repo>/dev?nameFilter=Build%20and%20check%20on%20ubuntu&label=Build`.
`nameFilter` is EXACT-match → pins ubuntu (the strictest cell — only it runs
`check-dual`; all 3 OS are ruleset-required on an immutable-between-merges `dev`
⇒ ubuntu-green ⟺ all-green; EXCLUDES advisory CodeQL). A job rename detaches the
badge → renders grey "unknown" (NOT red) → update `nameFilter` in lockstep.
Tracks `dev`, not `main`.

## GITHUB_TOKEN push suppression (load-bearing across 3 workflows)
GitHub does NOT fire `push:`/`pull_request:closed`-triggered workflows on
GITHUB_TOKEN pushes (recursion guard). The `/ff` bot merges via a GITHUB_TOKEN
push, so:
- **`dependency-submission.yml` MUST keep `workflow_dispatch` + `schedule`** —
  its `push:` never fires on a bot-merged `dev`, so the graph would rot → stale/
  late Dependabot alerts. After a dep-changing merge, refresh now: `gh workflow
  run dependency-submission.yml --ref dev` (your token, not suppressed); the
  weekly `schedule` is the hands-off backstop.
- **`pr-clean-cache.yml` only reaps MANUALLY-closed PRs**, never `/ff` merges
  (bot push doesn't fire `pull_request:closed`). `/ff`-merged `refs/pull/N/merge`
  caches are reaped by GitHub's 7-day eviction instead — sufficient & intentional
  (storage-only hygiene, well under the 10 GB cap; LRU evicts
  least-recently-*accessed*, so the hot `dev` baseline is the last victim, not
  the first). Do NOT "fix" by adding `gh cache delete` to pr-fast-forward.yml
  (marginal gain) or pushing `/ff` via a PAT (long-lived credential liability).
  Uses native `gh cache delete` (the old `actions/gh-actions-cache` extension's
  binary is egress-blocked at `release-assets.githubusercontent.com`).
- build.yml needs no dispatch/schedule — required checks read the PR-run contexts
  already attached to the merged SHA.

## `${{ !env.X }}` / bare `env.X` in a boolean position = constant, not condition
GitHub coerces a non-empty string → `true`, and an `env:` value is ALWAYS a
string, so the literal `"false"` is truthy. `cache-read-only: ${{
!env.IS_DEFAULT_BRANCH }}` pinned to a constant `false`; `if: … ||
env.IS_DEFAULT_BRANCH` was always-true. actionlint does NOT flag it (valid
syntax, dead semantics). Use the boolean comparison directly (`github.ref ==
format('refs/heads/{0}', …)`) or `env.X == 'true'`; never negate/branch on a
bare string env.

## Maven Dependabot alerts on build tooling ⇒ fix the dep-graph filter, NOT deps
`dependency-submission.yml` submits `include-configurations: "^runtimeClasspath$"`
(positive allow-list = shipped deps only). A negative name filter (the old
`^(?!(classpath)).*`) leaks the settings plugin-resolution classpath
(Develocity→protobuf/grpc; Sigstore/kmp-conf→BouncyCastle; commons-io) because
it isn't named `classpath`. If a NEW never-shipped coord alerts under
`manifest=settings.gradle.kts`, the filter regressed — don't bump/dismiss the dep.

## CodeQL advisory (`codeql.yml`)
The java-kotlin extractor hard-fails (`KotlinVersionTooRecentError`) on Kotlin
newer than its bundled ceiling — github/codeql pins one compiled extractor per
Kotlin (`versions.bzl`), no skip switch. While CodeQL ran in `build.yml` it
traced the `checks/*` smoke builds (compiling `kotlinLatest`, an RC) → killed the
matrix ~6mo. So it lives alone, builds ONLY `:plugin` (at repo Kotlin, under the
ceiling; `checks/*` are fixtures, not scan targets). Build step needs
`--no-daemon` + `-Pkotlin.compiler.execution.strategy=in-process` (both daemons
escape tracing) + `--no-build-cache --rerun-tasks` (a cache hit ⇒ empty DB / "no
source seen"). Action SHA-pinned but `tools:` omitted so the bundle/ceiling
floats. **Advisory by design: goes red when repo Kotlin crosses the ceiling**
(currently red — repo at 2.4.0 > extractor ceiling 2.3.30, verbatim `Kotlin
version 2.4.0 is too recent … supports versions below 2.3.30`), never blocks
merges.

## No OSSF Scorecard (added 1.1.0, removed `5d7bff3`)
Its generic repo-hygiene checklist is structurally N/A for a solo stop-gap:
nearly every finding was noise (CodeReview/Fuzzing/CII want a team;
BinaryArtifacts flags the *required* wrapper jars; Token-Permissions flags the
*necessary* `security-events: write`). Useful checks are covered elsewhere —
pinning via `actions-up`, freshness via Dependabot; the lone real gap
(SecurityPolicy) is closed by `SECURITY.md`. **Decision rule (vs CodeQL, kept):
advisory tooling earns its keep only when findings are actionable for THIS repo
— Scorecard's never were, so don't re-add.**

## Release (`release.yml`) — Sigstore + idempotency
*(Why signing is RELEASE-gated, in `build.gradle.kts` → root AGENTS.md.)*
- **Idempotent vs Plugin Portal duplicates**: a "Probe Plugin Portal for
  existing version" step short-circuits `publishPlugins` when the marker POM
  already resolves at the tagged version; Sigstore still fires via the alternate
  "Sign artefacts (when publish was skipped)" path, so re-tags produce valid
  bundles (OIDC identity stays `release.yml@refs/tags/v*`). Eliminates "tag
  pushed but downstream step failed → can't re-push the same tag": delete tag,
  re-tag, re-push.
- **`dev.sigstore.sign 2.x` writes `.sigstore.json`** (v0.x `.sigstore`, v1.x
  `.sigstore.bundle`, v2.x `.sigstore.json`). release.yml's asset-attach `find`
  covers all three via alternation — keep the union on bumps, do NOT narrow.
- **Asset-name disambiguation**: `pluginMaven` + `fluxo-bcv-tsPluginMarkerMaven`
  each emit a `pom-default.xml.sigstore.json`; `gh release upload` derives the
  asset name from basename, unique per release → same-basename files collide
  (HTTP 422). `--clobber` only fixes same-name re-runs, not distinct-content
  same-basename in one command; `gh`'s `file#displayName` sets the LABEL not the
  NAME. Fix: release.yml renames on disk to prefix each bundle with its
  publication name (from the parent `sigstoreSign…Publication` task dir).
  Unconditional, so adding future Sigstore outputs can't regress the gate.
