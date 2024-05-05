@file:Suppress("KDocUnresolvedReference")

package fluxo.bcvts

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.NormalizeLineEndings

/**
 *
 * @TODO Replace with Copy task?
 *
 * @see kotlinx.validation.KotlinApiBuildTask
 * @see kotlinx.validation.configureApiTasks
 * @see kotlinx.validation.BinaryCompatibilityValidatorPlugin
 *
 * @see org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink
 * @see org.jetbrains.kotlin.gradle.targets.js.ir.SyncExecutableTask
 * @see org.jetbrains.kotlin.gradle.targets.js.typescript.TypeScriptValidationTask
 *
 * @see org.gradle.api.tasks.Copy
 */
@DisableCachingByDefault(because = "Not worth caching")
internal abstract class KotlinTsApiBuildTask : DefaultTask() {

    @get:InputFiles
    @get:NormalizeLineEndings
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedDefinitions: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        // No 'group' to hide it from ./gradlew tasks.
        description =
            "Collects built Kotlin TS definitions as API for 'js'" +
                " compilations of :${project.name}. " +
                "Complementary task and shouldn't be called manually"
    }

    @TaskAction
    fun generate() {
        val files = generatedDefinitions.files
        if (files.isEmpty()) {
            val code = when {
                !hasGenerateTypeScriptDefinitions -> ""
                else -> "and `generateTypeScriptDefinitions()`"
            }
            @Suppress("MaxLineLength")
            val message = "No generated Kotlin TS definitions found for :${project.name}! " +
                "$KTS_API verification is not possible. \n" +
                "Please, enable TS definitions with `binaries.executable()`$code. \n" +
                "More instructions at " +
                "https://kotlinlang.org/docs/whatsnew1820.html#opt-in-for-generation-of-typescript-definition-files"
            logger.error(message)
            return
        }
        if (files.size > 1) {
            logger.error(
                "Ambigous generated TS definitions" +
                    ", taking only first:" +
                    " \n  ${files.joinToString("\n  ")}",
            )
        }

        // 1) Copy the built TS definitions.
        // 2) Make sure the file uses \n line endings and has a final new line.
        val lines = mutableListOf<String>()
        var lastEmpty = 0
        files.first().useLines { linesSeq ->
            for (line in linesSeq) {
                val cleanLine = line.trimEnd()
                lines.add(cleanLine)
                if (cleanLine.isEmpty()) {
                    lastEmpty++
                } else {
                    lastEmpty = 0
                }
            }
        }
        if (lastEmpty == 0) {
            lines.add("")
        } else if (lastEmpty > 1) {
            val size = lines.size
            lines.subList(size - (lastEmpty - 1), size).clear()
        }
        outputFile.asFile.get().bufferedWriter().use {
            lines.joinTo(it, "\n")
        }
    }
}
