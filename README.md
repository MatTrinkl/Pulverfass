# SE2Risiko

Technischer Einstiegspunkt für das Repository. Source of truth ist der Code im aktuellen Branch; ergänzende Produkt- und Architektur-Dokumentation liegt unter [`docs/`](docs/README.md).

## Modulüberblick

| Modul | Zweck | Stand |
| --- | --- | --- |
| `:shared` | gemeinsame Domain-, Map-, State-, Event- und Netzwerktypen | produktiv genutzt |
| `:server` | Ktor-WebSocket-Server, Lobby-Runtime, Routing und GameState-Delivery | produktiv genutzt |
| `:app` | Android-Client mit technischem WebSocket-Stack und Lobby-Flow | teilweise produktiv |
| `:e2e` | vorbereiteter Ort für spätere End-to-End-Tests | Platzhalter |

## Schnellstart

### Tests

```bash
./gradlew :shared:test :server:test
./gradlew :app:testDebugUnitTest
```

### Lokaler Start

```bash
./gradlew :server:run
./gradlew :app:installDebug
./gradlew dokkaLocal
```

## Wichtige Einstiegspunkte

- Server: [server/src/main/kotlin/at/aau/pulverfass/server/Application.kt](server/src/main/kotlin/at/aau/pulverfass/server/Application.kt)
- Android-App: [app/src/main/kotlin/at/aau/pulverfass/app/MainActivity.kt](app/src/main/kotlin/at/aau/pulverfass/app/MainActivity.kt)
- Shared Domain und Netzwerk: [shared/src/main/kotlin/at/aau/pulverfass/shared](shared/src/main/kotlin/at/aau/pulverfass/shared)
- Zentrale Projektdokumentation: [docs/README.md](docs/README.md)

## Dokumentation

- Projekt- und Modulübersicht: [docs/README.md](docs/README.md)
- Architektur-Dokumente: [docs/architecture/README.md](docs/architecture/README.md)
- Netzwerk-Nachrichtenkonventionen: [docs/network-messages/README.md](docs/network-messages/README.md)
- Server-spezifische Hinweise: [server/README.md](server/README.md)

## Aktueller Implementierungsstand

- Serverseitig sind Lobby-, Map-, Turn- und GameState-Sync-Systeme bereits integriert.
- Die Android-App deckt aktuell produktiv den Lobby-Flow ab.
- Gameplay-spezifische Client-State-Verarbeitung und eine echte Spielansicht sind noch nicht vollständig implementiert.

Diese Grenzen sind absichtlich auch in der Doku markiert und nicht als bereits fertige Features beschrieben.
