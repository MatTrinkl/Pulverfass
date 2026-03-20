plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.example.shared"
version = "1.0.0"

kotlin {
    jvmToolchain(25)
}

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
