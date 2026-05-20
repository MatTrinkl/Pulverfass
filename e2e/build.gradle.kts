plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.example.e2e"
version = "1.0.0"

kotlin {
    jvmToolchain(25)
}

dependencies {
    testImplementation(project(":server"))
    testImplementation(project(":shared"))
    testImplementation(libs.hikari.cp)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
