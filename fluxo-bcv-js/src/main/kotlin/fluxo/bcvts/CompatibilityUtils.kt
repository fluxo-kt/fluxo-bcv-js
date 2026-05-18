@file:Suppress("KDocUnresolvedReference")

package fluxo.bcvts

import java.lang.reflect.AccessibleObject
import kotlinx.validation.ApiValidationExtension
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
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
        // `getMethod` is inheritance-aware (walks supertypes/superinterfaces)
        // and signature-strict via the implicit no-arg varargs binding —
        // exact name match, exactly zero parameters. `getDeclaredMethod`
        // would miss the method when KGP refactors it to a parent
        // interface; `methods.firstOrNull { startsWith(...) }` would
        // false-match `*$default` synthetics or future overloads.
        val method = clazz.getMethod("generateTypeScriptDefinitions")
        hasGenerateTypeScriptDefinitions = true
        return@run { method.invoke(this) }
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
        // `getMethod` is the right seam here: KotlinJsCompilation is an
        // interface in every supported KGP, and the `binaries` getter is
        // commonly inherited from a parent interface (e.g.
        // `KotlinJsCompilation : KotlinCompilation<...>` chain).
        // `getDeclaredMethod` would miss inherited declarations; the
        // legacy `methods.firstOrNull { startsWith(...) }` would also
        // false-match `getBinariesFor*`-style additions. `getMethod`
        // walks the hierarchy and binds signature-strictly to the
        // no-arg overload via the implicit empty varargs.
        safe<Unit> {
            val method = clazz.getMethod("getBinaries")
            return@run { method.invoke(this) as KotlinJsBinaryContainer }
        }
        // No fallback path resolves: the previous JsIrBinary
        // back-reference field was both absent in modern KGP and
        // receiver-typed wrong (would have thrown at invocation, not
        // returned a container). Surface as an invocation-time error so
        // `binariesCompat`'s upper `safe { }` returns null cleanly,
        // never throws at class init.
        return@run { error("KotlinJsCompilation.binaries shim unresolved") }
    }

/** @see KotlinJsCompilation.binaries */
internal val KotlinJsCompilation.binariesCompat: KotlinJsBinaryContainer?
    get() = safe { KJsCompBinariesCaller(this) }


/** @see JsIrBinary.generateTs */
internal val JsIrBinaryGenerateTsCaller: (JsIrBinary.() -> Boolean?) = run {
    val clazz = JsIrBinary::class.java
    safe<Unit> {
        // Inheritance-aware: `JsIrBinary` is an interface whose
        // `generateTs` accessor may live on a parent supertype in some
        // KGP versions. `getMethod` walks the hierarchy and binds
        // signature-strictly to the no-arg overload.
        val method = clazz.getMethod("getGenerateTs")
        return@run { method.invoke(this) as? Boolean }
    }
    safe<Unit> {
        // Direct-field path: an early KGP exposed `generateTs` as a
        // plain field rather than a property. Use getDeclaredField
        // (fields, unlike methods, are NOT inherited via reflection).
        val field = clazz.getDeclaredField("generateTs")
        setAccessible(field)
        return@run { field.get(this) as? Boolean }
    }
    return@run { null }
}

/** @see JsIrBinary.generateTs */
internal val JsIrBinary.generateTsCompat: Boolean?
    get() = safe { JsIrBinaryGenerateTsCaller(this) }


// Both direct call paths are compile-time poison under Kotlin 2.3+:
// `KotlinJsIrLink.mode` is ERROR-level deprecated (KT-81010, Kotlin
// 2.3.0 compatibility guide), and `modeProperty` is KGP-internal so
// any `@Suppress("INVISIBLE_MEMBER")` is a brittle compile-time bond
// to a private API.
// Fully reflective access survives both the ERROR-level deprecation
// and a future physical symbol removal in Kotlin 2.4+. On failure
// `safe { }` returns null and the binary is silently skipped.
private val KotlinJsIrLinkModeCompatCaller: KotlinJsIrLink.() -> KotlinJsBinaryMode? = run {
    val clazz = KotlinJsIrLink::class.java
    // KGP ≤ 2.2.x: public `mode` property → public getter `getMode()`.
    // `getMethod` is inheritance-aware; KGP may move the getter to a
    // parent abstract class across versions.
    safe<Unit> {
        val m = clazz.getMethod("getMode")
        if (KotlinJsBinaryMode::class.java.isAssignableFrom(m.returnType)) {
            return@run { m.invoke(this) as? KotlinJsBinaryMode }
        }
    }
    // KGP 2.2+: internal `modeProperty: Property<KotlinJsBinaryMode>`.
    // Kotlin `internal` members get JVM name-mangling `$<module>` (e.g.
    // `getModeProperty$kotlin_gradle_plugin_common`). `getMethod`/
    // `getDeclaredMethod` against the unmangled name cannot find these;
    // a focused iteration that accepts exact-or-mangle is required.
    // The `name == "getModeProperty" || startsWith("getModeProperty$")`
    // shape avoids the substring smell that would false-match
    // `getModePropertyOther`.
    safe<Unit> {
        val m = clazz.methods.firstOrNull {
            it.parameterCount == 0 &&
                (it.name == "getModeProperty" || it.name.startsWith("getModeProperty\$"))
        } ?: return@safe
        return@run {
            @Suppress("UNCHECKED_CAST")
            (m.invoke(this) as? org.gradle.api.provider.Property<KotlinJsBinaryMode>)
                ?.orNull
        }
    }
    return@run { null }
}

