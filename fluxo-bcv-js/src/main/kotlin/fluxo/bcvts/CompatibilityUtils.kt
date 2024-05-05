@file:Suppress("KDocUnresolvedReference")

package fluxo.bcvts

import java.lang.reflect.AccessibleObject
import kotlinx.validation.ApiValidationExtension
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.HasBinaries
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.KotlinWasmTargetType
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsBinaryContainer
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget

// WASM WASI is incompatible with the TS generation in Kotlin 2.0.0.
private const val ALLOW_WASM_WASI = false


/**
 * A path to a directory containing an API dump.
 * The path should be relative to the project's root directory
 * and should resolve to its subdirectory.
 * By default, it's `api`.
 *
 * It was a constant in the old version of the plugin, before 0.14.0.
 *
 * @see ApiValidationExtension.apiDumpDirectory
 * @see kotlinx.validation.API_DIR
 */
internal val ApiValidationExtension?.apiDumpDirectoryCompat: String
    get() {
        return try {
            this?.apiDumpDirectory
        } catch (_: Throwable) {
            null
        } ?: DEFAULT_API_DIR
    }

private const val DEFAULT_API_DIR = "api"


/**
 * Get the extension manually instead of `kotlinExtension` helper to support old Kotlin.
 *
 * @see org.jetbrains.kotlin.gradle.dsl.kotlinExtension
 * @see org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
 * @see org.jetbrains.kotlin.gradle.dsl.KOTLIN_PROJECT_EXTENSION_NAME ("kotlin")
 */
internal val Project.kotlinExtensionCompat: KotlinProjectExtension
    get() = extensions.getByName("kotlin") as KotlinProjectExtension


internal val KotlinTarget.isTsCompat: Boolean
    get() {
        safe { if (name == "js" || name == "wasmJs") return true }
        safe { if (platformType == KotlinPlatformType.js) return true }
        safe { if (this is KotlinJsIrTarget) return true }
        safe { if (this is KotlinJsTargetDsl) return true }
        safe { if (platformType == KotlinPlatformType.wasm) return true }
        return false
    }

/**
 * @see KotlinJsIrTarget.binaries
 * @see KotlinJsTarget.binaries
 * @see KotlinJsTargetDsl.binaries
 */
internal val KotlinTarget.tsBinariesCompat: KotlinJsBinaryContainer?
    get() {
        safe { if (this is KotlinJsIrTarget) return binaries }
        safe { if (this is KotlinJsTargetDsl) return binaries }
        safe { if (this is HasBinaries<*>) return binaries as? KotlinJsBinaryContainer }
        return null
    }

/**
 * @see KotlinJsIrTarget.compilations
 * @see KotlinJsTarget.compilations
 */
internal val KotlinTarget.jsCompilationsCompat: NamedDomainObjectContainer<out KotlinJsCompilation>?
    get() {
        safe {
            if (this is KotlinJsIrTarget) {
                safe {
                    if (!ALLOW_WASM_WASI &&
                        (name.contains("WASI", ignoreCase = true) ||
                            wasmTargetType == KotlinWasmTargetType.WASI)
                    ) {
                        return null
                    }
                }

                /** @see KotlinJsTargetDsl.generateTypeScriptDefinitions */
                generateTypeScriptDefinitionsCaller()
                return compilations
            }
        }
        return null
    }


/** @see KotlinJsTargetDsl.generateTypeScriptDefinitions */
@Volatile
internal var hasGenerateTypeScriptDefinitions: Boolean = false

/** @see KotlinJsTargetDsl.generateTypeScriptDefinitions */
internal val generateTypeScriptDefinitionsCaller: (KotlinJsTargetDsl.() -> Unit) = run {
    val clazz = KotlinJsTargetDsl::class.java
    safe<Unit> {
        /** @see KotlinJsTargetDsl.generateTypeScriptDefinitions */
        val method = clazz.methods.firstOrNull { "generateTypeScriptDefinitions" in it.name }
        if (method != null) {
            hasGenerateTypeScriptDefinitions = true
            return@run {
                method.invoke(this)
            }
        }
    }
    return@run {}
}


/** @see ApiValidationExtension */
internal val Project.apiValidationExtensionOrNull: ApiValidationExtension?
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
internal val KJsCompBinariesCaller: (KotlinJsCompilation.() -> KotlinJsBinaryContainer) =
    run {
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
internal val KotlinJsCompilation.binariesCompat: KotlinJsBinaryContainer?
    get() = safe { KJsCompBinariesCaller(this) }


/** @see JsIrBinary.generateTs */
internal val JsIrBinaryGenerateTsCaller: (JsIrBinary.() -> Boolean?) = run {
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
internal val JsIrBinary.generateTsCompat: Boolean?
    get() = safe { JsIrBinaryGenerateTsCaller(this) }


internal val KotlinJsIrLink.modeCompat: KotlinJsBinaryMode
    get() {
        return try {
            @Suppress("DEPRECATION")
            mode
        } catch (_: Throwable) {
            // modeProperty is internal, unfortunately.
            @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
            modeProperty.get()
        }
    }


internal fun setAccessible(field: AccessibleObject) {
    try {
        @Suppress("Since15")
        field.trySetAccessible()
    } catch (_: Throwable) {
        safe { field.isAccessible = true }
    }
}


internal inline fun <R> safe(body: () -> R): R? {
    return try {
        body()
    } catch (_: Throwable) {
        null
    }
}
