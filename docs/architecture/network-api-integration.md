# Network API Integration

## Ziel

Dieses Dokument beschreibt, wie der technische Netzwerkpfad und die fachlichen Routing-/Client-Schichten aktuell im Projekt zusammenspielen.

Source of Truth:
- `:shared` enthält Protokolltypen, `MessageCodec`, `MessageType` und `NetworkPayloadRegistry`
- `:server` enthält WebSocket-Server, Routing und Delivery
- `:app` enthält den technischen Android-Client-Stack und den aktuellen Lobby-Controller

## Module und Verantwortlichkeiten

### `:shared`

Wichtige Klassen:
- `shared.network.codec.MessageCodec`
- `shared.message.codec.NetworkPayloadRegistry`
- `shared.message.protocol.MessageType`
- `shared.message.protocol.NetworkMessagePayload`
- `shared.network.receive.PacketReceiveAdapter`
- `shared.network.send.PacketSendAdapter`

Verantwortung:
- Serialisierung und Deserialisierung aller Payloads
- Header-/Packet-Framing
- gemeinsame IDs, Domain-Events und Responses/Requests

### `:server`

Wichtige Klassen:
- `server.Application`
- `server.ServerNetwork`
- `server.transport.ServerWebSocketTransport`
- `server.routing.MainServerLobbyRoutingService`
- `server.routing.MainServerRouter`
- `server.routing.GameStateDeliveryDispatcher`

Verantwortung:
- Ktor-WebSocket-Endpunkt `/ws`
- technische Entkopplung zwischen Transport und Routing
- Zuordnung von Requests zu Lobby-Domainlogik
- Versand öffentlicher und privater GameState-Payloads

### `:app`

Wichtige Klassen:
- `app.network.transport.AndroidWebSocketTransport`
- `app.network.ClientNetwork`
- `app.lobby.LobbyController`

Verantwortung:
- technischer WebSocket-Client
- Kodierung ausgehender Payloads
- Dekodierung eingehender Pakete
- aktueller fachlicher Lobby-Flow der Android-App

## Serverseitige Integration

### Entry Points

- Low-Level-Server ohne Lobby-Runtime:
  - `Application.module(network)`
- Produktionsverdrahtung mit Lobby-/GameState-Routing:
  - `Application.moduleWithLobbyRuntime(network)`
  - `createServerWithLobbyRuntime(...)`

`main()` startet aktuell den Server mit aktiver Lobby-Runtime.

### Verbindungsfluss

```text
WebSocket /ws
  -> ServerWebSocketTransport
  -> ServerNetwork
  -> PacketReceiver
  -> MainServerLobbyRoutingService
  -> MainServerRouter / direkte Handler
  -> LobbyManager / DeliveryDispatcher
```

### Laufzeitverhalten

1. Für jede WebSocket-Verbindung wird eine serverseitige `ConnectionId` vergeben.
2. `moduleWithLobbyRuntime(...)` ordnet aktuell jeder neuen Verbindung eine neue `PlayerId` zu.
3. `ServerNetwork` emittiert technische Events (`Connected`, `MessageReceived`, `Disconnected`, `Error`).
4. `MainServerLobbyRoutingService` dekodiert `ReceivedPacket`s zu `DecodedNetworkRequest`s.
5. Je nach Payload:
   - direkte technische Handler auf Routing-Service-Ebene, z. B. für
     - `CreateLobbyRequest`
     - `MapGetRequest`
     - `GameStatePrivateGetRequest`
     - `GameStateCatchUpRequest`
     - `StartPlayerSetRequest`
     - `TurnStateGetRequest`
     - `TurnAdvanceRequest`
   - oder Weitergabe an `MainServerRouter` für gemappte Lobby-Events wie Join/Leave/Kick/StartGame

## Aktuell integrierte GameState-Übertragung

### Öffentliche Server-zu-Lobby-Nachrichten

- `GameStateDeltaEvent`
- `PhaseBoundaryEvent`
- `GameStateSnapshotBroadcast`
- öffentliche Einzel-Events wie `TurnStateUpdatedEvent`, `TerritoryOwnerChangedEvent`, `TerritoryTroopsChangedEvent`, `GameStartedEvent`

### Private Server-zu-Client-Nachrichten

- `GameStatePrivateGetResponse`

