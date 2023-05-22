package fluxo.bcvjs

import kotlinx.validation.ApiValidationExtension
import kotlinx.validation.KotlinApiCompareTask
import kotlinx.validation.task
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsBinaryContainer
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import java.io.File
import java.lang.reflect.AccessibleObject

private const val EXT = ".d.ts"

/**
 *
 * @see kotlinx.validation.BinaryCompatibilityValidatorPlugin.configureMultiplatformPlugin
 */
internal fun Project.configureJsApiTasks(
    kotlin: KotlinMultiplatformExtension,
) {
    val extension = apiValidationExtensionOrNull
    if (!apiCheckEnabled(name, extension)) {
        return
    }

    // Common BCV tasks for multiplatform
    val commonApiDump = tasks.named("apiDump")
    val commonApiCheck = tasks.named("apiCheck")

    // Follow the strategy of BCV plugin.
    // API isn't overrided as an extension is different.
    val jvmTargetCountProvider = provider {
        kotlin.targets.count {
            it.platformType in arrayOf(
                KotlinPlatformType.jvm,
                KotlinPlatformType.androidJvm,
            )
        }
    }
    val dirConfig = jvmTargetCountProvider.map {
        if (it == 1) DirConfig.COMMON else DirConfig.TARGET_DIR
    }

    kotlin.targets.withType(KotlinJsIrTarget::class.java).configureEach { target ->
        val targetConfig = TargetConfig(project, this.name, dirConfig)
        target.compilations.matching { it.name == "main" }.configureEach {
            configureKotlinCompilation(it, extension, targetConfig, commonApiDump, commonApiCheck)
        }
    }
}

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

private fun apiCheckEnabled(projectName: String, extension: ApiValidationExtension?): Boolean {
    return extension == null || projectName !in extension.ignoredProjects && !extension.validationDisabled
}


private fun Project.configureKotlinCompilation(
    compilation: KotlinJsCompilation,
    extension: ApiValidationExtension?,
    targetConfig: TargetConfig,
    commonApiDump: TaskProvider<Task>? = null,
    commonApiCheck: TaskProvider<Task>? = null,
) {
    val projectName = project.name
    val apiDirProvider = targetConfig.apiDir
    val apiBuildDir = apiDirProvider.map { buildDir.resolve(it) }

    val binaries = compilation.binariesCompat
        .withType(JsIrBinary::class.java)
        .matching { it.mode == KotlinJsBinaryMode.PRODUCTION && it.generateTsCompat != false }

    val linkTasks: Provider<Set<KotlinJsIrLink>> = provider {
        binaries.mapTo(LinkedHashSet()) { it.linkTask.get() }
    }

    if (binaries.isEmpty()) {
        logger.warn(
            "Please, enable TS definitions with `generateTypeScriptDefinitions()` for :" +
                project.name + ", $compilation. No production binaries found with TS definitions enabled. " +
                "Kotlin JS API verification is not possible."
        )
    }

    val apiBuild = project.tasks.register(targetConfig.apiTaskName("Build"), KotlinJsApiBuildTask::class.java) { task ->
        // Don't enable task for empty umbrella modules
        task.isEnabled = apiCheckEnabled(projectName, extension) &&
            compilation.allKotlinSourceSets.any { it.kotlin.srcDirs.any { d -> d.exists() } }

        val definitions: Provider<Set<File>> = linkTasks.flatMap { set ->
            provider {
                set.flatMapTo(LinkedHashSet()) { link ->
                    link.destinationDirectory.asFileTree.matching { it.include("*$EXT") }.files
                }
            }
        }
        task.generatedDefinitions.from(definitions)
        task.dependsOn(linkTasks)

        task.outputFile.set(apiBuildDir.get().resolve(project.name + EXT))
    }
    configureCheckTasks(apiBuildDir, apiBuild, extension, targetConfig, commonApiDump, commonApiCheck)
}

private val KotlinJsCompilationBinariesCaller: (KotlinJsCompilation.() -> KotlinJsBinaryContainer) = run {
    val clazz = KotlinJsCompilation::class.java
    try {
        val method = clazz.methods.firstOrNull { it.name.startsWith("getBinaries") }
            ?: clazz.methods.firstOrNull { "getBinaries" in it.name }
        if (method != null) {
            return@run { method.invoke(this) as KotlinJsBinaryContainer }
        }
    } catch (_: Throwable) {
    }
    val field = JsIrBinary::class.java.getDeclaredField("binaries")
    setAccessible(field)
    return@run { field.get(this) as KotlinJsBinaryContainer }
}

private val KotlinJsCompilation.binariesCompat: KotlinJsBinaryContainer
    get() {
        return try {
            KotlinJsCompilationBinariesCaller(this)
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            val error = "Can't access KotlinJsCompilation.binaries, please check Kotlin version compatibility"
            throw IllegalStateException(error, e)
        }
    }

/** Compatibility for old Kotlin versions */
private val JsIrBinaryGenerateTsCaller: (JsIrBinary.() -> Boolean?) = run {
    try {
        val method = JsIrBinary::class.java.getDeclaredMethod("getGenerateTs")
        return@run { method.invoke(this) as? Boolean }
    } catch (_: Throwable) {
    }
    try {
        val field = JsIrBinary::class.java.getDeclaredField("generateTs")
        setAccessible(field)
        return@run { field.get(this) as? Boolean }
    } catch (_: Throwable) {
    }
    return@run { null }
}

private fun setAccessible(field: AccessibleObject) {
    try {
        @Suppress("Since15")
        field.trySetAccessible()
    } catch (_: Throwable) {
        try {
            field.isAccessible = true
        } catch (_: Throwable) {
        }
    }
}

private val JsIrBinary.generateTsCompat: Boolean?
    get() {
        return try {
            JsIrBinaryGenerateTsCaller(this)
        } catch (_: Throwable) {
            null
        }
    }


@Suppress("LongParameterList")
private fun Project.configureCheckTasks(
    apiBuildDir: Provider<File>,
    apiBuild: TaskProvider<out Task>,
    extension: ApiValidationExtension?,
    targetConfig: TargetConfig,
    commonApiDump: TaskProvider<out Task>? = null,
    commonApiCheck: TaskProvider<out Task>? = null,
) {
    val projectName = project.name
    val apiCheckDir = targetConfig.apiDir.map {
        projectDir.resolve(it).also { r ->
            logger.debug("Configuring api for {} to {}", targetConfig.targetName ?: "jvm", r)
        }
    }
    val apiCheck = task<KotlinApiCompareTask>(targetConfig.apiTaskName("Check")) {
        isEnabled = apiCheckEnabled(projectName, extension) && apiBuild.map { it.enabled }.getOrElse(true)
        group = "verification"
        description = "Checks signatures of public API against the golden value in API folder for $projectName"
        compareApiDumps(apiReferenceDir = apiCheckDir.get(), apiBuildDir = apiBuildDir.get())
        dependsOn(apiBuild)
    }

    val apiDump = task<Sync>(targetConfig.apiTaskName("Dump")) {
        isEnabled = apiCheckEnabled(projectName, extension) && apiBuild.map { it.enabled }.getOrElse(true)
        group = "other"
        description = "Syncs API from build dir to ${targetConfig.apiDir} dir for $projectName"
        from(apiBuildDir)
        into(apiCheckDir)
        dependsOn(apiBuild)
    }

    commonApiDump?.configure { it.dependsOn(apiDump) }

    (commonApiCheck ?: project.tasks.named("check"))
        .configure { it.dependsOn(apiCheck) }
}
