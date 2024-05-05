package fluxo.bcvts

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

/**
 * Copy of the [kotlinx.validation.TargetConfig]
 *
 * @see kotlinx.validation.TargetConfig
 */
internal class TargetConfig(
    project: Project,
    private val apiDumpDirectory: String,
    val targetTsName: String?,
    val targetName: String,
    val dirConfig: Provider<DirConfig>?,
) {
    fun apiTaskName(suffix: String) = apiTaskName(targetTsName, suffix)

    val apiDirName: Provider<String> = dirConfig?.map { dirConfig ->
        when (dirConfig) {
            is DirConfig.COMMON -> apiDumpDirectory
            else -> "$apiDumpDirectory/$targetTsName"
        }
    } ?: project.provider { apiDumpDirectory }

    val apiDir: Provider<Directory> = project.layout.projectDirectory.dir(apiDirName)
}

internal fun apiTaskName(targetName: String?, suffix: String) = when (targetName) {
    null, "" -> "api$suffix"
    else -> "${targetName}Api$suffix"
}

internal const val TS = "ts"
internal const val TS_CAP = "Ts"
