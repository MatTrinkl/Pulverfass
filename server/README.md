# Server Module

Das Servermodul stellt einen Ktor-Server mit WebSocket-Unterstützung bereit.

## WebSocket-Konfiguration

- Das Ktor-Plugin `WebSockets` wird in `Application.module()` installiert.
- Der WebSocket-Endpunkt ist unter `/ws` verfügbar.
- `ConnectionManager` verwaltet aktive technische Verbindungen zentral und stellt `send`, `sendMany` und `broadcast`
  bereit.
- `SessionManager` verwaltet stabile Session-Tokens über Verbindungswechsel hinweg und bildet die Grundlage für
  spätere Reconnect-Flows.
- Die serverseitige Transport-Schicht `ServerWebSocketTransport` registriert und deregistriert WebSocket-Sessions über
  den `ConnectionManager` und emittiert technische Transport-Events.
- Pro Verbindung wird serverseitig eine `ConnectionId` vergeben.
- Nach erfolgreichem Connect sendet der Server automatisch eine `CONNECTION_RESPONSE` mit einem `SessionToken` an den
  Client.
- Transport-Events werden als `SharedFlow` emittiert: `Connected`, `BinaryMessageReceived`, `Disconnected` und optional
  `TransportError`.
- Binary Frames werden als rohe ByteArrays weitergereicht und können über `send(connectionId, bytes)` auch wieder an
  bestehende Verbindungen gesendet werden.
- Für den technischen Outbound-Pfad verpackt der shared `PacketSendAdapter` ein `SerializedPacket` über `PacketCodec`
  in Wire-Bytes; der serverseitige `PacketSender` liefert diese Bytes via `ServerWebSocketTransport` aus.
- Text Frames werden in Serie 1 aktiv gemäß `WebSocketPolicy` abgelehnt: Der Server schließt die Verbindung mit
  `CANNOT_ACCEPT` und der Nachricht `Text frames are not supported on /ws.`.
- Weitere fachliche Nachrichtenverarbeitung ist bewusst noch nicht angeschlossen.

## Start und Stop

- Lokal starten: `./gradlew :server:run`
- Für programmatischen Start/Stop steht `createServer(host, port)` zur Verfügung.
- Sauberes Stoppen erfolgt über `ApplicationEngine.stop(gracePeriodMillis, timeoutMillis)`.

## Lobby Runtime (sequentielle Event-Loops)

- Der Server enthält mit `LobbyManager` eine zentrale Verwaltung für mehrere Lobbys.
- Jede Lobby wird als eigene `LobbyRuntime` gekapselt.
- `LobbyRuntime` bündelt LobbyId (`LobbyCode`), gekapselten `GameState`, Event-Loop, Lifecycle und optionale Hooks.
- Innerhalb einer Lobby laufen Events strikt sequentiell (FIFO).
- Mehrere Lobbys können parallel verarbeitet werden (Coroutine-basiert, kein Thread-per-Lobby-Modell).
- Read-Zugriff erfolgt über `LobbyStateReader.currentState()` als Snapshot.

### API (nach außen relevant)

- `createLobby(lobbyCode, initialState)` erstellt und startet eine Lobby.
- `getLobby(lobbyCode)` liefert die Runtime einer Lobby.
- `submit(event, context)` reiht ein Event für eine bereits laufende Lobby ein.
- `removeLobby(lobbyCode)` beendet eine Lobby kontrolliert.
- `shutdownAll()` beendet alle laufenden Lobby-Loops kontrolliert.

### Queue- und Backpressure-Verhalten

- Jede `LobbyRuntime` nutzt intern einen `Channel` mit konfigurierbarer Kapazität (`queueCapacity`).
- Ist die Queue voll, suspendiert `submit(...)` bis wieder Platz frei ist.
- Dadurch entsteht kontrolliertes Backpressure statt unkontrolliertem Parallelzugriff auf den `GameState`.

## Netzwerk-zu-Domain-Mapping (Lobby)

- `DecodedNetworkRequest` ist das neutrale Inputmodell für bereits dekodierte Requests (`ConnectionId`, `MessageHeader`, `NetworkMessagePayload`, `EventContext`).
- `NetworkToLobbyEventMapper` trennt Netzwerkmodell und Lobby-Domain.
- `DefaultNetworkToLobbyEventMapper` mappt aktuell:
  - `LOBBY_JOIN_REQUEST` + `JoinLobbyRequest` + `EventContext.playerId` -> `PlayerJoined`
- Definierte Fehlerfälle:
  - Header/Payload-Mismatch
  - fehlender `playerId`-Kontext
  - nicht unterstützte Lobby-Payloads

## MainServer Routing-Layer

- `MainServerRouter` verbindet Mapping-Schicht und `LobbyManager`.
- Input ist ein bereits technisch dekodierter `DecodedNetworkRequest`.
- Ablauf:
  - Request wird über `NetworkToLobbyEventMapper` in Domain-Events übersetzt
  - Routingdaten werden validiert (nicht-leere Events, konsistente Lobby-Zuordnung)
  - Ziel-Lobby wird über `LobbyManager` aufgelöst
  - Events werden mit `EventContext` an die `LobbyRuntime` weitergereicht
- Definierte Routing-Fehler:
  - unbekannte Lobby
  - leeres Mapping-Ergebnis
  - Event-Lobby passt nicht zur gerouteten Lobby
- Das transportunabhängige Ergebnis-/Fehlermodell:
  - `LobbyRoutingResult.Success`
  - `LobbyRoutingResult.Failure(LobbyRoutingError)`
  - Fehlerarten: `LobbyNotFound`, `InvalidRoutingData`, `InvalidEvent`, `InvalidStateTransition`

### Server-Einbettung

- `MainServerLobbyRoutingService` bindet `ServerNetwork` an den Router:
  - liest `ReceivedPacket` aus `PacketReceiver`
  - dekodiert die Payload
  - erzeugt `DecodedNetworkRequest`
  - ruft `MainServerRouter.handle(...)` auf
- Damit bleibt der Transportpfad von Lobby-Domainlogik getrennt.
