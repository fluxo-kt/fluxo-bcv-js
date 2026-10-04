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
    referenceDumpDir: Provider<Directory>,
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

    // Baseline dir: the dump base itself in COMMON layout, else its per-target
    // subdir. Built from `referenceDumpDir`, not `apiDirName`, because embedded
    // mode may keep baselines outside the project-relative default.
    val apiDir: Provider<Directory> = when (dirConfig) {
        null -> referenceDumpDir
        else -> referenceDumpDir.zip(dirConfig) { base, layout ->
            if (layout is DirConfig.COMMON) base else base.dir(targetTsName.orEmpty())
        }
    }
}

internal fun apiTaskName(targetName: String?, suffix: String) = when (targetName) {
    null, "" -> "api$suffix"
    else -> "${targetName}Api$suffix"
}

internal const val TS = "ts"
internal const val TS_CAP = "Ts"
