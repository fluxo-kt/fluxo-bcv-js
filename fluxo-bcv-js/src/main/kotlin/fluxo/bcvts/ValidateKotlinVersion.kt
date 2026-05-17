package fluxo.bcvts

import org.gradle.api.Project
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

// `KOTLIN_MIN_VERSION` is generated from `libs.versions.toml::kotlinMin`
// by the `genKotlinMinVersion` task (see fluxo-bcv-js/build.gradle.kts).
// Do NOT redeclare here; that would shadow the generated source-of-truth.

internal fun Project.validateKotlinVersion(): Boolean {
    val kotlinVersionStr = safe { getKotlinPluginVersion() }
        ?: safe { KotlinVersion.CURRENT.toString() }
    val kotlinVersionIsOk = kotlinVersionStr?.let { v ->
        safe { isKotlinVersionSupported(v) }
    } ?: false
    if (kotlinVersionIsOk) {
        return true
    }

    val versionText = when {
        kotlinVersionStr.isNullOrBlank() -> ""
        else -> ", your version is '$kotlinVersionStr'"
    }

    @Suppress("MaxLineLength")
    val message = "You need at least Kotlin $KOTLIN_MIN_VERSION " +
        "to enable $KTS_API verification$versionText. \n" +
        "Please, update your Kotlin! More details at " +
        "https://kotlinlang.org/docs/whatsnew1620.html#improvements-to-export-and-typescript-declaration-generation"
    logger.error(message)
    return false
}

// Compare *base* versions (qualifier-stripped) so a user on a pre-release
// of the floor — e.g. `1.7.22-Beta1`, `2.0.0-RC2`, `2.3.21-SNAPSHOT`,
// `2.4.0-RC`, `1.7.22-IJ123-456` — is correctly recognised as feature-
// equivalent to the stable. Plain `GradleVersion.compareTo` orders
// `X-Beta1 < X`, which would reject pre-release adopters even though
// the TS-generation feature surface is identical.
//
// Strip the qualifier BEFORE calling `GradleVersion.version(...)`:
// Gradle 9.x's parser is stricter than 8.x's and *rejects* qualifier
// shapes it doesn't recognise (e.g. `2.4.0-RC` — uppercase without a
// numeric suffix throws `IllegalArgumentException`). We never reach
// `.baseVersion`'s qualifier-stripping because construction throws
// first. Pre-stripping mirrors fluxo-kmp-conf's
// `RegisterShrinkerTask` (`version.substringBefore('-')`).
private fun isKotlinVersionSupported(version: String): Boolean =
    GradleVersion.version(version.substringBefore('-')) >=
        GradleVersion.version(KOTLIN_MIN_VERSION.substringBefore('-'))
