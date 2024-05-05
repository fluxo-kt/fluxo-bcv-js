@file:Suppress("KotlinConstantConditions")

package fluxo.bcvts

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * A custom version of [kotlinx.validation.KotlinApiCompareTask].
 *
 * @see kotlinx.validation.KotlinApiCompareTask
 */
@CacheableTask
internal open class KotlinTsApiCompareTask : DefaultTask() {

    /*
     * Nullability and optionality is a workaround for
     * https://github.com/gradle/gradle/issues/2016
     *
     * Unfortunately, there is no way to skip validation apart from setting 'null'
     */
    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    var projectApiFile: File? = null

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    lateinit var apiBuildFile: File

    // Used for a diagnostic error message when projectApiDir doesn't exist.
    @Input
    @Optional
    var nonExistingProjectApiFile: String? = null

    fun compareApiDumps(referenceFile: File, buildFile: File) {
        if (referenceFile.exists()) {
            projectApiFile = referenceFile
        } else {
            nonExistingProjectApiFile = referenceFile.toString()
        }
        apiBuildFile = buildFile
    }

    @OutputFile
    @Optional
    @Suppress("unused")
    val dummyOutputFile: File? = null

    private val projectPath = project.path

    private val rootDir = project.rootProject.rootDir

    @TaskAction
    fun verify() {
        val expectedFile = projectApiFile ?: error(
            "Expected $KTS_API declaration '$nonExistingProjectApiFile' does not exist.\n" +
                "Please ensure that ':apiDump' was executed " +
                "in order to get API dump to compare the build against",
        )

        val path = projectPath
        val actualFile = apiBuildFile
        if (!actualFile.exists()) {
            error(
                "File ${actualFile.name} is missing from " +
                    "${expectedFile.relativeDirPath()}, " +
                    "please run $path:apiDump task to generate one",
            )
        }

        // Normalize case-sensitivity
        val diff = compareFiles(expectedFile, actualFile)
        if (!diff.isNullOrBlank()) {
            error(
                "API check failed for project $path.\n" +
                    "$diff\n\n " +
                    "You can run $path:apiDump task to overwrite API declarations",
            )
        } else if (DBG > 0) {
            logger.lifecycle(" >> {} API check passed for project {}", actualFile.name, path)
        }
    }

    private fun File.relativeDirPath(): String {
        return toRelativeString(rootDir) + File.separator
    }

    private fun compareFiles(checkFile: File, builtFile: File): String? {
        val checkText = checkFile.readText()
        val builtText = builtFile.readText()

        // Don't compare full text because newlines on Windows & Linux/macOS are different
        val checkLines = checkText.lines()
        val builtLines = builtText.lines()
        if (checkLines == builtLines) {
            return null
        }

        @Suppress("MagicNumber")
        val contextSize = 3
        val patch = DiffUtils.diff(checkLines, builtLines)
        val diff = UnifiedDiffUtils.generateUnifiedDiff(
            checkFile.toString(),
            builtFile.toString(),
            checkLines,
            patch,
            contextSize,
        )
        return diff.joinToString("\n")
    }
}
