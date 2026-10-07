package fluxo.bcvts

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project

// Top-level so other files in the package (notably `CompatibilityUtils.kt`)
// reuse the plugin ids without duplicating literals.
internal const val PLUGIN_ID_KMP = "org.jetbrains.kotlin.multiplatform"
internal const val PLUGIN_ID_KJS = "org.jetbrains.kotlin.js"
internal const val PLUGIN_ID_BCV = "org.jetbrains.kotlinx.binary-compatibility-validator"

// Stable, machine-parseable observable for path selection.
// `checks/dual/sweep` greps for these lines to assert that exactly one
// trigger fires per configuration run; the format is part of the
// integration-test contract.
internal const val LIFECYCLE_TAG = "[fluxo-bcv-ts]"

// The switch differs by KGP version: 2.4 removed `enabled` (a compile error there),
// while on 2.2/2.3 an empty block leaves validation off.
private const val ENABLE_ABI_VALIDATION_HINT =
    "`kotlin { abiValidation { } }` (Kotlin 2.4+) or " +
        "`kotlin { abiValidation { enabled.set(true) } }` (Kotlin 2.2/2.3), " +
        "with @OptIn(ExperimentalAbiValidation::class)"

public class FluxoBcvTsPlugin : Plugin<Project> {
    private companion object {
        // Single-fire latch key. `plugins.withId(...)` may fire once for
        // each of KMP/KJS (mutually exclusive in practice, but the
        // contract here is "exactly one trigger registration"), so we
        // gate via project extras to keep the lifecycle observable
        // single-shot for `checks/dual`.
        private const val TRIGGER_FLAG = "fluxo.bcvts.triggerRegistered"

        // DSL key for the public extension. Consumers write
        // `fluxoBcvTs { preferEmbedded.set(true) }`. Standard Gradle
        // camelCase convention. Kept `private` (symmetric with
        // `TRIGGER_FLAG`) — the const is only referenced inside this
        // file; `public` inside a `private companion object` would
        // emit a Java-accessible static field that Kotlin consumers
        // could not import anyway, which is an unintentional ABI
        // asymmetry.
        private const val EXTENSION_NAME: String = "fluxoBcvTs"
    }

    override fun apply(target: Project) {
        // Register the public extension synchronously, BEFORE any
        // `withId` listener fires. Consumers can then configure
        // `fluxoBcvTs { … }` in their build script and the trigger
        // logic (which runs lazily in `afterEvaluate`) reads it back.
        // Managed type: Gradle's ManagedFactory injects `Property`
        // instances, so no explicit `objects.property()` boilerplate.
        val ext = target.extensions
            .create(EXTENSION_NAME, FluxoBcvTsExtension::class.java)
        // `preferEmbedded` deliberately has no convention: a null/unset
        // value is the AUTO sentinel (see `resolveTrigger`), which the
        // lifecycle line reports as `auto`. `wireToKgpAbi` defaults to false
        // — current call sites use `.orNull == true` which already
        // null-handles, but the convention forward-protects any future
        // `.get()` reader from `MissingValueException` and makes the
        // documented default observable in IDE auto-complete.
        ext.wireToKgpAbi.convention(false)

        // The plugin still needs Kotlin (KMP or legacy KJS) for target
        // discovery. The validator source — external BCV plugin or
        // KGP-embedded abiValidation — is resolved *inside*
        // `registerTrigger`'s `afterEvaluate`, so either path can fire
        // the pipeline.
        val action = Action<Plugin<Any>> { target.registerTriggerOnce() }
        target.plugins.withId(PLUGIN_ID_KMP, action)
        target.plugins.withId(PLUGIN_ID_KJS, action)

        // Helpful warnings if neither Kotlin nor a validator source is
        // configured. Runs once at the end of project configuration,
        // independent of the trigger; the conditions are complementary
        // (the trigger fires only when these errors do NOT).
        target.afterEvaluate {
            val plugins = plugins
            if (!plugins.hasPlugin(PLUGIN_ID_KMP) && !plugins.hasPlugin(PLUGIN_ID_KJS)) {
                val message = "Neither Kotlin Multiplatform nor Kotlin/JS plugin " +
                    "is applied to the :$name project. \n" +
                    "There is no $KTS_API to provide stability for." +
                    " Fluxo-BCV-JS does nothing. \n" +
                    "Please read the setup instructions at " +
                    "https://kotlinlang.org/docs/multiplatform-get-started.html"
                logger.error(message)
                return@afterEvaluate
            }
            val external = plugins.hasPlugin(PLUGIN_ID_BCV)
            val embedded = kgpAbiValidationEnabledCompat
            if (!external && !embedded) {
                val message = "Neither the external KotlinX BCV plugin " +
                    "(`$PLUGIN_ID_BCV`) nor KGP-embedded `abiValidation` is " +
                    "configured on the :$name project. " +
                    "Fluxo-BCV-JS requires one of them. \n" +
                    "Apply the external plugin (see " +
                    "https://github.com/Kotlin/binary-compatibility-validator#setup" +
                    ") OR enable embedded mode via " +
                    "$ENABLE_ABI_VALIDATION_HINT."
                logger.error(message)
            }
        }
    }

