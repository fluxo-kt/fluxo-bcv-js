@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.kotlinx.binCompatValidator)
    alias(libs.plugins.deps.guard)
}

val pluginId = "io.github.fluxo-kt.binary-compatibility-validator-js"

group = "io.github.fluxo-kt"
version = libs.versions.fluxoBcvJs.get()

libs.versions.javaLangTarget.get().let { javaLangTarget ->
    logger.lifecycle("> Conf Java compatibility $javaLangTarget")
    java {
        JavaVersion.toVersion(javaLangTarget).let { v ->
            sourceCompatibility = v
            targetCompatibility = v
        }
    }

    val kotlinLangVersion = libs.versions.kotlinLangVersion.get()
    logger.lifecycle("> Conf Kotlin language and API $kotlinLangVersion")
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = javaLangTarget
            languageVersion = kotlinLangVersion
            apiVersion = kotlinLangVersion
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
    implementation(libs.plugin.binCompatValidator)
    implementation(libs.diffutils)
}

gradlePlugin {
    val shortDescr = "JS/TS API support for KotlinX Binary Compatibility Validator"
    plugins.create("fluxo-bcv-js") {
        id = pluginId
        implementationClass = "fluxo.bcvjs.FluxoBcvJsPlugin"
        displayName = shortDescr
        description = "Allows dumping TypeScript definitions of a JS part of a Kotlin multiplatform library" +
            " that's public in the sense of npm package visibility," +
            " and ensures that the public definitions haven’t been changed in a way" +
            " that makes this change binary incompatible."
    }

    val projectUrl = "https://github.com/fluxo-kt/fluxo-bcv-js"
    val scmUrl = "scm:git:git://github.com/fluxo-kt/fluxo-bcv-js.git"
    val publicationUrl = "$projectUrl/tree/main"
    pluginBundle {
        website = projectUrl
        vcsUrl = publicationUrl
        tags = listOf(
            "kotlin",
            "kotlin-js",
            "api-management",
            "binary-compatibility",
            "javascript",
            "typescript",
            "kotlin-multiplatform",
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
