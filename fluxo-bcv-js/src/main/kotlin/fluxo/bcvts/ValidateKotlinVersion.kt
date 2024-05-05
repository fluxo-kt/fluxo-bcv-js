package fluxo.bcvts

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

private const val KOTLIN_MIN_VERSION = "1.6.20"

internal fun Project.validateKotlinVersion(): Boolean {
    val kotlinVersionStr = safe { getKotlinPluginVersion() }
        ?: safe { KotlinVersion.CURRENT.toString() }
    val kotlinVersionIsOk = kotlinVersionStr?.let { v ->
        safe { isVersionGreaterThanOrEqual(v, KOTLIN_MIN_VERSION) }
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

@Suppress("SameParameterValue", "ReturnCount")
private fun isVersionGreaterThanOrEqual(version: String, targetVersion: String): Boolean {
    val parts = version.split('.')
    val targetParts = targetVersion.split('.')
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
