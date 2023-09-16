@file:Suppress(
    "KDocUnresolvedReference",
    "KotlinConstantConditions",
    "LongMethod",
    "LoopWithTooManyJumpStatements",
    "NoConsecutiveBlankLines",
    "NoWildcardImports",
    "ReturnCount",
    "TooGenericExceptionCaught",
    "WildcardImport",
)

package fluxo.bcvjs

import kotlinx.validation.ApiValidationExtension
import kotlinx.validation.KotlinApiCompareTask
import org.gradle.api.*
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.ir.*
import java.io.File
import java.lang.reflect.AccessibleObject


private const val DBG = 0
private const val KOTLIN_MIN_VERSION = "1.6.20"
private const val EXT = ".d.ts"
private const val API = "api"
private const val SUFFIX_BUILD = "Build"
private const val SUFFIX_DUMP = "Dump"
private const val SUFFIX_CHECK = "Check"

private val BCV_PLATFORMS = arrayOf(
    KotlinPlatformType.jvm,
    KotlinPlatformType.androidJvm,
)

private fun apiCheckEnabled(projectName: String, extension: ApiValidationExtension?): Boolean {
    return extension == null || projectName !in extension.ignoredProjects && !extension.validationDisabled
}


/**
 *
 * @see kotlinx.validation.BinaryCompatibilityValidatorPlugin.configureMultiplatformPlugin
 */
