package fluxo.bcvjs

import kotlinx.validation.API_DIR
import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * Copy of [kotlinx.validation.TargetConfig]
 *
 * @see kotlinx.validation.TargetConfig
 */
internal class TargetConfig(
    project: Project,
    val targetName: String? = null,
    private val dirConfig: Provider<DirConfig>? = null,
) {
    private val apiDirProvider = project.provider { API_DIR }

    fun apiTaskName(suffix: String) = when (targetName) {
        null, "" -> "api$suffix"
        else -> "${targetName}Api$suffix"
    }

    val apiDir: Provider<String>
        get() = dirConfig?.map { dirConfig ->
            when (dirConfig) {
                DirConfig.COMMON -> API_DIR
                else -> "$API_DIR/$targetName"
            }
        } ?: apiDirProvider
}
