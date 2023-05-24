package fluxo.bcvjs

import difflib.DiffUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Custom version of [kotlinx.validation.KotlinApiCompareTask].
 *
 * @see kotlinx.validation.KotlinApiCompareTask
 */
internal open class KotlinJsApiCompareTask : DefaultTask() {

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    var projectApiFile: File? = null

    @Input
    @Optional
    var nonExistingProjectApiFile: String? = null

    fun compareApiDumps(apiReferenceFile: File, apiBuildFile: File) {
        if (apiReferenceFile.exists()) {
            projectApiFile = apiReferenceFile
        } else {
            projectApiFile = null
            nonExistingProjectApiFile = apiReferenceFile.toString()
        }
        this.apiBuildFile = apiBuildFile
    }

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    lateinit var apiBuildFile: File

    private val projectName = project.name

    private val rootDir = project.rootProject.rootDir

    @TaskAction
    fun verify() {
        val projectApiFile = projectApiFile ?: error(
            "Expected Kotlin/JS API declaration '$nonExistingProjectApiFile' does not exist.\n" +
                "Please ensure that ':apiDump' was executed in order to get API dump to compare the build against"
        )

        val subject = projectName
        if (!apiBuildFile.exists()) {
            error(
                "File ${apiBuildFile.name} is missing from ${projectApiFile.relativeDirPath()}, please run " +
                    ":$subject:apiDump task to generate one"
            )
        }

        // Normalize case-sensitivity
        val expectedFile = projectApiFile
        val actualFile = apiBuildFile
        val diff = compareFiles(expectedFile, actualFile)
        if (!diff.isNullOrBlank()) {
            error(
                "API check failed for project $subject.\n" +
                    "$diff\n\n " +
                    "You can run :$subject:apiDump task to overwrite API declarations"
            )
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
        val diff = DiffUtils.generateUnifiedDiff(
            checkFile.toString(),
            builtFile.toString(),
            checkLines,
            patch,
            contextSize,
        )
        return diff.joinToString("\n")
    }
}
