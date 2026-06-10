import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
}

group = "com.example.shared"
version = "1.0.0"

// Use JDK 25 as compiler runtime but target JVM 17 bytecode.
// The shared module is consumed by Android (via D8/R8), which does not support
// class file versions above JVM 21.
kotlin {
    jvmToolchain(25)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.atomicfu)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(project.dependencies.platform("org.junit:junit-bom:5.10.2"))
            implementation("org.junit.jupiter:junit-jupiter")
            runtimeOnly("org.junit.platform:junit-platform-launcher")
        }
    }

    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

/*
 * Kompatibilitäts-Task: CI und Doku rufen weiterhin `:shared:test` auf.
 * Im KMP-Modell heißt der JVM-Test-Task `jvmTest`.
 */
tasks.register("test") {
    group = "verification"
    description = "Alias for jvmTest (KMP compatibility)."
    dependsOn("jvmTest")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("jvmTest"))

    executionData.setFrom(layout.buildDirectory.file("jacoco/jvmTest.exec"))
    sourceDirectories.setFrom(files("src/commonMain/kotlin", "src/jvmMain/kotlin"))
    classDirectories.setFrom(
        files(layout.buildDirectory.dir("classes/kotlin/jvm/main")),
    )

    reports {
        // Pfad bleibt stabil, weil CI/Sonar build/reports/jacoco/test/jacocoTestReport.xml erwartet.
        xml.required.set(true)
        xml.outputLocation.set(
            layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"),
        )
        html.required.set(true)
        csv.required.set(false)
    }
}
