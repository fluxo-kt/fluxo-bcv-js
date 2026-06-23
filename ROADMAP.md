# Roadmap, ideas, and notes

<details>
  <summary>Show</summary>

- Integration tests
- Pull requests for
  - ★4000 https://github.com/square/wire
  - ★49 https://github.com/DrewCarlson/mobius.kt
  - ★23 https://github.com/Liftric/cognito-idp
  - https://github.com/search?q=binaries.executable+path%3A*.kts&type=code&ref=opensearch
- JAR shrinking via ProGuard/R8 — prototyped on `feat/shrinking`
  (`b2dd53d`; pruned, restore with
  `git push origin b2dd53d:refs/heads/feat/shrinking`). Deferred: a
  plugin JAR is build-time-only (never shipped into consumer apps), so
  size is near-irrelevant; the ~1k-line `.pro` ruleset isn't worth the
  upkeep. Revisit only if the JAR grows materially.

</details>