    private fun Project.registerTriggerOnce() {
        val xp = extensions.extraProperties
        if (xp.has(TRIGGER_FLAG)) return
        xp.set(TRIGGER_FLAG, true)
        // FIXME: Support lazy initialization of the targets and extension.
        afterEvaluate {
            val external = plugins.hasPlugin(PLUGIN_ID_BCV)
            val embedded = kgpAbiValidationEnabledCompat
            if (!external && !embedded) return@afterEvaluate

            val ext = extensions.getByType(FluxoBcvTsExtension::class.java)
            val preference: Boolean? = ext.preferEmbedded.orNull
            configureTsApiTasks(useEmbedded = resolveTrigger(preference, external, embedded))
        }
    }

    /**
     * Picks the validator that drives the `.d.ts` pipeline, prints the lifecycle
     * line and explains any fallback. Returns `true` for embedded.
     */
    private fun Project.resolveTrigger(
        preference: Boolean?,
        external: Boolean,
        embedded: Boolean,
    ): Boolean {
        // The trigger picks the whole pipeline, not only the log line:
        // embedded = KGP's dump dir, per-target layout, BCV opt-outs ignored;
        // external = BCV's dump dir, layout and opt-outs (1.0.x behaviour).
        // Decision table:
        //   only one source active          → that one (a preference it
        //                                     cannot satisfy is explained)
        //   both active, preference=false   → external
        //   both active, AUTO or true       → embedded, unless BCV < 0.15
        // Both-active AUTO picks embedded: external BCV is in maintenance mode
        // and new ABI work lands in KGP, while waiting for KGP to drop its
        // experimental opt-in (KT-71172) bought nothing, since neither
        // validator was ever declared stable. The cost: such projects' baselines
        // move on upgrade, so the missing-baseline error explains the move and
        // `preferEmbedded=false` keeps the old layout.
        // Embedded mode keeps `.d.ts` baselines in `<dump dir>/<target>/`, which a
        // directory-syncing BCV would delete on its next `apiDump`. Reachable:
        // BCV 0.14 builds Kotlin 2.2 output with `languageVersion` <= 1.9.
        val unsafeForEmbedded = embedded && external && preference != false &&
            externalBcvSyncsWholeDumpDir()
        val useEmbedded = when {
            !embedded -> false
            !external -> true
            else -> preference != false && !unsafeForEmbedded
        }
        val trigger = if (useEmbedded) "embedded" else "external"
        val preferenceLabel = preference?.toString() ?: "auto"
        logger.lifecycle("$LIFECYCLE_TAG trigger=$trigger preferEmbedded=$preferenceLabel")

        if (unsafeForEmbedded) {
            logger.warn(
                "$LIFECYCLE_TAG embedded mode skipped: the external BCV plugin " +
                    "is older than 0.15 and its apiDump syncs the whole API dir, which " +
                    "would delete the .d.ts baselines. Upgrade BCV to 0.15+.",
            )
        } else {
            logTriggerFallbacks(preference, external, embedded)
        }
        return useEmbedded
    }

    /** Explains why the resolved trigger differs from the preference. */
    private fun Project.logTriggerFallbacks(
        preference: Boolean?,
        external: Boolean,
        embedded: Boolean,
    ) {
        if (preference == true && !embedded) {
            logger.lifecycle(
                "$LIFECYCLE_TAG preferEmbedded=true but KGP-embedded abiValidation " +
                    "is not enabled — falling back to external BCV. " +
                    "Enable it with $ENABLE_ABI_VALIDATION_HINT.",
            )
        }
        if (preference == false && !external) {
            logger.lifecycle(
                "$LIFECYCLE_TAG preferEmbedded=false but external BCV plugin is " +
                    "not applied — falling back to KGP-embedded abiValidation.",
            )
        }
    }
}
