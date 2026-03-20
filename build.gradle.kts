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
    id("org.sonarqube") version "4.4.1.3373"
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
