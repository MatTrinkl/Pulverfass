# Server-Modul

Das Modul `:server` stellt den Ktor-WebSocket-Server, die Lobby-Runtime und die serverseitige GameState-Delivery bereit.

## Start

```bash
./gradlew :server:run
```

Der produktive Einstiegspunkt ist:
- `at.aau.pulverfass.server.ApplicationKt`

`main()` startet aktuell `createServerWithLobbyRuntime()`.

## Zentrale Einstiegspunkte

- `Application.module(network)`
  Low-Level-WebSocket-Server mit `ServerNetwork`
- `Application.moduleWithLobbyRuntime(network)`
  Produktionsverdrahtung mit Lobby-/Routing-/GameState-Logik
- `createServer(...)`
  erstellt einen startbaren Server ohne Lobby-Runtime
- `createServerWithLobbyRuntime(...)`
  erstellt einen startbaren Server mit aktiver Lobby-Runtime

## Transport und Netzwerk

### WebSocket

- Endpunkt: `/ws`
- nur Binary-Frames sind Teil des unterstützten Protokolls
- Text-Frames werden gemäß `WebSocketPolicy` aktiv mit `CANNOT_ACCEPT` geschlossen

### Wichtige Klassen

- `ServerWebSocketTransport`
- `ServerNetwork`
- `PacketReceiver`
- `PacketSender`

### Technischer Datenfluss

```text
WebSocket /ws
  -> ServerWebSocketTransport
  -> ServerNetwork
  -> PacketReceiver
  -> MainServerLobbyRoutingService
```

## Lobby-Runtime

### Wichtige Klassen

- `LobbyManager`
- `LobbyRuntime`
- `LobbyEventLoop`

### Eigenschaften

- eine Runtime pro Lobby
- FIFO-Verarbeitung pro Lobby
- parallele Verarbeitung mehrerer Lobbys
- `GameState` wird nur über den Reducer mutiert

## Routing und Domain-Anbindung

### Wichtige Klassen

- `DecodedNetworkRequest`
- `DefaultNetworkToLobbyEventMapper`
- `MainServerRouter`
- `MainServerLobbyRoutingService`

### Aufgaben des `MainServerLobbyRoutingService`

- dekodiert eingehende `ReceivedPacket`s
- baut `DecodedNetworkRequest`s mit `EventContext`
- behandelt bestimmte Requests direkt
- oder delegiert an `MainServerRouter`
- sendet Responses, Deltas, Boundary-Marker und Snapshots
- behandelt Pause/Resume bei Disconnect/Reconnect

## Map- und GameState-Integration

Beim Start von `moduleWithLobbyRuntime(...)`:

- lädt `ClasspathMapDefinitionRepository` die Default-Map aus dem Klassenpfad
- erstellt der `LobbyManager` neue Lobbys mit `GameState.initial(lobbyCode, mapDefinition)`

Damit enthalten neue Lobbys ab Start:
- geladene `MapDefinition`
- initialisierte `territoryStates`
- vorbereiteten `turnState` für das Setup

## Aktuell integrierte Requests

Direkt oder indirekt verdrahtet sind aktuell unter anderem:

- `CreateLobbyRequest`
- `JoinLobbyRequest`
- `LeaveLobbyRequest`
- `KickPlayerRequest`
- `StartGameRequest`
- `StartPlayerSetRequest`
- `MapGetRequest`
- `TurnAdvanceRequest`
- `TurnStateGetRequest`
- `GameStatePrivateGetRequest`
- `GameStateCatchUpRequest`

## Aktuell integrierte GameState-Übertragung

### Öffentlich

- `GameStateDeltaEvent`
- `PhaseBoundaryEvent`
- `GameStateSnapshotBroadcast`
- öffentliche Einzel-Events wie `TurnStateUpdatedEvent`, `GameStartedEvent`, `TerritoryOwnerChangedEvent`, `TerritoryTroopsChangedEvent`

### Privat

- `GameStatePrivateGetResponse`

### Catch-up

- `GameStateCatchUpResponse` als vollständiger öffentlicher Snapshot

### Observability

- Logging an den relevanten Sendepfaden für Delta, Boundary, Snapshot und Private Snapshot
- `RoundHistoryBuffer` für die letzten zwei Runden pro Lobby

## Aktuelle Grenzen

- Spieleridentität ist aktuell an die WebSocket-Verbindung gekoppelt; es gibt noch kein persistentes Session-/Auth-System.
- Der Server hält eine Default-Map im Speicher; Multi-Map-Management ist noch nicht implementiert.
- Der `RoundHistoryBuffer` dient Diagnosezwecken und ist noch kein öffentliches Replay-API.
- Persistente Speicherung von Lobbys oder Event-Logs ist derzeit nicht vorhanden.
