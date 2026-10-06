package fluxo.bcvts

import kotlinx.validation.ApiValidationExtension
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

internal class FluxoBcvTsState(
    val singleTarget: Boolean,
    // Where baselines live; differs from `apiDumpDir` only in embedded mode
    // with a custom KGP `referenceDumpDir`.
    val referenceDumpDir: Provider<Directory>,
    val dirConfig: Provider<DirConfig>,
    // Null in embedded mode, even when the external BCV is applied: KGP drives,
    // so BCV's dump dir, layout and opt-outs are ignored. `apiCheckEnabled` and
    // `apiDumpDirectoryCompat` read null as "no BCV" (checks on, `api/`).
    val bcv: ApiValidationExtension?,
    val commonApiDump: TaskProvider<Task>,
    val commonApiCheck: TaskProvider<Task>,
) {
    // Relative name: also names the build output dir (`build/<apiDumpDir>`).
    // Derived from `bcv` (null → `api`), so it cannot disagree with it.
    val apiDumpDir: String get() = bcv.apiDumpDirectoryCompat
}
