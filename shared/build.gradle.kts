plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.example.shared"
version = "1.0.0"

// Use JDK 25 as compiler runtime but target JVM 17 bytecode.
// The shared module is consumed by Android (via D8/R8), which does not support
// class file versions above JVM 21. Both Kotlin and Java must target the same
// version to avoid the "Inconsistent JVM Target Compatibility" error.
kotlin {
    jvmToolchain(25)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
