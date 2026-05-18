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
 * Every build invocation that activates the pipeline emits exactly
 * one machine-parseable line to Gradle's lifecycle log:
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
 * Format is stable across 1.x minor releases — `checks/dual` asserts
 * exact whole-line equality via `grep -Fxq`. External CI integrations
 * can rely on the same shape.
 *
 * Marked `@Incubating` while the dual-mode contract beds in; the
 * stability commitment moment (removal of `@Incubating`) is targeted
 * for 1.2.0.
 */
@Incubating
public interface FluxoBcvTsExtension {
    /**
     * Decides which trigger path runs the `.d.ts` pipeline when both
     * the external KotlinX BCV plugin AND KGP-embedded `abiValidation`
     * are observable on the project:
     *
     * - **unset / `null`** (AUTO, the default): if only one validator
     *   source is active, that one is used; if both are active, the
     *   external plugin is preferred (1.0.x backward-compat) and a
     *   one-shot recommendation is logged.
     * - **`true`**: force the KGP-embedded path even when external BCV
     *   is applied. Falls back to external silently if embedded turns
     *   out not to be enabled.
     * - **`false`**: force the external-BCV path. Falls back to
     *   embedded silently if external is absent.
     *
     * In all cases the trigger emits a `[fluxo-bcv-ts] trigger=…
     * preferEmbedded=…` lifecycle line so the resolved choice is
     * machine-observable in CI / build scans.
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
