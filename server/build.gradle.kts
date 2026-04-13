plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    application
}

group = "at.aau.pulverfass.server"
version = "1.0.0"

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("at.aau.pulverfass.server.ApplicationKt")
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.junit)
}

tasks.named<Test>("test") {
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))

    executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))
    sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java"))
    classDirectories.setFrom(
        files(
            layout.buildDirectory.dir("classes/kotlin/main"),
            layout.buildDirectory.dir("classes/java/main"),
        ),
    )
}
