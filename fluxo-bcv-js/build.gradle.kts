import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaCompilation

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.kotlinx.binCompatValidator)
    alias(libs.plugins.deps.guard)
}

val pluginId = "io.github.fluxo-kt.binary-compatibility-validator-js"
val experimentalTest = true

group = "io.github.fluxo-kt"
version = libs.versions.fluxoBcvJs.get()

kotlin {
    target.compilations {
        val kotlinVersion = libs.versions.kotlinLangVersion.get()
        val kotlinApiVersion = libs.versions.kotlinLangVersion.get()
        val main by getting {
            libs.versions.javaLangTarget.get().let { jvmVersion ->
                kotlinOptions {
                    jvmTarget = jvmVersion
                    languageVersion = kotlinVersion
                    // Note: apiVersion can't be greater than languageVersion!
                    apiVersion = kotlinVersion

                    freeCompilerArgs += "-Xcontext-receivers"
                    freeCompilerArgs += "-Xklib-enable-signature-clash-checks"
                    freeCompilerArgs += "-Xjsr305=strict"
                    freeCompilerArgs += "-Xjvm-default=all"
                    freeCompilerArgs += "-Xtype-enhancement-improvements-strict-mode"
                    freeCompilerArgs += "-Xvalidate-bytecode"
                    freeCompilerArgs += "-Xvalidate-ir"
                    // freeCompilerArgs += "-Xskip-metadata-version-check"
                    freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
                }
                compileJavaTaskProvider.configure {
                    sourceCompatibility = jvmVersion
                    targetCompatibility = jvmVersion
                }
                var kv = kotlinVersion
                if (kotlinApiVersion != kotlinVersion) {
                    kv += " (API $kotlinApiVersion)"
                }
                logger.lifecycle("> Conf compatibility for Kotlin $kv, JVM $jvmVersion")
            }
        }

        val configureLatest: (KotlinWithJavaCompilation<KotlinJvmOptions, *>).() -> Unit = {
            kotlinOptions {
                jvmTarget = "17"
                languageVersion = "2.1"
                apiVersion = "2.1"
            }
            compileJavaTaskProvider.configure {
                sourceCompatibility = "17"
                targetCompatibility = "17"
            }
        }
        getByName("test", configureLatest)

        // Experimental test compilation with the latest Kotlin settings.
        // Don't try for sources with old compatibility settings.
        val isInCompositeBuild = gradle.includedBuilds.size > 1
        if (!isInCompositeBuild && experimentalTest && kotlinVersion.toFloat() >= 1.4f) {
            create("experimentalTest") {
                configureLatest()

                defaultSourceSet.dependsOn(main.defaultSourceSet)

                tasks.named("check") {
                    dependsOn(compileTaskProvider)
                }
            }
        }
    }
}

configurations.implementation {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-metadata-jvm")
    exclude(group = "org.ow2.asm")
}

dependencies {
    compileOnly(libs.plugin.kotlin)
    compileOnly(libs.plugin.binCompatValidator)
    implementation(libs.diffutils)
}

gradlePlugin {
    val projectUrl = "https://github.com/fluxo-kt/fluxo-bcv-js"
    val scmUrl = "scm:git:git://github.com/fluxo-kt/fluxo-bcv-js.git"
    val publicationUrl = "$projectUrl/tree/main"
    website.set(projectUrl)
    vcsUrl.set(publicationUrl)

    val shortDescr = "JS/TS API support for KotlinX Binary Compatibility Validator"
    plugins.create("fluxo-bcv-js") {
        id = pluginId
        implementationClass = "fluxo.bcvjs.FluxoBcvJsPlugin"
        displayName = shortDescr
        description = "Allows dumping TypeScript definitions of a JS part of a Kotlin multiplatform library" +
            " that's public in the sense of npm package visibility," +
            " and ensures that the public definitions haven’t been changed in a way" +
            " that makes this change binary incompatible."

        tags.set(
            listOf(
                "kotlin",
                "kotlin-js",
                "api-management",
                "binary-compatibility",
                "javascript",
                "typescript",
                "kotlin-multiplatform"
            )
        )
    }

    tasks.create("sourceJarTask", org.gradle.jvm.tasks.Jar::class.java) {
        from(pluginSourceSet.java.srcDirs)
        archiveClassifier.set("sources")
    }

    publishing {
        repositories {
            maven {
                name = "localDev"
                url = uri("../_/local-repo")
            }
        }

        publications.withType<MavenPublication>().configureEach {
            pom {
                name.set("Fluxo BCV JS")
                description.set(shortDescr)
                url.set(publicationUrl)

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("amal")
                        name.set("Artyom Shendrik")
                        email.set("artyom.shendrik@gmail.com")
                    }
                }

                scm {
                    url.set(projectUrl)
                    connection.set(scmUrl)
                    developerConnection.set(scmUrl)
                }
            }
        }

        val signingKey = providers.environmentVariable("SIGNING_KEY").orNull?.replace("\\n", "\n")
        if (!signingKey.isNullOrEmpty()) {
            logger.lifecycle("> Conf SIGNING_KEY SET, applying signing configuration")
            project.plugins.apply("signing")
            extensions.configure<SigningExtension> {
                val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(publications)
            }
        } else {
            logger.warn("> Conf SIGNING_KEY IS NOT SET! Publications are unsigned")
        }
    }
}

dependencyGuard {
    configuration("compileClasspath")
    configuration("runtimeClasspath")
}
