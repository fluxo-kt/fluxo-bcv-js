package fluxo.bcvts

import kotlinx.validation.ApiValidationExtension
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

internal class FluxoBcvTsState(
    val singleTarget: Boolean,
    val apiDumpDir: String,
    val dirConfig: Provider<DirConfig>,
    val bcv: ApiValidationExtension,
    val commonApiDump: TaskProvider<Task>,
    val commonApiCheck: TaskProvider<Task>,
)
