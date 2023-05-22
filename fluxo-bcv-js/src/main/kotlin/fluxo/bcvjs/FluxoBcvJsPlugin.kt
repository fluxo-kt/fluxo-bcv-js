package fluxo.bcvjs

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

class FluxoBcvJsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            target.plugins.withId("org.jetbrains.kotlinx.binary-compatibility-validator") {
                target.configureJsApiTasks(target.kotlinExtension as KotlinMultiplatformExtension)
            }
        }
    }
}
