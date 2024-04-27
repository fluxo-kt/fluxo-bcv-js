package fluxo.bcvjs

import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * Copy of the [kotlinx.validation.TargetConfig]
 *
 * @see kotlinx.validation.TargetConfig
 */
internal class TargetConfig(
    project: Project,
    val targetName: String? = null,
    val dirConfig: Provider<DirConfig>? = null,
) {
    fun apiTaskName(suffix: String) = apiTaskName(targetName, suffix)

    val apiDir: Provider<String> = dirConfig?.map { dirConfig ->
        when (dirConfig) {
            is DirConfig.COMMON -> API_DIR
            else -> "$API_DIR/$targetName"
        }
    } ?: project.provider { API_DIR }
}

internal fun apiTaskName(targetName: String?, suffix: String) = when (targetName) {
    null, "" -> "api$suffix"
    else -> "${targetName}Api$suffix"
}

/**
 *
 * @fixme This is a copy of [kotlinx.validation.API_DIR] before 0.14.0
 *   After 0.14.0 it can be customized and requires special support.
 *
 * @see kotlinx.validation.API_DIR
 */
@Deprecated("Should be replaced with dynamic value from kotlinx.validation")
internal const val API_DIR = "api"
