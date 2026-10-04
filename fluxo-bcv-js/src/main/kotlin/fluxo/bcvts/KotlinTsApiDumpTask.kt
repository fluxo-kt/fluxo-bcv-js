package fluxo.bcvts

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Copies the built `.d.ts` dump over the committed baseline.
 *
 * A task class, not a `doLast { }` lambda: configuration cache stores task
 * lambdas as serialized lambdas, and restoring one reflects over EVERY method
 * signature of the capturing file class. `ConfigureTsApiTasks.kt` has BCV
 * types in its signatures, which are absent from embedded-only builds (no
 * external BCV plugin), so a lambda there made `apiDump` fail with
 * `NoClassDefFoundError: kotlinx/validation/ApiValidationExtension` under CC.
 * Only `File`-typed state is serialized here.
 */
@DisableCachingByDefault(because = "Copies a single small file into the source tree")
internal abstract class KotlinTsApiDumpTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val builtFile: RegularFileProperty

    @get:OutputFile
    abstract val referenceFile: RegularFileProperty

    @TaskAction
    fun dump() {
        val target = referenceFile.get().asFile
        builtFile.get().asFile.copyTo(target, overwrite = true)
        logger.debug(" >> Copied API file: {}", target)
    }
}
