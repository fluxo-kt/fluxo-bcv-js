@file:Suppress(
    "CyclomaticComplexMethod",
    "KDocUnresolvedReference",
    "KotlinConstantConditions",
    "LongMethod",
    "LongParameterList",
    "TooGenericExceptionCaught",
)

package fluxo.bcvts

import kotlin.contracts.contract
import kotlinx.validation.ApiValidationExtension
import kotlinx.validation.KotlinApiCompareTask
import org.gradle.api.DefaultTask
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation.Companion.MAIN_COMPILATION_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode
import org.jetbrains.kotlin.gradle.targets.js.ir.JsBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink


internal const val DBG = 0
private const val EXT = ".d.ts"
private const val API = "api"
private const val SUFFIX_BUILD = "Build"
private const val SUFFIX_DUMP = "Dump"
private const val SUFFIX_CHECK = "Check"
private const val OTHER_GROUP = "other"

internal const val KTS_API = "Kotlin/TypeScript API"

private val BCV_PLATFORMS = arrayOf(
    KotlinPlatformType.jvm,
    KotlinPlatformType.androidJvm,
)

private fun apiCheckEnabled(projectName: String, bcv: ApiValidationExtension?): Boolean {
    contract {
        returns(true) implies (bcv != null)
    }
    return bcv == null || (!bcv.validationDisabled && projectName !in bcv.ignoredProjects)
}


/**
 *
 * @see kotlinx.validation.BinaryCompatibilityValidatorPlugin.configureMultiplatformPlugin
 */
internal fun Project.configureTsApiTasks() {
    if (!validateKotlinVersion()) {
        return
    }

    val bcv: ApiValidationExtension? = apiValidationExtensionOrNull
    if (!apiCheckEnabled(name, bcv)) {
        logger.info("{} API checks are disabled for {}", KTS_API, path)
        return
    }

    var singleTarget = true
    val targets = when (val kotlin = kotlinExtensionCompat) {
        is KotlinMultiplatformExtension -> {
            singleTarget = false
            kotlin.targets
        }

        is KotlinSingleTargetExtension<*> -> listOf(kotlin.target)
        else -> emptyList()
    }
    if (targets.none(KotlinTarget::isTsCompat)) {
        logger.warn(
            "{} checks are disabled for {} as no compatible targets found",
            KTS_API, path,
        )
    }

    val dumpDirectory = bcv.apiDumpDirectoryCompat

    // Common BCV tasks for multiplatform
    // Create the own ones (for the raw Kotlin/JS module)
    val apiDump = "$API$SUFFIX_DUMP".let { tasks.maybeRegister(it) }
    val apiCheck = "$API$SUFFIX_CHECK".let { taskName ->
        tasks.maybeRegister(taskName).also { t ->
            tasks.namedCompat { it == CHECK_TASK_NAME }.configureEach {
                dependsOn(t)
            }
        }
    }

    // Follow the strategy of a BCV plugin.
    // API isn't overrided in any way as an extension is different.
    val dirConfig = provider {
        val bcvTargets = targets.filter {
            it.platformType in BCV_PLATFORMS
        }
        when {
            bcvTargets.size > 1 -> DirConfig.TARGET_DIR
            else -> DirConfig.COMMON(bcvTargets.firstOrNull()?.name)
        }
    }
    val state = FluxoBcvTsState(
        singleTarget = singleTarget,
        apiDumpDir = dumpDirectory,
        dirConfig = dirConfig,
        commonApiDump = apiDump,
        commonApiCheck = apiCheck,
        bcv = bcv,
    )

    if (targets is DomainObjectCollection<KotlinTarget>) {
        // Lazy configuration for Kotlin Multiplatform targets.
        targets.configureEach {
            configureTarget(target = this, state)
        }
    } else {
        for (target in targets) {
            configureTarget(target, state)
        }
    }
}

