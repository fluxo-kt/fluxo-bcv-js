//import org.jetbrains.kotlin.gradle.dsl.JvmTarget
//import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    signing
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.kotlinx.binCompatValidator)
    alias(libs.plugins.deps.guard)
}

libs.versions.javaLangTarget.get().let { javaLangTarget ->
    logger.lifecycle("> Conf Java compatibility $javaLangTarget")
    java {
        JavaVersion.toVersion(javaLangTarget).let { v ->
            sourceCompatibility = v
            targetCompatibility = v
        }
    }
    val compileKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
    compileKotlin.kotlinOptions {
        jvmTarget = javaLangTarget

        val kotlinLangVersion = libs.versions.kotlinLangVersion.get()
        logger.lifecycle("> Conf Kotlin language and API $kotlinLangVersion")
        languageVersion = kotlinLangVersion
        apiVersion = kotlinLangVersion
    }
}

configurations.implementation {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-metadata-jvm")
    exclude(group = "com.googlecode.java-diff-utils")
    exclude(group = "org.ow2.asm")
}

dependencies {
    compileOnly(libs.plugin.kotlin)
    implementation(libs.plugin.binCompatValidator)
}

gradlePlugin {
    plugins.create(project.name) {
        id = "fluxo.kotlinx.binary-compatibility-validator.js"
        implementationClass = "fluxo.bcvjs.FluxoBcvJsPlugin"
        displayName = "JS/TS API support for KotlinX Binary Compatibility Validator"
        description = "Allows dumping TypeScript definitions of a JS part of a Kotlin multiplatform library" +
            " that's public in the sense of npm package visibility," +
            " and ensures that the public definitions haven’t been changed in a way" +
            " that makes this change binary incompatible."
    }
}

dependencyGuard {
    configuration("compileClasspath")
    configuration("runtimeClasspath")
}
