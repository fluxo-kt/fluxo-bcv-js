# Roadmap, ideas, and notes

<details>
  <summary>Show</summary>

- Integration tests — incl. resolving the *published* plugin end-to-end
  (Portal `plugins{}` + JitPack `useModule`), the seam `checks/*` skip via
  `includeBuild` (a wrong JitPack coordinate shipped undetected in 1.1.0)
- Pull requests for
  - ★4000 https://github.com/square/wire
  - ★49 https://github.com/DrewCarlson/mobius.kt
  - ★23 https://github.com/Liftric/cognito-idp
  - https://github.com/search?q=binaries.executable+path%3A*.kts&type=code&ref=opensearch
- JAR shrinking via ProGuard/R8 — prototype archived as tag
  `archive/feat-shrinking` (`git switch -c feat/shrinking
  archive/feat-shrinking` to resume). Deferred: a plugin JAR is
  build-time-only (never shipped into consumer apps), so size is
  near-irrelevant; the ~1k-line `.pro` ruleset isn't worth the upkeep.
  Revisit only if the JAR grows materially.

</details>
