package fluxo.bcvts

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project

// Top-level so other files in the package (notably
// `ConfigureTsApiTasks.kt`) can branch on `plugins.hasPlugin(PLUGIN_ID_BCV)`
// without duplicating literals.
internal const val PLUGIN_ID_KMP = "org.jetbrains.kotlin.multiplatform"
internal const val PLUGIN_ID_KJS = "org.jetbrains.kotlin.js"
internal const val PLUGIN_ID_BCV = "org.jetbrains.kotlinx.binary-compatibility-validator"

// Stable, machine-parseable observable for path selection.
// `checks/dual` greps for these lines to assert that exactly one
// trigger fires per build invocation; the format is part of the
// integration-test contract.
internal const val LIFECYCLE_TAG = "[fluxo-bcv-ts]"

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
        // camelCase convention.
        public const val EXTENSION_NAME: String = "fluxoBcvTs"
    }

    override fun apply(target: Project) {
        // Register the public extension synchronously, BEFORE any
        // `withId` listener fires. Consumers can then configure
        // `fluxoBcvTs { … }` in their build script and the trigger
        // logic (which runs lazily in `afterEvaluate`) reads it back.
        // Managed type: Gradle's ManagedFactory injects `Property`
        // instances, so no explicit `objects.property()` boilerplate.
        target.extensions.create(EXTENSION_NAME, FluxoBcvTsExtension::class.java)

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

            val ext = extensions.getByType(FluxoBcvTsExtension::class.java)
            val preference: Boolean? = ext.preferEmbedded.orNull
            // Decision table:
            //   preference=true,  embedded → embedded
            //   preference=false, external → external
            //   preference=null   (AUTO) or preference unreachable
            //     → external if available, else embedded.
            // Any unreachable preference falls back silently to the
            // available source; AUTO + both-active prefers external for
            // 1.0.x backward-compat and emits a one-shot recommendation.
            val trigger = when {
                preference == true && embedded -> "embedded"
                preference == false && external -> "external"
                external -> "external"
                else -> "embedded"
            }
            val preferenceLabel = preference?.toString() ?: "auto"
            logger.lifecycle("$LIFECYCLE_TAG trigger=$trigger preferEmbedded=$preferenceLabel")

            if (preference == null && external && embedded) {
                logger.lifecycle(
                    "$LIFECYCLE_TAG both external BCV and KGP-embedded abiValidation " +
                        "are active; using external (AUTO). Consider " +
                        "`fluxoBcvTs { preferEmbedded.set(true) }` to migrate once " +
                        "external BCV is removed (it is frozen upstream).",
                )
            }
            if (preference == true && !embedded) {
                logger.lifecycle(
                    "$LIFECYCLE_TAG preferEmbedded=true but KGP-embedded abiValidation " +
                        "is not enabled — falling back to external BCV. " +
                        "Set `kotlin { abiValidation { enabled.set(true) } }` " +
                        "(requires @OptIn(ExperimentalAbiValidation::class)).",
                )
            }
            if (preference == false && !external) {
                logger.lifecycle(
                    "$LIFECYCLE_TAG preferEmbedded=false but external BCV plugin is " +
                        "not applied — falling back to KGP-embedded abiValidation.",
                )
            }
            configureTsApiTasks()
        }
    }
}