@Suppress("CyclomaticComplexMethod")
internal fun Project.configureJsApiTasks() {
    val kotlinVersion = safe { getKotlinPluginVersion() }
    val kotlinVersionChecked = kotlinVersion?.let { v ->
        safe { isVersionGreaterThanOrEqual(v, KOTLIN_MIN_VERSION) }
    } ?: false
    if (!kotlinVersionChecked) {
        val versionText = when {
            kotlinVersion.isNullOrEmpty() -> ""
            else -> ", your version is '$kotlinVersion'"
        }

        @Suppress("MaxLineLength")
        val message = "You need at least Kotlin $KOTLIN_MIN_VERSION " +
            "to enable Kotlin/JS API verification$versionText. \n" +
            "Please, update your Kotlin! More details at " +
            "https://kotlinlang.org/docs/whatsnew1620.html#improvements-to-export-and-typescript-declaration-generation"
        logger.error(message)
        return
    }

    val targets = when (val kotlin = kotlinExtensionCompat) {
        is KotlinMultiplatformExtension -> kotlin.targets.toSet()
        is KotlinSingleTargetExtension<*> -> setOf(kotlin.target)
        else -> emptySet()
    }
    if (targets.none { it.isJsCompat }) {
        logger.warn("Kotlin/JS API checks are disabled for :$name as no Kotlin/JS target found")
        return
    }

    val extension = apiValidationExtensionOrNull
    if (!apiCheckEnabled(name, extension)) {
        logger.info("Kotlin/JS API checks are disabled for :$name")
        return
    }

    // Common BCV tasks for multiplatform
    // Create own ones (for the raw Kotlin/JS module)
    val commonApiDump = "$API$SUFFIX_DUMP".let { tasks.findByName(it) ?: tasks.create(it) }
    val commonApiCheck = "$API$SUFFIX_CHECK".let {
        tasks.findByName(it)
            ?: tasks.create(it)
                .also { t -> tasks.getByName("check").dependsOn(t) }
    }

    // Follow the strategy of BCV plugin.
    // API isn't overrided in any way as an extension is different.
    val dirConfig = provider {
        val bcvTargets = targets.filter { it.platformType in BCV_PLATFORMS }
        when {
            bcvTargets.size > 1 -> DirConfig.TARGET_DIR
            else -> DirConfig.COMMON(bcvTargets.firstOrNull()?.name)
        }
    }

    for (target in targets) {
        val compilations = target.jsCompilationsCompat?.matching { it.name == "main" }.orEmpty()
        if (compilations.isEmpty() && !target.isJsCompat) {
            continue
        }

        // Find the KotlinJsIrLink tasks in a few ways
        val binaries = LinkedHashSet<JsBinary>().also { binaries ->
            target.jsBinariesCompat?.let { binaries.addAll(it) }
            for (compilation in compilations) {
                if (DBG > 0) {
                    logger.lifecycle(" >> compilation ${compilation.name}: $compilation // ${compilation.javaClass}")
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

        val linkTasksFromBinaries = binaries.mapTo(LinkedHashSet()) { it.linkTask.get() }
        val linkTasksCollection = target.project.tasks.withType(KotlinJsIrLink::class.java).matching {
            it.mode == KotlinJsBinaryMode.PRODUCTION &&
                !it.name.contains("Test", ignoreCase = true) &&
                it.name.contains(target.name, ignoreCase = true)
        }
        val linkTasks: Provider<Set<KotlinJsIrLink>> = project.provider { linkTasksCollection + linkTasksFromBinaries }

        if (DBG > 0) {
            linkTasks.get().forEach {
                logger.warn(" >> linkTask ${it.name}: $it")
            }
        }

        val targetConfig = TargetConfig(target.project, target.name, dirConfig)
        configureKotlinCompilation(extension, targetConfig, commonApiDump, commonApiCheck, linkTasks)
    }
}

private fun Project.configureKotlinCompilation(
    extension: ApiValidationExtension?,
    targetConfig: TargetConfig,
    commonApiDump: Task,
    commonApiCheck: Task?,
    linkTasks: Provider<Set<KotlinJsIrLink>>,
) {
    val tsDefinitionFiles: Provider<Set<File>> = linkTasks.flatMap { set ->
        project.provider {
            set.flatMapTo(LinkedHashSet()) { link ->
                link.destinationDirectory.asFileTree.matching { it.include("*$EXT") }.files
            }
        }
    }

    val buildDir = targetConfig.apiDir.map { layout.buildDirectory.asFile.get().resolve(it) }
    val buildFile = buildDir.map { it.resolve(project.name + EXT) }
    val buildTaskName = targetConfig.apiTaskName(SUFFIX_BUILD)
    val apiBuild = project.task<KotlinJsApiBuildTask>(buildTaskName) {
        // Don't enable task for empty umbrella modules
        it.isEnabled = apiCheckEnabled(project.name, extension)

        it.generatedDefinitions.from(tsDefinitionFiles)
        it.dependsOn(linkTasks)

        it.outputFile.fileProvider(buildFile)
    }
    project.configureCheckTasks(buildDir, buildFile, apiBuild, extension, targetConfig, commonApiDump, commonApiCheck)
}

@Suppress("LongParameterList")
private fun Project.configureCheckTasks(
    buildDir: Provider<File>,
    buildFile: Provider<File>,
    apiBuild: TaskProvider<out Task>,
    extension: ApiValidationExtension?,
    config: TargetConfig,
    commonApiDump: Task,
    commonApiCheck: Task?,
) {
    val apiCheckTaskName = config.apiTaskName(SUFFIX_CHECK)
    val apiDumpTaskName = config.apiTaskName(SUFFIX_DUMP)

    val apiCheckDir = config.apiDir.map {
        projectDir.resolve(it).also { r ->
            logger.debug("Configuring api for {} to {}", config.targetName ?: "js", r)
        }
    }

    val apiCheck: TaskProvider<out DefaultTask>

    fun configureApiCheckTask(t: DefaultTask) {
        t.isEnabled = apiCheckEnabled(t.project.name, extension) && apiBuild.map { it.enabled }.getOrElse(true)
        t.group = "verification"
        t.description = "Checks signatures of public API against the golden value in API folder for :${t.project.name}"
        t.dependsOn(apiBuild)
    }

    fun registerCustomApiCheckTask() = task<KotlinJsApiCompareTask>(apiCheckTaskName) { t ->
        configureApiCheckTask(t)
        val apiBuildFile = buildFile.get()
        val referenceFile = File(apiCheckDir.get(), apiBuildFile.name)
        t.compareApiDumps(apiReferenceFile = referenceFile, apiBuildFile = apiBuildFile)
    }

    val apiDump = task<Sync>(apiDumpTaskName) { t ->
        t.isEnabled = apiCheckEnabled(project.name, extension) && apiBuild.map { it.enabled }.getOrElse(true)
        t.group = "other"
        t.description = "Syncs API from build dir to ${config.apiDir} dir for :${project.name}"
        t.dependsOn(apiBuild)
        t.from(buildDir)
        t.into(apiCheckDir)
    }

    // Special case
    // BCV has the 'COMMON' dir strategy and uses 'api' dir for comparison.
    val dirConfig = config.dirConfig?.get()
    if (dirConfig is DirConfig.COMMON && dirConfig.bcvTargetName != null) {
        val bcvTargetName = dirConfig.bcvTargetName
        logger.info("Special handling for BCV 'DirConfig.COMMON' strategy (bcvTargetName={})", bcvTargetName)

        val bcvCheckTaskName = apiTaskName(bcvTargetName, SUFFIX_CHECK)
        val bcvBuild = tasks.named(apiTaskName(bcvTargetName, SUFFIX_BUILD))
        val bcvDump = tasks.named(apiTaskName(bcvTargetName, SUFFIX_DUMP))
        val bcvCheck = tasks.named(bcvCheckTaskName)

        // Avoid conflicts with bcvCheck task.
        // It doesn't like extra files in the build directory.
        val bcvCheckCleaner = defaultTask(bcvCheckTaskName + "JsCompatCleaner") { t ->
            t.doLast {
                val file = buildFile.get()
                if (file.delete()) {
                    logger.info("Removed Kotlin/JS API file for compatibility with '$bcvCheckTaskName' task: {}", file)
                }
            }
        }
        bcvCheck.configure { t ->
            t.dependsOn(bcvCheckCleaner)
        }
        apiBuild.configure { t ->
            t.mustRunAfter(bcvCheckCleaner)
            t.mustRunAfter(bcvBuild)
            t.mustRunAfter(bcvCheck)
        }

        // BCV's dump task uses this output of 'jsApiBuild' task
        bcvDump.configure { t ->
            t.dependsOn(apiBuild)
        }

        apiCheck = registerCustomApiCheckTask()
    } else {
        apiCheck = try {
            task<KotlinApiCompareTask>(apiCheckTaskName) { t ->
                configureApiCheckTask(t)

                /** @see KotlinApiCompareTask.compareApiDumps */
                val apiReferenceDir = apiCheckDir.get()
                when {
                    apiReferenceDir.exists() -> {
                        t.projectApiDir = apiReferenceDir
                    }

                    else -> try {
                        t.projectApiDir = null
                        t.nonExistingProjectApiDir = apiReferenceDir.toString()
                    } catch (e: Throwable) {
                        logger.warn(e.toString(), e)
                        t.projectApiDir = apiReferenceDir
                    }
                }
                t.apiBuildDir = buildDir.get()
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

    commonApiDump.dependsOn(apiDump)

    (commonApiCheck ?: tasks.getByName("check"))
        .dependsOn(apiCheck)
}


// region Compatibility utils

/**
 * Get the extension manually instead of `kotlinExtension` helper to support old Kotlin.
 *
 * @see org.jetbrains.kotlin.gradle.dsl.kotlinExtension
 * @see org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
 * @see org.jetbrains.kotlin.gradle.dsl.KOTLIN_PROJECT_EXTENSION_NAME ("kotlin")
 */
private val Project.kotlinExtensionCompat: KotlinProjectExtension
    get() = extensions.getByName("kotlin") as KotlinProjectExtension


private val KotlinTarget.isJsCompat: Boolean
    get() {
        safe { if (name == "js") return true }
        safe { if (platformType == KotlinPlatformType.js) return true }
        safe { if (this is KotlinJsIrTarget) return true }
        safe { if (this is KotlinJsTarget) return true }
        safe { if (this is KotlinJsTargetDsl) return true }
        return false
    }

/**
 * @see KotlinJsIrTarget.binaries
 * @see KotlinJsTarget.binaries
 * @see KotlinJsTargetDsl.binaries
 */
private val KotlinTarget.jsBinariesCompat: KotlinJsBinaryContainer?
    get() {
        safe { if (this is KotlinJsIrTarget) return binaries }
        safe { if (this is KotlinJsTarget) return binaries }
        safe { if (this is KotlinJsTargetDsl) return binaries }
        return null
    }

/**
 * @see KotlinJsIrTarget.compilations
 * @see KotlinJsTarget.compilations
 */
private val KotlinTarget.jsCompilationsCompat: NamedDomainObjectContainer<out KotlinJsCompilation>?
    get() {
        safe {
            if (this is KotlinJsIrTarget) {
                generateTypeScriptDefinitionsCaller()
                return compilations
            }
        }
        safe { if (this is KotlinJsTarget) return compilations }
        return null
    }


/** @see KotlinJsTargetDsl.generateTypeScriptDefinitions */
@Volatile
internal var hasGenerateTypeScriptDefinitions: Boolean = false

/** @see KotlinJsTargetDsl.generateTypeScriptDefinitions */
private val generateTypeScriptDefinitionsCaller: (KotlinJsTargetDsl.() -> Unit) = run {
    val clazz = KotlinJsTargetDsl::class.java
    safe<Unit> {
        val method = clazz.methods.firstOrNull { "generateTypeScriptDefinitions" in it.name }
        if (method != null) {
            hasGenerateTypeScriptDefinitions = true
            return@run { method.invoke(this) }
        }
    }
    return@run {}
}


/** @see ApiValidationExtension */
private val Project.apiValidationExtensionOrNull: ApiValidationExtension?
    get() {
        val clazz: Class<*> = try {
            ApiValidationExtension::class.java
        } catch (_: Throwable) {
            return null
        }
        return generateSequence(this) { it.parent }
            .map { it.extensions.findByType(clazz) }
            .firstOrNull { it != null } as ApiValidationExtension?
    }


/** @see KotlinJsCompilation.binaries */
@Suppress("IdentifierGrammar")
private val KotlinJsCompilationBinariesCaller: (KotlinJsCompilation.() -> KotlinJsBinaryContainer) = run {
    val clazz = KotlinJsCompilation::class.java
    safe<Unit> {
        val method = clazz.methods.firstOrNull { it.name.startsWith("getBinaries") }
            ?: clazz.methods.firstOrNull { "getBinaries" in it.name }
        if (method != null) {
            return@run { method.invoke(this) as KotlinJsBinaryContainer }
        }
    }
    val field = JsIrBinary::class.java.getDeclaredField("binaries")
    setAccessible(field)
    return@run { field.get(this) as KotlinJsBinaryContainer }
}

/** @see KotlinJsCompilation.binaries */
private val KotlinJsCompilation.binariesCompat: KotlinJsBinaryContainer?
    get() = safe { KotlinJsCompilationBinariesCaller(this) }


/** @see JsIrBinary.generateTs */
private val JsIrBinaryGenerateTsCaller: (JsIrBinary.() -> Boolean?) = run {
    safe<Unit> {
        val method = JsIrBinary::class.java.getDeclaredMethod("getGenerateTs")
        return@run { method.invoke(this) as? Boolean }
    }
    safe<Unit> {
        val field = JsIrBinary::class.java.getDeclaredField("generateTs")
        setAccessible(field)
        return@run { field.get(this) as? Boolean }
    }
    return@run { null }
}

/** @see JsIrBinary.generateTs */
private val JsIrBinary.generateTsCompat: Boolean?
    get() = safe { JsIrBinaryGenerateTsCaller(this) }


private inline fun <reified T : Task> Project.task(
    name: String,
    noinline configuration: (T) -> Unit,
): TaskProvider<T> = tasks.register(name, T::class.java, Action(configuration))

private fun Project.defaultTask(name: String, configuration: (DefaultTask) -> Unit) =
    task<DefaultTask>(name, configuration)

@Suppress("SameParameterValue")
private fun isVersionGreaterThanOrEqual(version: String, targetVersion: String): Boolean {
    val parts = version.split(".")
    val targetParts = targetVersion.split(".")
    for (i in 0 until parts.size.coerceAtLeast(targetParts.size)) {
        val part = parts.getOrNull(i)?.toIntOrNull() ?: 0
        val targetPart = targetParts.getOrNull(i)?.toIntOrNull() ?: 0
        if (part > targetPart) {
            return true
        } else if (part < targetPart) {
            return false
        }
    }
    return true // equal to the target version
}

private fun setAccessible(field: AccessibleObject) {
    try {
        @Suppress("Since15")
        field.trySetAccessible()
    } catch (_: Throwable) {
        safe { field.isAccessible = true }
    }
}

private inline fun <R> safe(body: () -> R): R? {
    return try {
        body()
    } catch (_: Throwable) {
        null
    }
}

// endregion
