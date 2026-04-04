plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.example.e2e"
version = "1.0.0"

kotlin {
    jvmToolchain(25)
}

tasks.register("placeholderE2eInfo") {
    group = "verification"
    description = "Prints information about the current placeholder state of the E2E module."
    doLast {
        println("E2E placeholder module: Real end-to-end tests will be added once dependencies are ready.")
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
