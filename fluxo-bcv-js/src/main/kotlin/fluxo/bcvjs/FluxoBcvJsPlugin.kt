package fluxo.bcvjs

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@Suppress("unused")
class FluxoBcvJsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            target.plugins.withId("org.jetbrains.kotlinx.binary-compatibility-validator") {
                /**
                 * Get the extension manually instead of `kotlinExtension` helper to support old Kotlin.
                 *
                 * @see org.jetbrains.kotlin.gradle.dsl.kotlinExtension
                 * @see org.jetbrains.kotlin.gradle.dsl.KOTLIN_PROJECT_EXTENSION_NAME ("kotlin")
                 */
                val extension = target.extensions.findByName("kotlin") as KotlinMultiplatformExtension
                target.configureJsApiTasks(extension)
            }
        }
    }
}
