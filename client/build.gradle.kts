import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ClientApp"
            isStatic = true
        }
        // Obj-C-Interop (AVFoundation, UIKit) ist in Kotlin/Native als
        // experimentell markiert; der Opt-in gilt für alle iOS-Kompilierungen.
        iosTarget.compilerOptions {
            optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.ktor.client.cio)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("androidUnitTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlin.test)
            implementation(libs.androidx.test.core)
            implementation(libs.robolectric)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.ui.test.junit4)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.websockets)
        }
    }
}

android {
    namespace = "at.aau.pulverfass.client"
    compileSdk = 35

    defaultConfig {
        applicationId = "at.aau.pulverfass.client"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency")
    }
}

dependencies {
    // Stellt androidx.activity.ComponentActivity im Debug-Manifest bereit,
    // damit Robolectric-Compose-Tests (createComposeRule) eine Activity finden.
    debugImplementation(libs.androidx.ui.test.manifest)
}

compose.resources {
    packageOfResClass = "at.aau.pulverfass.client.resources"
}

// Generierte Compose-Resources-Klassen (Res.kt etc.) sind kein handgeschriebener
// Code und werden von ktlint ausgenommen.
configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    filter {
        exclude { element -> element.file.path.replace('\\', '/').contains("/generated/") }
    }
}