private fun Project.configureTarget(
    target: KotlinTarget,
    state: FluxoBcvTsState,
) {
    if (!target.isTsCompat) {
        return
    }

    val compilations = target.jsCompilationsCompat
        ?.namedCompat { it == MAIN_COMPILATION_NAME }
        .orEmpty()

    if (compilations.isEmpty()) {
        return
    }

    // Find the KotlinJsIrLink tasks in a few ways
    val binaries = LinkedHashSet<JsBinary>().also { binaries ->
        target.tsBinariesCompat?.let { binaries.addAll(it) }
        for (compilation in compilations) {
            if (DBG > 0) {
                logger.lifecycle(
                    " >> compilation {}: {} // {}",
                    compilation.name,
                    compilation,
                    compilation.javaClass,
                )
            }
            compilation.binariesCompat?.let { binaries.addAll(it) }
        }
    }.filterIsInstance<JsIrBinary>()
        .filter { it.mode == KotlinJsBinaryMode.PRODUCTION && it.generateTsCompat != false }

    if (DBG > 0) {
        binaries.forEach {
            logger.warn(" >> binary ${it.name}: $it // ${it.javaClass}")
        }
    }

    val targetName = target.name
    val linkTasksFromBinaries = binaries.mapTo(LinkedHashSet()) { it.linkTask.get() }
    val linkTasksCollection =
        target.project.tasks.withType(KotlinJsIrLink::class.java).matching {
            it.modeCompat == KotlinJsBinaryMode.PRODUCTION &&
                !it.name.contains("Test", ignoreCase = true) &&
                it.name.contains(targetName, ignoreCase = true) &&
                (target.platformType != KotlinPlatformType.js ||
                    !it.name.contains("wasm", ignoreCase = true))
        }
    val linkTasks: Set<KotlinJsIrLink> = linkTasksCollection + linkTasksFromBinaries

    if (DBG > 0) {
        linkTasks.forEach {
            logger.warn(" >> linkTask ${it.name}: $it")
        }
    }
    if (linkTasks.size > 1) {
        logger.warn(
            "Ambigous link tasks for $targetName target $KTS_API verification!" +
                "\n\t${linkTasks.map { it.name }}",
        )
    }

    val targetTsName = targetName.let { n ->
        when {
            n.equals("js", ignoreCase = true) -> TS

            n.contains("js", ignoreCase = true) ->
                n.replace("js", TS_CAP, ignoreCase = true)

            else -> n + TS_CAP
        }
    }

    val targetConfig = TargetConfig(
        project = target.project,
        apiDumpDirectory = state.apiDumpDir,
        targetTsName = targetTsName,
        targetName = targetName,
        dirConfig = state.dirConfig,
    )
    configureKotlinCompilation(this, state, targetConfig, linkTasks)
}

private fun configureKotlinCompilation(
    project: Project,
    state: FluxoBcvTsState,
    targetConfig: TargetConfig,
    linkTasks: Set<KotlinJsIrLink>,
) {
    val buildFileName = when {
        state.singleTarget -> "${project.name}$EXT"
        else -> "${project.name}.${targetConfig.targetName}$EXT"
    }
    val buildDir = project.layout.buildDirectory.dir(targetConfig.apiDirName)
    val buildFile = buildDir.map { it.file(buildFileName) }
    val buildTaskName = targetConfig.apiTaskName(SUFFIX_BUILD)
    val apiBuildTask = project.task<KotlinTsApiBuildTask>(buildTaskName) {
        // Don't enable task for empty umbrella modules
        isEnabled = apiCheckEnabled(this.project.name, state.bcv)
        group = OTHER_GROUP

        generatedDefinitions.setFrom(
            linkTasks.map {
                it.destinationDirectory.asFileTree.matching {
                    this.include("*$EXT")
                }
            },
        )
        dependsOn(linkTasks)

        outputFile.set(buildFile)
    }

    project.configureCheckTasks(
        buildDir,
        buildFile,
        apiBuildTask,
        state,
        targetConfig,
    )
}

