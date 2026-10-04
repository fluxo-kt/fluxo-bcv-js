package fluxo.bcvts

import org.gradle.api.Incubating
import org.gradle.api.provider.Property

/**
 * Configures `.d.ts` API validation behaviour for Kotlin/JS and
 * Kotlin/Wasm-JS targets in 1.1.0+.
 *
 * Apply via the standard DSL — `fluxoBcvTs { … }` — once the
 * `io.github.fluxo-kt.binary-compatibility-validator-js` plugin has
 * been applied:
 *
 * ```kotlin
 * fluxoBcvTs {
 *     preferEmbedded.set(true)   // force KGP-embedded path
 *     wireToKgpAbi.set(true)     // run `:apiCheck` alongside `:checkKotlinAbi`
 * }
 * ```
 *
 * ### Lifecycle observable (integration-test contract)
 *
 * Each configuration run that activates the pipeline emits exactly one
 * machine-parseable line to Gradle's lifecycle log (a build that reuses a
 * configuration-cache entry skips configuration, so it prints none):
 *
 * ```
 * [fluxo-bcv-ts] trigger=<external|embedded> preferEmbedded=<auto|true|false>
 * ```
 *
 * - `trigger=external` — external KotlinX BCV plugin drives the pipeline.
 * - `trigger=embedded` — KGP-embedded `abiValidation` drives it.
 * - `preferEmbedded=auto` — `[preferEmbedded]` is unset.
 * - `preferEmbedded=true|false` — `[preferEmbedded]` was set to that value.
 *
 * When AUTO resolves with BOTH sources active, an additional
 * recommendation line follows (asking the consumer to migrate to
 * `preferEmbedded=true` once external BCV is removed); when a forced
 * preference cannot be satisfied, a fallback-notice line follows.
 * Format is stable across 1.x minor releases — `checks/dual/sweep`
 * asserts exact whole-line equality via `grep -Fxq`. External CI
 * integrations can rely on the same shape.
 *
 * Marked `@Incubating` while the dual-mode contract beds in: 1.2.0
 * changed what `preferEmbedded` does, so removal of `@Incubating` is
 * targeted for 1.3.0.
 */
@Incubating
public interface FluxoBcvTsExtension {
    /**
     * Decides which validator drives the `.d.ts` pipeline when both the
     * external KotlinX BCV plugin AND KGP-embedded `abiValidation` are
     * active. The choice sets where baselines live and which opt-outs apply:
     *
     * - **external**: BCV's `apiDumpDirectory`, BCV's layout (`api/` for a
     *   single JVM target, `api/<target>/` otherwise), and BCV's
     *   `ignoredProjects` / `validationDisabled`.
     * - **embedded**: KGP's `abiValidation { referenceDumpDir }` (default
     *   `api/`), always per target (`api/ts/`, `api/wasmTs/`), and no BCV
     *   opt-outs.
     *
     * Values:
     * - **unset / `null`** (AUTO, the default): the only active validator,
     *   or external when both are active, plus a logged recommendation.
     * - **`true`**: embedded even when external BCV is applied. Switching
     *   moves the `.d.ts` baselines, so run `apiDump` once and commit them.
     *   Falls back to external, with a logged reason, if embedded validation
     *   is not enabled, or if the external BCV is older than 0.15 (its
     *   `apiDump` syncs the whole dump dir and would delete the baselines).
     * - **`false`**: external; falls back to embedded, with a logged
     *   reason, if the external plugin is absent.
     *
     * The resolved choice is printed as the
     * `[fluxo-bcv-ts] trigger=… preferEmbedded=…` lifecycle line.
     */
    @get:Incubating
    public val preferEmbedded: Property<Boolean>

    /**
     * Wire this plugin's umbrella `:apiCheck` task to also run when
     * KGP's `:checkKotlinAbi` runs, so `./gradlew checkKotlinAbi`
     * picks up `.d.ts` validation in addition to KLIB ABI.
     *
     * Default **`false`** — `./gradlew check` already triggers
     * `:apiCheck` via the lifecycle umbrella in both modes. Users
     * invoking `:checkKotlinAbi` directly are doing focused KGP work;
     * silently piggy-backing `.d.ts` validation would make their fast
     * focused-check unexpectedly slow.
     */
    @get:Incubating
    public val wireToKgpAbi: Property<Boolean>
}