internal val KotlinJsIrLink.modeCompat: KotlinJsBinaryMode?
    get() = safe { KotlinJsIrLinkModeCompatCaller(this) }


// KGP-embedded ABI validation lookup (Kotlin 2.2+; @OptIn(ExperimentalAbiValidation::class)).
// Fully reflective: types and package paths drift across Kotlin
// versions, and consuming the typed `AbiValidationMultiplatformExtension`
// would create a hard compile-time bond to a still-experimental KGP
// surface. On any reflection failure we fail-closed (return Absent /
// false) so a broken shim never spuriously fires the embedded pipeline.
//
// Known names KGP has used (or may use) for the extension. Add new
// keys here when KGP renames — and remove the old one only after the
// renamed key has shipped in every supported `kotlinLatest`.
private val ABI_EXT_NAMES = arrayOf("abiValidation", "kotlinAbi")

private enum class AbiLookup { Absent, Detected, Enabled }

private fun Any.findAbiExt(): Any? {
    val ea = this as? ExtensionAware ?: return null
    return ABI_EXT_NAMES.firstNotNullOfOrNull { ea.extensions.findByName(it) }
}

private fun Any.readAbiEnabled(): Boolean? = safe {
    val m = javaClass.methods.firstOrNull {
        it.name == "getEnabled" && it.parameterCount == 0
    } ?: return@safe null
    @Suppress("UNCHECKED_CAST")
    (m.invoke(this) as? org.gradle.api.provider.Property<Boolean>)?.orNull
}

private fun Project.abiLookup(): AbiLookup {
    val kotlinExt: Any = extensions.findByName("kotlin") ?: return AbiLookup.Absent
    // Scopes to probe: the kotlin extension itself (top-level
    // `kotlin { abiValidation { } }`) plus every target (per-target
    // `kotlin { jvm { abiValidation { } } }` — KMP fanout shape).
    val scopes = mutableListOf<Any>(kotlinExt)
    safe<Unit> {
        val getTargets = kotlinExt.javaClass.methods.firstOrNull {
            it.name == "getTargets" && it.parameterCount == 0
        } ?: return@safe
        (getTargets.invoke(kotlinExt) as? Iterable<*>)?.forEach { tgt ->
            if (tgt != null) scopes.add(tgt)
        }
    }
    var detected = false
    for (scope in scopes) {
        val abi = scope.findAbiExt() ?: continue
        detected = true
        if (abi.readAbiEnabled() == true) return AbiLookup.Enabled
    }
    return if (detected) AbiLookup.Detected else AbiLookup.Absent
}

/**
 * True iff KGP's `abiValidation` extension is observable on the kotlin
 * extension or any of its targets. A presence check — does NOT imply
 * the user has opted into embedded validation. Use for diagnostic
 * flow only (e.g. logging "embedded extension present but `enabled`
 * cannot be read — shim might be broken"); use [kgpAbiValidationEnabledCompat]
 * for trigger decisions.
 *
 * @see org.jetbrains.kotlin.gradle.dsl.abi.AbiValidationMultiplatformExtension
 * @see org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
 */
internal val Project.kgpAbiValidationDetectedCompat: Boolean
    get() = abiLookup() != AbiLookup.Absent

/**
 * True iff KGP's embedded ABI validation is detected AND its
 * `enabled` flag reads true on at least one scope (top-level or any
 * target). Drives the embedded-mode trigger in [FluxoBcvTsPlugin].
 *
 * @see org.jetbrains.kotlin.gradle.dsl.abi.AbiValidationMultiplatformExtension
 * @see org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
 */
internal val Project.kgpAbiValidationEnabledCompat: Boolean
    get() = abiLookup() == AbiLookup.Enabled


internal fun setAccessible(field: AccessibleObject) {
    try {
        @Suppress("Since15")
        field.trySetAccessible()
    } catch (_: LinkageError) {
        // `trySetAccessible` (JDK 9+) absent on JDK 8: NoSuchMethodError.
        safe { field.isAccessible = true }
    }
}


// Reflective compat seams: swallow API-drift exceptions, propagate JVM
// errors. The narrow catch set is the contract; `Throwable` would absorb
// `OutOfMemoryError`, `StackOverflowError`, `VirtualMachineError`,
// `ThreadDeath`, `AssertionError` — none of which the compat layer can
// (or should) recover from.
//
// `inline` is preserved for the non-local-return flow in
// `KotlinTarget.jsCompilationsCompat`; introducing a `@PublishedApi`
// internal logger or helper would leak into the plugin's BCV baseline
// (BCV 0.14 `nonPublicMarkers` applies at class scope, not method scope).
internal inline fun <R> safe(body: () -> R): R? {
    return try {
        body()
    } catch (_: LinkageError) {
        null
    } catch (_: ReflectiveOperationException) {
        null
    } catch (_: RuntimeException) {
        null
    }
}
