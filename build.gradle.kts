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

    // Konfiguration für JVM-Module (shared & server)
    tasks.withType<JacocoReport>().configureEach {
        dependsOn(tasks.withType<Test>()) // Tests müssen vor dem Report laufen

        reports {
            xml.required.set(true) // XML für Sonar
            html.required.set(true) // Optional für lokale Ansicht
        }
    }
}