private fun Project.configureCheckTasks(
    buildDir: Provider<Directory>,
    buildFile: Provider<RegularFile>,
    apiBuildTask: TaskProvider<out Task>,
    state: FluxoBcvTsState,
    config: TargetConfig,
) {
    val apiCheckTaskName = config.apiTaskName(SUFFIX_CHECK)
    val apiDumpTaskName = config.apiTaskName(SUFFIX_DUMP)

    val apiCheckDir = config.apiDir

    val apiCheck: TaskProvider<out DefaultTask>

    fun DefaultTask.configureApiCheckTask() {
        isEnabled = apiCheckEnabled(project.name, state.bcv)
            && apiBuildTask.map { it.enabled }.getOrElse(true)
        group = VERIFICATION_GROUP
        description = "Checks signatures of public TypeScript API against the " +
            "golden value in API folder for :${project.name}"
        dependsOn(apiBuildTask)
    }

    val referenceFile = apiCheckDir.zip(buildFile) { dir, file ->
        dir.file(file.asFile.name)
    }

    fun registerCustomApiCheckTask() = task<KotlinTsApiCompareTask>(apiCheckTaskName) {
        configureApiCheckTask()
        compareApiDumps(
            referenceFile = referenceFile.get().asFile,
            buildFile = buildFile.get().asFile,
        )
    }

    val apiDump = task<DefaultTask>(apiDumpTaskName) {
        isEnabled = apiCheckEnabled(project.name, state.bcv) &&
            apiBuildTask.map { it.enabled }.getOrElse(true)
        group = OTHER_GROUP

        val dirName = config.apiDirName.get()
        description = "Syncs API from build dir to $dirName dir for :${project.name}"

        dependsOn(apiBuildTask)

        inputs.file(buildFile)
        outputs.file(referenceFile)
        doLast {
            val source = buildFile.get().asFile
            val target = referenceFile.get().asFile
            source.copyTo(target, overwrite = true)
            if (DBG > 0) {
                logger.lifecycle(" >> Copied API file: {}", target)
            }
        }
    }

    // Special case
    // BCV has the 'COMMON' dir strategy and uses 'api' dir for comparison.
    val dirConfig = config.dirConfig?.get()
    if (dirConfig is DirConfig.COMMON && dirConfig.bcvTargetName != null) {
        val bcvTargetName = dirConfig.bcvTargetName
        logger.info(
            "Special handling for BCV 'DirConfig.COMMON' strategy (bcvTargetName={})",
            bcvTargetName,
        )

        val bcvCheckTaskName = apiTaskName(bcvTargetName, SUFFIX_CHECK)
        val bcvBuild = tasks.named(apiTaskName(bcvTargetName, SUFFIX_BUILD))
        val bcvDump = tasks.named(apiTaskName(bcvTargetName, SUFFIX_DUMP))
        val bcvCheck = tasks.named(bcvCheckTaskName)

        // Avoid conflicts with bcvCheck task.
        // It doesn't like extra files in the build directory.
        val bcvCheckCleaner = tasks.maybeRegister(bcvCheckTaskName + "TsCompatCleaner") {
            group = OTHER_GROUP
            doLast {
                val file = buildFile.get().asFile
                if (file.delete()) {
                    val msg = " >> Removed {} file for compatibility with '{}' task: {}"
                    when {
                        DBG <= 0 -> logger.info(msg, KTS_API, bcvCheckTaskName, file)
                        else -> logger.lifecycle(msg, KTS_API, bcvCheckTaskName, file)
                    }
                }
            }
        }
        bcvCheck.configure {
            dependsOn(bcvCheckCleaner)
        }
        apiBuildTask.configure {
            mustRunAfter(bcvCheckCleaner)
            mustRunAfter(bcvBuild)
            mustRunAfter(bcvCheck)
        }

        // BCV's dump task uses this output of 'jsApiBuild' task
        bcvDump.configure {
            dependsOn(apiBuildTask)
        }

        apiCheck = registerCustomApiCheckTask()
    } else {
        apiCheck = try {
            task<KotlinApiCompareTask>(apiCheckTaskName) {
                configureApiCheckTask()

                /** @see KotlinApiCompareTask.compareApiDumps */
                val apiReferenceDir = apiCheckDir.get().asFile
                when {
                    apiReferenceDir.exists() -> {
                        projectApiDir = apiReferenceDir
                    }

                    else -> try {
                        projectApiDir = null
                        nonExistingProjectApiDir = apiReferenceDir.toString()
                    } catch (e: Throwable) {
                        logger.warn(e.toString(), e)
                        projectApiDir = apiReferenceDir
                    }
                }
                apiBuildDir = buildDir.get().asFile
            }
        } catch (e: Throwable) {
            // Expected for old BCV
            @Suppress("InstanceOfCheckForException")
            if (e !is NoClassDefFoundError) {
                val message = "Unexpected error: $e \n" +
                    "Please, submit an issue to https://github.com/fluxo-kt/fluxo-bcv-js/issues"
                logger.warn(message, e)
            }
            registerCustomApiCheckTask()
        }
    }

    state.commonApiDump.configure {
        dependsOn(apiDump)
    }
    state.commonApiCheck.configure {
        dependsOn(apiCheck)
    }
}
