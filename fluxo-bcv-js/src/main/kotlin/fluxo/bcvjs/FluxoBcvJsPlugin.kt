package fluxo.bcvjs

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class FluxoBcvJsPlugin : Plugin<Project> {
    private companion object {
        private const val PLUGIN_ID_KMP = "org.jetbrains.kotlin.multiplatform"
        private const val PLUGIN_ID_KJS = "org.jetbrains.kotlin.js"
        private const val PLUGIN_ID_BCV = "org.jetbrains.kotlinx.binary-compatibility-validator"
    }

    override fun apply(target: Project) {
        target.plugins.withId(PLUGIN_ID_BCV) {
            val function: (Plugin<Any>) -> Unit = {
                target.afterEvaluate {
                    it.configureJsApiTasks()
                }
            }
            target.plugins.withId(PLUGIN_ID_KMP, function)
            target.plugins.withId(PLUGIN_ID_KJS, function)
        }

        target.afterEvaluate {
            val plugins = it.plugins
            if (!plugins.hasPlugin(PLUGIN_ID_KMP) && !plugins.hasPlugin(PLUGIN_ID_KJS)) {
                it.logger.warn(
                    "Kotlin Multiplatform plugin is not appplied to the :${it.name} project. " +
                        "There is no Kotlin/JS API to provide stability for without it. " +
                        "So Fluxo-BCV-JS does nothing. \n" +
                        "Please read the setup instructions at " +
                        "https://kotlinlang.org/docs/multiplatform-get-started.html"
                )
            }
            if (!plugins.hasPlugin(PLUGIN_ID_BCV)) {
                it.logger.warn(
                    "KotlinX BinaryCompatibilityValidator plugin is not appplied to the :${it.name} project. " +
                        "Fluxo-BCV-JS requires it for work. \n" +
                        "Please read the setup instructions at " +
                        "https://github.com/Kotlin/binary-compatibility-validator#setup"
                )
            }
        }
    }
}
