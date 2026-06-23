# Server-Modul

Das Modul `:server` stellt den Ktor-WebSocket-Server, die Lobby-Runtime und die serverseitige GameState-Delivery bereit.

## Start

```bash
./gradlew :server:run
```

### Docker

Default-Port im Container: `8080`

Aus dem Repo-Root:

```bash
./gradlew :server:installDist
docker build -t se2risiko-server .
docker run --rm -p 8080:8080 se2risiko-server
```

Relevante Runtime-ENV-Variablen:

- `PORT` optional, Default `8080`
- `HOST` optional, Default `0.0.0.0`
- `APP_VERSION` optional, Default `dev` oder Manifest-Version falls vorhanden
- `DB_URL` optional, überschreibt Host/Port/Name falls gesetzt
- `DB_HOST` optional, nötig wenn kein `DB_URL` gesetzt ist
- `DB_PORT` optional, Default `5432`
- `DB_NAME` optional, nötig wenn kein `DB_URL` gesetzt ist
- `DB_USER` nötig sobald DB konfiguriert wird
- `DB_PASSWORD` nötig sobald DB konfiguriert wird
- `DB_POOL_MAX_SIZE` optional, Default `10`
- `DB_CONNECTION_TIMEOUT_MS` optional, Default `5000`
- `DB_VALIDATION_TIMEOUT_MS` optional, Default `2000`
- `WS_MAX_FRAME_SIZE_BYTES` optional, Default `1048576`

Smoke-Test auf demselben Port:

```bash
curl http://127.0.0.1:8080/health
curl http://127.0.0.1:8080/version
curl http://127.0.0.1:8080/ready
```

### Production Compose

Die Root-`compose.yaml` ist jetzt auf den Deploy-Server ausgerichtet:

- `server` läuft aus einem GHCR-Image
- `dokka` läuft aus einem separaten GHCR-Image
- `db` nutzt das offizielle PostgreSQL-Image
- beide Services teilen ein internes Docker-Netzwerk
- PostgreSQL hat keine veröffentlichten Host-Ports
- DB-Daten liegen persistent im Volume `postgres_data`
- Dokka wird öffentlich über `DOKKA_PORT` ausgeliefert
- `server` und `dokka` laufen im Deploy-Setup ohne zusätzliche Linux-Capabilities

Die produktive `.env` liegt auf dem Server und gehört nicht ins Repo. Als Vorlage dient [.env.example](../.env.example).

Beispiel auf dem Server:

```bash
cp .env.example .env
# Werte in .env anpassen
docker compose up -d
```

Readiness mit DB-Prüfung:

```bash
curl -i http://127.0.0.1:8080/ready
```

Wichtig:

- `SERVER_IMAGE` muss auf ein veröffentlichtes GHCR-Image zeigen
- `DOKKA_IMAGE` muss auf ein veröffentlichtes GHCR-Image zeigen
- `POSTGRES_PASSWORD` muss serverseitig gesetzt werden
- die DB ist nur intern über den Service-Namen `db` erreichbar

### Main Deploy via GitHub Actions

Der Deploy-Workflow auf `main` erwartet folgende GitHub Secrets/Vars:

- `DEPLOY_SSH_KEY`
- `DEPLOY_HOST`
- `DEPLOY_USER`
- `DEPLOY_PORT`
- `DEPLOY_PATH`
- optional `DEPLOY_HOST_KEY`
- optional `DEPLOY_HEALTH_URL`
- optional `DEPLOY_DOKKA_URL`

Der Workflow deployed per SSH mit:

- `docker login ghcr.io`
- `docker compose pull server dokka`
- `docker compose up -d`
- anschließend Smoke-Tests gegen `/health` und die Dokka-`index.html`

Die vollständige Setup- und Secret-Dokumentation steht in [docs/deploy.md](../docs/deploy.md).

### Local Compose Override

Für einen lokalen Build ohne GHCR-Pull gibt es zusätzlich `compose.local.yaml`:

```bash
export POSTGRES_PASSWORD='<SET_LOCALLY>'
export DB_PASSWORD='<SET_LOCALLY>'
./gradlew :server:installDist dokkaGenerate
docker compose -f compose.yaml -f compose.local.yaml up --build
```

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
- `MainServerLobbyRoutingService`
- `SessionManager`
- `SessionContextRegistry`

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

- Reconnect nutzt stabile Session-Tokens, ist aber noch kein vollständiges Auth-System.
- Der Server hält eine Default-Map im Speicher; Multi-Map-Management ist noch nicht implementiert.
- Der `RoundHistoryBuffer` dient Diagnosezwecken und ist noch kein öffentliches Replay-API.
- Lobbys, Events, Snapshots und Reconnect-Kontext werden serverseitig in PostgreSQL persistiert.
