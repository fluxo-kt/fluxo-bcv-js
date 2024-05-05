package fluxo.bcvts

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
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
internal open class KotlinTsApiCompareTask
@Inject constructor(objects: ObjectFactory): DefaultTask() {

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

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val apiBuildFile: RegularFileProperty = objects.fileProperty()

    // Used for a diagnostic error message when projectApiDir doesn't exist.
    @Input
    @Optional
    var nonExistingProjectApiFile: String? = null

    fun compareApiDumps(referenceFile: File, buildFile: Provider<RegularFile>) {
        if (referenceFile.exists()) {
            projectApiFile = referenceFile
        } else {
            nonExistingProjectApiFile = referenceFile.toString()
        }
        apiBuildFile.set(buildFile)
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
        val actualFile = apiBuildFile.get().asFile
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
