# SE2Risiko Dokumentation

Diese Dokumentation beschreibt den aktuellen Stand des Repositorys. Source of Truth ist der Code im aktuellen Branch, ergänzt um Tests, Build-Konfiguration und die Architektur-Dokumente in `docs/architecture/`.

## Schnellstart

### Build und Tests

```bash
./gradlew :shared:test :server:test
./gradlew :app:testDebugUnitTest
./gradlew :e2e:test
```

Hinweis:
- Für lokale Volltests wird im Projekt aktuell häufig `-Pkotlin.incremental=false` verwendet, weil der Kotlin-Inkremental-Compile in diesem Repository zeitweise instabil ist.

### Lokaler Start

```bash
./gradlew :server:run
./gradlew :app:installDebug
./gradlew dokkaLocal
```

### Einstiegspunkte

- Server Runtime: `server/src/main/kotlin/at/aau/pulverfass/server/Application.kt`
- Android App: `app/src/main/kotlin/at/aau/pulverfass/app/MainActivity.kt`
- Gemeinsame Netzwerk- und Domain-Typen: `shared/src/main/kotlin/at/aau/pulverfass/shared/`

## Modulübersicht

| Modul | Zweck | Aktueller Stand |
|------|------|------|
| `:shared` | Domain-State, Lobby-Reducer, Map-Config, IDs, Netzwerkprotokoll, Codec | produktiv genutzt |
| `:server` | Ktor-WebSocket-Server, Lobby-Runtime, Routing, GameState-Delivery | produktiv genutzt |
| `:app` | Android-Client, technischer WebSocket-Stack, Lobby-UI | produktiv für Lobby-Flow, Gameplay noch unvollständig |
| `:e2e` | zukünftiger Ort für End-to-End-Tests | aktuell Platzhaltermodul |

## Architektur-Navigation

- `architecture/lobby-event-system.md`
  Aktuelles Reducer-/GameState-Modell, Event-Hierarchie und Zustandsregeln.
- `architecture/map-config.md`
  Map-Schema, Loader, Validierung, Hashing und Runtime-Integration.
- `architecture/turn-state-machine.md`
  Turn-/Phasenmodell, Broadcast-Reihenfolge und Pause/Resume-Regeln.
- `architecture/gamestate-versioning.md`
  `stateVersion`, Delta-Sync und Round-History-Policy.
- `architecture/game-state-visibility.md`
  Public/Private-Klassifikation von GameState-Payloads.
- `architecture/network-api-integration.md`
  Server- und Client-Integration des Netzwerkpfads.
- `architecture/client-systems.md`
  Entwickleranleitung für die vorhandenen Client-Systeme und deren Nutzung.

## Weitere Doku-Bereiche

- `network-messages/`
  Referenz und Vorlagen für `NetworkMessagePayload`-Klassen, Serializer und Registry-Einträge.
- `checklists/`
  Review- und Qualitätschecklisten.
- `ci/`
  CI-/Runner-spezifische Hinweise.

## Aktuell integrierte Systeme

### Voll integriert

- Shared Map-Definition mit Loader, Validierung und Hashing
- GameState mit Territory-State, TurnState, Query-APIs und `stateVersion`
- Serverseitiger Lobby-Reducer und Lobby-Runtime
- WebSocket-Transport und High-Level-Network-Pipeline auf Server und App
- Router/Server-Wiring für:
  - Lobby Create / Join / Leave / Kick
  - Startspieler-Setup
  - GameStart
  - TurnAdvance / TurnStateGet
  - MapGet
  - `GameStatePrivateGet`
  - `GameStateCatchUp`
  - öffentliche Deltas, Boundary-Marker und Snapshot-Broadcasts
- Public/Private Delivery-Framework
- Round-History-Buffer für die letzten zwei Runden

### Teilweise integriert

- Android-App verarbeitet aktuell produktiv den Lobby-Flow inklusive Create/Join/Leave und Spielerlisten-Events.
- Der technische Client-Stack kann beliebige Payloads senden und empfangen, aber es gibt noch keine produktive Client-State-Schicht für:
  - `MapGetResponse`
  - `TurnStateGetResponse`
  - `GameStateDeltaEvent`
  - `PhaseBoundaryEvent`
  - `GameStateSnapshotBroadcast`
  - `GameStateCatchUpResponse`
  - `GameStatePrivateGetResponse`
  - `GameStatePrivateGetResponse`

### Bewusst noch offen

- Gameplay-UI jenseits des Lobby-/Waiting-Room-Flows
- Kartenrendering im Client
- Client-seitige Anwendung von Delta-Sync und Snapshot-Recovery
- Persistente Replay-/History-Speicherung
- Echte End-to-End-Tests im Modul `:e2e`

## Dokumentationskonvention

- Diese Doku beschreibt nur implementierte oder durch Tests/Build belegte Funktionen.
- Offene Stellen werden explizit als Lücke, Platzhalter oder vorbereiteter Ausbaupfad markiert.
- Für API-Details ist KDoc/Dokka ergänzend zur Markdown-Doku gedacht, nicht als Ersatz.
