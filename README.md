# SE2Risiko

A clean Kotlin monorepo containing three independent but interconnected modules:

- **android-app** – Kotlin Android app with Jetpack Compose
- **server** – Kotlin/Ktor backend server
- **shared** – Pure Kotlin module for DTOs, constants and utilities shared across modules

---

## Repository Structure

```
SE2Risiko/
├── README.md
├── .gitignore
├── settings.gradle.kts          ← root multi-project Gradle build
├── build.gradle.kts             ← root plugin declarations (apply false)
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml       ← version catalog for all dependencies
│   └── wrapper/
│       └── gradle-wrapper.properties
│
├── android-app/
│   ├── settings.gradle.kts      ← standalone settings (optional)
│   ├── build.gradle.kts         ← container project
│   ├── gradle.properties
│   └── app/
│       ├── build.gradle.kts
│       └── src/
│           ├── main/
│           │   ├── AndroidManifest.xml
│           │   ├── kotlin/com/example/androidapp/
│           │   │   ├── MainActivity.kt
│           │   │   └── ui/theme/Theme.kt
│           │   └── res/values/
│           ├── test/            ← JVM unit tests
│           └── androidTest/     ← instrumented tests
│
├── server/
│   ├── settings.gradle.kts      ← standalone settings (optional)
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── kotlin/com/example/server/Application.kt
│       │   └── resources/logback.xml
│       └── test/
│           └── kotlin/com/example/server/ServerTest.kt
│
└── shared/
    ├── settings.gradle.kts      ← standalone settings (optional)
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/com/example/shared/
        │   ├── HealthResponse.kt
        │   ├── ApiRoutes.kt
        │   └── Constants.kt
        └── test/kotlin/com/example/shared/SharedTest.kt
```

---

## The `shared` Module

The `shared` module is a **pure Kotlin library** (no Android SDK, no Ktor) containing:

| File | Purpose |
|---|---|
| `HealthResponse.kt` | Serializable data class used by the `/health` endpoint |
| `ApiRoutes.kt` | Centralised API route constants |
| `Constants.kt` | App-wide constants (version, default port, …) |

Both `android-app` and `server` depend on `shared` via:
```kotlin
implementation(project(":shared"))
```

---

## Starting the Server

### Prerequisites
- JDK 17+
- Gradle (or use the wrapper: `./gradlew`)

### Run

```bash
# From the repository root
./gradlew :server:run
```

The server starts on **http://localhost:8080**.

### Available Endpoints

| Method | Route | Response |
|--------|-------|----------|
| GET | `/health` | `{"status":"UP","message":"OK"}` |

### Run server tests

```bash
./gradlew :server:test
```

---

## Starting the Android App

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK with API level 35 installed

### Open in Android Studio

1. Open Android Studio
2. Select **File → Open** and choose the repository root
3. Wait for Gradle sync to complete
4. Select the `app` run configuration and press **Run ▶**

### Run Android unit tests (JVM)

```bash
./gradlew :android-app:app:test
```

### Run Android instrumented tests (requires a device/emulator)

```bash
./gradlew :android-app:app:connectedAndroidTest
```

---

## Running All Tests

```bash
./gradlew test
```

---

## Building All Modules

```bash
./gradlew build
```

---

## Package Names

| Module | Package |
|--------|---------|
| android-app | `com.example.androidapp` |
| server | `com.example.server` |
| shared | `com.example.shared` |
