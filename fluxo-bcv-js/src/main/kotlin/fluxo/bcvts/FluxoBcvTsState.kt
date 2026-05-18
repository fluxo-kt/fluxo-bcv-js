package fluxo.bcvts

import kotlinx.validation.ApiValidationExtension
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

internal class FluxoBcvTsState(
    val singleTarget: Boolean,
    val apiDumpDir: String,
    val dirConfig: Provider<DirConfig>,
    // Nullable since 1.1.0: in embedded-only mode the external BCV
    // plugin's `ApiValidationExtension` does not exist on the project,
    // so `apiValidationExtensionOrNull` returns null. Callers use the
    // nullable-aware `apiCheckEnabled(...)` overload, and
    // `apiDumpDirectoryCompat` (extension property on the nullable
    // receiver) already handles the null case via `DEFAULT_API_DIR`.
    val bcv: ApiValidationExtension?,
    val commonApiDump: TaskProvider<Task>,
    val commonApiCheck: TaskProvider<Task>,
)
