package fluxo.bcvts

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project

public class FluxoBcvTsPlugin : Plugin<Project> {
    private companion object {
        private const val PLUGIN_ID_KMP = "org.jetbrains.kotlin.multiplatform"
        private const val PLUGIN_ID_KJS = "org.jetbrains.kotlin.js"
        private const val PLUGIN_ID_BCV = "org.jetbrains.kotlinx.binary-compatibility-validator"

        // Stable, machine-parseable observable for path selection.
        // `checks/dual` greps for these lines to assert that exactly
        // one trigger fires per build invocation; the format is part
        // of the integration-test contract.
        private const val LIFECYCLE_TAG = "[fluxo-bcv-ts]"

        // Single-fire latch key. `plugins.withId(...)` may fire once for
        // each of KMP/KJS (mutually exclusive in practice, but the
        // contract here is "exactly one trigger registration"), so we
        // gate via project extras to keep the lifecycle observable
        // single-shot for `checks/dual`.
        private const val TRIGGER_FLAG = "fluxo.bcvts.triggerRegistered"
    }

    override fun apply(target: Project) {
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
                    "is appplied to the :$name project. \n" +
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
                    "`kotlin { @OptIn(ExperimentalAbiValidation::class) " +
                    "abiValidation { enabled.set(true) } }`."
                logger.error(message)
                // Diagnostic refinement: the extension was found by name
                // but `enabled` couldn't be read — shim drift likely.
                if (kgpAbiValidationDetectedCompat) {
                    logger.lifecycle(
                        "$LIFECYCLE_TAG embedded abiValidation extension detected but " +
                            "`enabled` could not be read — KGP shim drift suspected; " +
                            "see CompatibilityUtils.kt ABI_EXT_NAMES.",
                    )
                }
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
            // AUTO selection: when both validator sources are active,
            // prefer external for backward-compat with 1.0.x users. The
            // explicit `preferEmbedded` knob from `FluxoBcvTsExtension`
            // lands in the next commit; until then the AUTO branch is
            // the only path.
            val trigger = if (external) "external" else "embedded"
            logger.lifecycle("$LIFECYCLE_TAG trigger=$trigger preferEmbedded=auto")
            if (external && embedded) {
                logger.lifecycle(
                    "$LIFECYCLE_TAG both external BCV and KGP-embedded abiValidation " +
                        "are active; using external. The `preferEmbedded` knob lands in " +
                        "the 1.1.0 extension for forward-compat selection.",
                )
            }
            configureTsApiTasks()
        }
    }
}
