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
 * Each configuration run that finds a validator (external BCV or embedded
 * `abiValidation`) emits exactly one machine-parseable line to Gradle's
 * lifecycle log, even if BCV's opt-outs then skip the project (a build that
 * reuses a configuration-cache entry skips configuration, so it prints none):
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
 * When the chosen mode differs from the preference (a forced preference
 * that cannot be satisfied, or embedded mode refused for an old BCV), a
 * notice line follows.
 * Format is stable across 1.x minor releases — `checks/dual/sweep`
 * asserts it as an exact whole line. External CI
 * integrations can rely on the same shape.
 *
 * Marked `@Incubating` while the dual-mode contract beds in: 1.2.0 and
 * 1.3.0 each changed what `preferEmbedded` does, so removal of
 * `@Incubating` is targeted for 1.4.0, after a release that keeps it.
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
     *   or embedded when both are active (since 1.3.0; external before).
     *   A project upgrading with both active has its `.d.ts` baselines
     *   moved: run `apiDump` once and commit them, or set `false`.
     * - **`true`**: embedded; falls back to external, with a logged
     *   reason, if embedded validation is not enabled.
     * - **`false`**: external, keeping the 1.0–1.2 layout; falls back to
     *   embedded, with a logged reason, if the external plugin is absent.
     *
     * With both active, embedded is never chosen alongside an external BCV
     * older than 0.15: its `apiDump` syncs the whole dump dir and would
     * delete the baselines, so external drives, with a logged reason.
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
