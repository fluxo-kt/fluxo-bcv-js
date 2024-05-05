package fluxo.bcvts

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project

public class FluxoBcvTsPlugin : Plugin<Project> {
    private companion object {
        private const val PLUGIN_ID_KMP = "org.jetbrains.kotlin.multiplatform"
        private const val PLUGIN_ID_KJS = "org.jetbrains.kotlin.js"
        private const val PLUGIN_ID_BCV = "org.jetbrains.kotlinx.binary-compatibility-validator"
    }

    override fun apply(target: Project) {
        // The plugin depends on the KotlinX BinaryCompatibilityValidator plugin,
        // and the Kotlin Multiplatform or Kotlin/JS (legacy) plugins.
        target.plugins.withId(PLUGIN_ID_BCV) {
            val action = Action<Plugin<Any>> {
                // FIXME: Support lazy initialization of the targets and extension.
                target.afterEvaluate {
                    configureTsApiTasks()
                }
            }
            target.plugins.withId(PLUGIN_ID_KMP, action)
            target.plugins.withId(PLUGIN_ID_KJS, action)
        }

        // Helpful warnings for the user if the required plugins are not applied.
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
            }
            if (!plugins.hasPlugin(PLUGIN_ID_BCV)) {
                val message = "KotlinX BinaryCompatibilityValidator plugin" +
                    "is not appplied to the :$name project. " +
                    "Fluxo-BCV-JS requires it for work. \n" +
                    "Please read the setup instructions at " +
                    "https://github.com/Kotlin/binary-compatibility-validator#setup"
                logger.error(message)
            }
        }
    }
}
