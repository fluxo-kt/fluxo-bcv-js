@file:Suppress("KDocUnresolvedReference")

package fluxo.bcvts

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


/** True iff Kotlin can emit `.d.ts` for this target (so it gets TS API tasks). */
internal val KotlinTarget.isTsCompat: Boolean
    get() {
        if (!ALLOW_WASM_WASI && isWasmWasi) return false
        safe { if (name == "js" || name == "wasmJs") return true }
        safe { if (platformType == KotlinPlatformType.js) return true }
        safe { if (this is KotlinJsIrTarget) return true }
        safe { if (this is KotlinJsTargetDsl) return true }
        safe { if (platformType == KotlinPlatformType.wasm) return true }
        return false
    }

private val KotlinTarget.isWasmWasi: Boolean
    get() = name.contains("WASI", ignoreCase = true) ||
        safe { (this as? KotlinJsIrTarget)?.wasmTargetType == KotlinWasmTargetType.WASI } == true

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
                // Own `safe { }`: KGP's `generateTypeScriptDefinitions()` is not
                // idempotent — when the build script already called it, a second
                // call throws `DuplicateTaskException` (its validation task exists).
                // That failure means "already enabled", so it must never discard
                // the compilations (sharing one `safe { }` silently dropped every
                // JS target of such consumers).
                /** @see KotlinJsTargetDsl.generateTypeScriptDefinitions */
                safe { generateTypeScriptDefinitionsCaller() }
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


/**
 * `generateTs` is a plain Kotlin `var` (public getter, no `@JvmField`)
 * wherever it exists (`JsBinaries.kt` at KGP v1.8.22, v1.9.x); older KGP
 * has no such property, so `null` there means "not disabled". No
 * field-reflection fallback: it could never run, and `setAccessible`
 * would be the only JDK 9+ API call in a plugin that targets Java 8.
 *
 * @see JsIrBinary.generateTs
 */
internal val JsIrBinaryGenerateTsCaller: (JsIrBinary.() -> Boolean?) = run {
    safe<Unit> {
        // `getMethod` is inheritance-aware and binds to the no-arg overload.
        val method = JsIrBinary::class.java.getMethod("getGenerateTs")
        return@run { method.invoke(this) as? Boolean }
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

// KGP creates these tasks when (and only when) the user activates
// `abiValidation` in any of its API shapes — 2.2/2.3's
// `kotlin { abiValidation { enabled.set(true) } }`, 2.4+'s plain
// `kotlin { abiValidation { } }` or `abiValidation()`. Task-based
// detection is reliable across the matrix because it bypasses
// the unstable extension-shape API (2.4 removed `.enabled` while
// keeping the extension reachable, which would otherwise false-
// positive a presence-based check).
private const val CHECK_KOTLIN_ABI_TASK = "checkKotlinAbi"
private const val UPDATE_KOTLIN_ABI_TASK = "updateKotlinAbi"

/**
 * `null` = no `abiValidation` extension; `false` = extension present but not
 * enabled; `true` = enabled. A nullable Boolean rather than an enum: the plugin
 * runs on Gradle's embedded Kotlin stdlib (1.7.10 on Gradle 7.6), and enum
 * classes compiled with Kotlin 1.9+ call `kotlin.enums.EnumEntries`, which that
 * stdlib lacks (`NoClassDefFoundError` at plugin apply).
 */
private fun Project.abiLookup(): Boolean? {
    // Task-based primary signal. `tasks.names` is lazy (no task
    // realization), CC-safe, and present in every supported Gradle.
    val taskNames = tasks.names
    if (CHECK_KOTLIN_ABI_TASK in taskNames || UPDATE_KOTLIN_ABI_TASK in taskNames) {
        return true
    }
    // Extension-based fallback for older KGP that doesn't materialise
    // the tasks (or where naming has drifted further). Preserves the
    // 2.2/2.3 contract where `enabled: Property<Boolean>` is the opt-
    // in signal.
    val abiExts = extensions.findByName("kotlin")?.abiScopes()
        ?.mapNotNull { it.findAbiExt() }
        .orEmpty()
    return if (abiExts.isEmpty()) null else abiExts.any { it.readAbiEnabled() == true }
}

/**
 * Scopes to probe for an ABI extension: the kotlin extension itself (top-level
 * `kotlin { abiValidation { } }`) plus every target (per-target
 * `kotlin { jvm { abiValidation { } } }` — KMP fanout shape).
 */
private fun Any.abiScopes(): List<Any> {
    val kotlinExt = this
    val scopes = mutableListOf(kotlinExt)
    safe<Unit> {
        val getTargets = kotlinExt.javaClass.methods.firstOrNull {
            it.name == "getTargets" && it.parameterCount == 0
        } ?: return@safe
        (getTargets.invoke(kotlinExt) as? Iterable<*>)?.forEach { tgt ->
            if (tgt != null) scopes.add(tgt)
        }
    }
    return scopes
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
    get() = abiLookup() != null

/**
 * True iff KGP's embedded ABI validation is detected AND its
 * `enabled` flag reads true on at least one scope (top-level or any
 * target). Drives the embedded-mode trigger in [FluxoBcvTsPlugin].
 *
 * @see org.jetbrains.kotlin.gradle.dsl.abi.AbiValidationMultiplatformExtension
 * @see org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
 */
internal val Project.kgpAbiValidationEnabledCompat: Boolean
    get() = abiLookup() == true


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
