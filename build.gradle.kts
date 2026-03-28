// Root build file — only declares plugins used across sub-projects.
// Actual configuration lives in each sub-project's own build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
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
}
