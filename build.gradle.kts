import org.jetbrains.dokka.gradle.DokkaExtension

// Root build file; most configuration lives in the individual subprojects.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.dokka)
}

val dokkaSiteOutputDir = layout.buildDirectory.dir("documentation/html")
val generatedDokkaIncludesDir = layout.buildDirectory.dir("generated/dokka/includes")
val gitBranch = "main"
val gitRemoteBaseUrl = "https://github.com/MatTrinkl/SE2Risiko/tree/$gitBranch"

val generateDokkaProductDocs by tasks.registering {
    group = "documentation"
    description = "Generates a Dokka-compatible product documentation page from the docs folder."

    val docsDir = layout.projectDirectory.dir("docs")
    val outputFile = generatedDokkaIncludesDir.map { it.file("product-docs.md") }

    inputs.dir(docsDir)
    outputs.file(outputFile)

    doLast {
        val markdownFiles =
            docsDir.asFileTree
                .matching { include("**/*.md") }
                .files
                .sortedBy { it.relativeTo(rootDir).invariantSeparatorsPath }

        val targetFile = outputFile.get().asFile
        targetFile.parentFile.mkdirs()

        val content =
            buildString {
                appendLine("# Module SE2Risiko")
                appendLine()
                appendLine("## Übersicht")
                appendLine()
                appendLine("Diese Seite ist der lokale Einstiegspunkt für technische API-Doku und Produktdoku.")
                appendLine()
                appendLine("### Schnellzugriff")
                appendLine()
                appendLine("<ul>")
                appendLine("""<li><a href="#product-docs">Produktdokumentation</a></li>""")
                appendLine("""<li><a href="app/index.html">App API</a></li>""")
                appendLine("""<li><a href="server/index.html">Server API</a></li>""")
                appendLine("""<li><a href="shared/index.html">Shared API</a></li>""")
                appendLine("</ul>")
                appendLine()
                appendLine("### Module")
                appendLine()
                appendLine("<ul>")
                appendLine("""<li><a href="app/index.html"><strong>app</strong></a>: Android-App und App-seitige Integration</li>""")
                appendLine("""<li><a href="server/index.html"><strong>server</strong></a>: Ktor-Server, Transport und Server-Networklayer</li>""")
                appendLine("""<li><a href="shared/index.html"><strong>shared</strong></a>: Gemeinsame Netzwerk- und Protokolltypen</li>""")
                appendLine("</ul>")
                appendLine()
                appendLine("""<a id="product-docs"></a>""")
                appendLine("## Produktdokumentation")
                appendLine()
                appendLine("Die folgenden Markdown-Dateien aus dem `docs`-Ordner werden zusätzlich in diese Seite integriert.")
                appendLine()

                if (markdownFiles.isEmpty()) {
                    appendLine("Aktuell wurden keine Markdown-Dateien im `docs`-Ordner gefunden.")
                } else {
                    appendLine("### Themen")
                    appendLine()
                    appendLine("<ul>")

                    markdownFiles.forEach { file ->
                        val relativePath = file.relativeTo(rootDir).invariantSeparatorsPath
                        val anchorId =
                            relativePath
                                .lowercase()
                                .replace("/", "-")
                                .replace(".", "-")

                        appendLine("""<li><a href="#$anchorId"><code>$relativePath</code></a></li>""")
                    }

                    appendLine("</ul>")
                    appendLine()

                    markdownFiles.forEach { file ->
                        val relativePath = file.relativeTo(rootDir).invariantSeparatorsPath
                        val anchorId =
                            relativePath
                                .lowercase()
                                .replace("/", "-")
                                .replace(".", "-")

                        appendLine("""<a id="$anchorId"></a>""")
                        appendLine("### `$relativePath`")
                        appendLine()
                        appendLine(file.readText(Charsets.UTF_8).trim())
                        appendLine()
                    }
                }
            }

        targetFile.writeText(content, Charsets.UTF_8)
    }
}

dokka {
    moduleName.set(rootProject.name)

    dokkaPublications.html {
        outputDirectory.set(dokkaSiteOutputDir)
        includes.from(generateDokkaProductDocs.map { it.outputs.files.singleFile })
    }

    pluginsConfiguration.html {
        footerMessage.set("Local SE2Risiko technical and product documentation")
    }
}

dependencies {
    dokka(project(":app"))
    dokka(project(":server"))
    dokka(project(":shared"))
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "jacoco")

    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.13"
    }

    tasks.withType<Test>().configureEach {
        if (!project.plugins.hasPlugin("com.android.application") &&
            !project.plugins.hasPlugin("com.android.library")
        ) {
            useJUnitPlatform()
        }
    }

    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    pluginManager.withPlugin("org.jetbrains.dokka") {
        extensions.configure<DokkaExtension>("dokka") {
            moduleName.set(path.removePrefix(":"))

            dokkaPublications.html {
                pluginsConfiguration.html {
                    footerMessage.set("Local SE2Risiko technical documentation")
                }
            }

            dokkaSourceSets.configureEach {
                val kotlinSourceDir = project.file("src/main/kotlin")

                if (kotlinSourceDir.exists()) {
                    sourceLink {
                        localDirectory.set(kotlinSourceDir)
                        remoteUrl.set(uri("$gitRemoteBaseUrl/${project.path.removePrefix(":")}/src/main/kotlin"))
                        remoteLineSuffix.set("#L")
                    }
                }
            }
        }
    }
}

tasks.named("dokkaGenerate") {
    dependsOn(generateDokkaProductDocs)
}

tasks.register("dokkaLocal") {
    group = "documentation"
    description = "Generates the local Dokka site with API and product documentation."
    dependsOn("dokkaGenerate")

    doLast {
        println("Open ${dokkaSiteOutputDir.get().asFile.resolve("index.html")}")
    }
}
