package fluxo.bcvjs

import kotlinx.validation.API_DIR
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

fun apiTaskName(targetName: String?, suffix: String) = when (targetName) {
    null, "" -> "api$suffix"
    else -> "${targetName}Api$suffix"
}