### Client-zu-Server Requests

- `MapGetRequest`
- `GameStatePrivateGetRequest`
- `GameStateCatchUpRequest`
- `TurnAdvanceRequest`
- `TurnStateGetRequest`

### Sequenz bei erfolgreichem `TurnAdvanceRequest`

Reihenfolge der öffentlichen S2L-Nachrichten:

1. `GameStateDeltaEvent`
2. `PhaseBoundaryEvent`
3. `TurnStateUpdatedEvent`
4. optional `GameStateSnapshotBroadcast` bei Spielerwechsel

Zusätzlich erhält der anfragende Client synchron die `TurnAdvanceResponse`.

### Public/Private Delivery

- `GameStateDeliveryDispatcher` trennt technisch zwischen öffentlichen und privaten Payloads.
- `PublicGameStateBuilder` ist die einzige Quelle für öffentliche Snapshots und Deltas.
- Private Daten werden nicht in `GameStateDeltaEvent` oder öffentliche Snapshot-Broadcasts aufgenommen.

### Observability

Der Server loggt an den relevanten Sendepfaden mindestens:
- `lobbyCode`
- `playerId` bzw. aktiven Spieler
- `fromVersion` / `toVersion` bei Deltas
- `stateVersion`
- `turnCount`

Zusätzlich hält der Server pro Lobby einen `RoundHistoryBuffer` für die letzten zwei Runden.

## Appseitige Integration

## Aktueller Stand

Die App besitzt inzwischen eine produktive technische Client-Implementierung. Sie besteht nicht aus einer direkten `Network<Unit>`-Implementierung, sondern aus einer Komposition folgender Klassen:

- `AndroidWebSocketTransport`
- `PacketReceiver`
- `PacketSender`
- `ClientNetwork`
- `LobbyController`

### Datenfluss

```text
AndroidWebSocketTransport.events
  -> ClientNetwork
    -> PacketReceiver
      -> LobbyController
        -> Compose UI
```

### Aktuell fachlich genutzte Nachrichten

Produktiv im Client verarbeitet werden derzeit:
- `CreateLobbyResponse`
- `CreateLobbyErrorResponse`
- `JoinLobbyResponse`
- `JoinLobbyErrorResponse`
- `PlayerJoinedLobbyEvent`
- `PlayerLeftLobbyEvent`
- `PlayerKickedLobbyEvent`

Technisch empfangbar, aber aktuell nicht fachlich verdrahtet:
- `GameStateDeltaEvent`
- `PhaseBoundaryEvent`
- `GameStateSnapshotBroadcast`
- `GameStateCatchUpResponse`
- `GameStatePrivateGetResponse`
- `MapGetResponse`
- `TurnStateGetResponse`

Für diese Payloads existiert im Client derzeit noch keine produktive State-Verarbeitung.

### Initialisierung in der App

Der aktuelle Einstiegspunkt erzeugt genau einen `LobbyController`:

```kotlin
val lobbyController = remember { LobbyController() }
```

Dieser Controller verwaltet:
- Verbindung
- Lobby-Create/Join/Leave
- Spielerliste im Waiting Room

## Neue Nachrichten hinzufügen

Damit eine neue Nachricht end-to-end funktioniert, müssen aktuell folgende Stellen konsistent erweitert werden:

1. Payload-Klasse im `:shared`
2. `MessageType`
3. `NetworkPayloadRegistry`
4. Serializer-/Codec-Tests
5. Server-Routing oder Server-Delivery
6. optional Client-Verarbeitung, falls die App die Nachricht fachlich nutzen soll

## Build und lokale Verifikation

Typische Befehle:

```bash
./gradlew :shared:test :server:test
./gradlew :app:testDebugUnitTest
./gradlew :server:run
./gradlew dokkaLocal
```

Hinweis:
- Im aktuellen Repository wird für umfangreichere Testläufe häufig `-Pkotlin.incremental=false` verwendet.

## Bekannte Grenzen

- Die App besitzt noch keine autoritative Client-State-Schicht für GameState-Deltas und Snapshots.
- Player-Identität ist aktuell serverseitig an die WebSocket-Verbindung gekoppelt; es gibt noch kein persistentes Session-/Auth-Modell.
- `:e2e` ist vorbereitet, enthält aber noch keine echten produktiven End-to-End-Szenarien.
