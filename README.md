# SE2Risiko

Technischer Einstiegspunkt für das Repository. Source of truth ist der Code im aktuellen Branch; ergänzende Produkt- und Architektur-Dokumentation liegt unter [`docs/`](docs/README.md).

## Modulüberblick

| Modul | Zweck | Stand |
| --- | --- | --- |
| `:shared` | gemeinsame Domain-, Map-, State-, Event- und Netzwerktypen (Kotlin Multiplatform: JVM + iOS) | produktiv genutzt |
| `:server` | Ktor-WebSocket-Server, Lobby-Runtime, Routing und GameState-Delivery | produktiv genutzt |
| `:app` | Android-Client mit technischem WebSocket-Stack und Lobby-Flow | teilweise produktiv |
| `:client` | Compose-Multiplatform-Client (Android + iOS) mit aus `app` portierter UI | im Aufbau |
| `iosApp/` | Xcode-Wrapper für den iOS-Build des client-Moduls ([Anleitung](iosApp/README.md)) | im Aufbau |
| `:e2e` | vorbereiteter Ort für spätere End-to-End-Tests | Platzhalter |

## Schnellstart

### Tests

```bash
./gradlew :shared:test :server:test
./gradlew :app:testDebugUnitTest
./gradlew :client:testDebugUnitTest
```

### Lokaler Start

```bash
./gradlew :server:run
./gradlew :app:installDebug
./gradlew :client:installDebug   # Multiplatform-Client am Android-Emulator
./gradlew dokkaLocal
```

iOS-Build (nur am Mac): siehe [iosApp/README.md](iosApp/README.md).

## Wichtige Einstiegspunkte

- Server: [server/src/main/kotlin/at/aau/pulverfass/server/Application.kt](server/src/main/kotlin/at/aau/pulverfass/server/Application.kt)
- Android-App: [app/src/main/kotlin/at/aau/pulverfass/app/MainActivity.kt](app/src/main/kotlin/at/aau/pulverfass/app/MainActivity.kt)
- Multiplatform-Client: [client/src/commonMain/kotlin/at/aau/pulverfass/client/App.kt](client/src/commonMain/kotlin/at/aau/pulverfass/client/App.kt)
- Shared Domain und Netzwerk: [shared/src/commonMain/kotlin/at/aau/pulverfass/shared](shared/src/commonMain/kotlin/at/aau/pulverfass/shared)
- Zentrale Projektdokumentation: [docs/README.md](docs/README.md)

## Dokumentation

- Projekt- und Modulübersicht: [docs/README.md](docs/README.md)
- Architektur-Dokumente: [docs/architecture/README.md](docs/architecture/README.md)
- Netzwerk-Nachrichtenkonventionen: [docs/network-messages/README.md](docs/network-messages/README.md)
- Server-spezifische Hinweise: [server/README.md](server/README.md)
- iOS-Build und Verifikation: [iosApp/README.md](iosApp/README.md)
- Team-Mac-Setup (Xcode bis iPhone-Build): [docs/ios/mac-setup.md](docs/ios/mac-setup.md)

## Aktueller Implementierungsstand

- Serverseitig sind Lobby-, Map-, Turn- und GameState-Sync-Systeme bereits integriert.
- Die Android-App deckt aktuell produktiv den Lobby-Flow ab.
- Gameplay-spezifische Client-State-Verarbeitung und eine echte Spielansicht sind noch nicht vollständig implementiert.
- Der `:client` (Compose Multiplatform) spiegelt den Stand der Android-App für Android **und** iOS; die iOS-Verifikation auf echter Hardware steht noch aus (Checkliste in [iosApp/README.md](iosApp/README.md)).

Diese Grenzen sind absichtlich auch in der Doku markiert und nicht als bereits fertige Features beschrieben.
