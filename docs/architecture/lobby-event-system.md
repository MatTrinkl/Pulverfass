# Lobby-Event-System

## Ziel

Das Lobby-Event-System ist die fachliche Zustandsmaschine des Projekts. Es kapselt Lobby-, Turn- und Map-bezogene Zustandsänderungen in transportunabhängigen Domain-Events und wendet diese deterministisch über den Reducer auf den `GameState` an.

## Kernbausteine

### `LobbyEvent`

Pfad:
- `shared/src/main/kotlin/at/aau/pulverfass/shared/lobby/event/LobbyEvent.kt`

Struktur:
- `ExternalLobbyEvent`
- `InternalLobbyEvent`

Die Events sind bewusst unabhängig von Netzwerk und UI. Die Übersetzung von `NetworkMessagePayload` nach Domain-Events passiert erst im Server-Routing.

### `GameState`

Pfad:
- `shared/src/main/kotlin/at/aau/pulverfass/shared/lobby/state/GameState.kt`

Verantwortung:
- hält den vollständigen fachlichen Zustand einer Lobby
- enthält Spieler, Status, Turn-State, Map-Definition, Territory-State und `stateVersion`
- bietet Query-Funktionen für Gameplay-nahe Logik wie:
  - `ownerOf`
  - `troopsOn`
  - `neighbors`
  - `isAdjacent`
  - `continentOwner`
  - `continentsOwnedBy`
  - `isConnectedByOwnedPath`

Wichtige Invarianten:
- `players` und `turnOrder` enthalten dieselben Spieler
- `turnState`, `activePlayer` und `configuredStartPlayerId` müssen konsistent sein
- `mapDefinition` und `territoryStates` sind entweder gemeinsam gesetzt oder gemeinsam leer

### `DefaultLobbyEventReducer`

Pfad:
- `shared/src/main/kotlin/at/aau/pulverfass/shared/lobby/reducer/DefaultLobbyEventReducer.kt`

Verantwortung:
- validiert Events gegen den aktuellen `GameState`
- erzeugt deterministisch eine neue State-Kopie
- erhöht über `withMetadata(context)` bei jeder erfolgreichen Anwendung `stateVersion` und `processedEventCount`

## Aktuell relevante Event-Gruppen

### Lobby- und Lifecycle-Events

- `LobbyCreated`
- `LobbyClosed`
- `PlayerJoined`
- `PlayerLeft`
- `PlayerKicked`
- `InvalidActionDetected`
- `SystemTick`
- `TimeoutTriggered`

### Setup- und Spielstart-Events

- `StartPlayerConfigured`
- `GameStarted`

### Turn-Events

- `TurnEnded`
- `TurnStateUpdatedEvent`

Wichtig:
- Die eigentliche Mutation des kombinierten Turn-Zustands erfolgt ausschließlich über `TurnStateUpdatedEvent`.
- `TurnEnded` ist fachlich weiterhin vorhanden, wird im aktuellen Serverpfad für Turn-Transitions aber nicht als primäre öffentliche Netzwerk-Nachricht verwendet.

### Map-State-Events

- `TerritoryOwnerChangedEvent`
- `TerritoryTroopsChangedEvent`

Diese Events sind die einzigen vorgesehenen Mutationen des mutierbaren Map-Zustands im `GameState`.

## Reducer-Regeln

### Lobby-Management

- `PlayerJoined` ergänzt Spieler, Anzeigenamen und Turn-Order.
- `PlayerLeft` und `PlayerKicked` entfernen Spieler deterministisch aus Spielerliste und Turn-Order.
- Vor Spielstart synchronisiert der Reducer den vorbereiteten Turn-State mit der aktuellen Spielerliste.
- Nach Spielstart bleibt der Status bei mindestens zwei Spielern `RUNNING`, sonst fällt die Lobby auf `WAITING_FOR_PLAYERS` zurück.

### Startspieler und Spielstart

- `StartPlayerConfigured` ist nur vor Spielstart zulässig.
- `StartPlayerConfigured` darf nur vom `lobbyOwner` ausgelöst werden.
- `GameStarted` erfordert mindestens zwei Spieler.
- Beim Spielstart wird ein initialer Turn-State über dieselbe kombinierte TurnState-Update-Logik erzeugt wie bei späteren Transitions.

### Turn-State

- `TurnStateUpdatedEvent` aktualisiert atomar:
  - `activePlayerId`
  - `turnPhase`
  - `turnCount`
  - `startPlayerId`
  - `isPaused`
  - `pauseReason`
  - `pausedPlayerId`
- `turnCount` darf im Reducer nie rückwärts laufen.
- `pausedPlayerId` muss bei `WAITING_FOR_PLAYER` dem aktiven Spieler entsprechen.

### Map-State

- `TerritoryOwnerChangedEvent` und `TerritoryTroopsChangedEvent` erfordern eine geladene Map.
- Mutationen unbekannter Territorien werden abgewiesen.
- Ein Territory-Owner muss Teil der Lobby sein, falls er gesetzt wird.

## Serverseitige Runtime

Die Anwendung der Events erfolgt pro Lobby sequentiell in der Runtime:

```text
MainServerLobbyRoutingService
  -> MainServerRouter / direkte Handler
  -> LobbyManager.submit(event, context)
  -> LobbyRuntime / LobbyEventLoop
  -> DefaultLobbyEventReducer.apply(...)
```

Eigenschaften:
- FIFO-Verarbeitung pro Lobby
- parallele Verarbeitung mehrerer Lobbys
- keine direkte Mutation außerhalb des Reducers

## Verhältnis zu Netzwerk-Nachrichten

Wichtig:
- `LobbyEvent`s sind Domain-Modell
- `NetworkMessagePayload`s sind Transportmodell

Beispiele:
- `JoinLobbyRequest` wird serverseitig in `PlayerJoined` übersetzt
- `TurnAdvanceRequest` führt serverseitig zu genau einem `TurnStateUpdatedEvent`
- `GameStateDeltaEvent` transportiert nur öffentliche Projektionen, nicht rohe Domain-Events beliebiger Sichtbarkeit

## Fehlerverhalten

Ungültige Zustandsübergänge werden nicht still ignoriert. Typische Fehlerquellen:

- Event gehört zu einer anderen Lobby als der State
- Spieler ist nicht Teil der Lobby
- Spielstart mit weniger als zwei Spielern
- Turn-Transition für falschen aktiven Spieler
- Map-Mutation ohne geladene Map
- `turnCount` läuft rückwärts

Im Shared-Layer werden dafür vor allem `InvalidLobbyEventException` und `LobbyCodeMismatchException` genutzt.

## Erweiterungspunkte

Wenn neue fachliche Systeme dazukommen, gilt:

1. neues Domain-Event in `shared.lobby.event`
2. klare Reducer-Regel in `DefaultLobbyEventReducer`
3. Tests im Shared-Layer
4. erst danach optional Routing, Delivery oder Client-Nutzung

Empfohlen:
- Domain-Event zuerst im Reducer verankern
- Netzwerkmodell davon getrennt halten
- öffentliche und private Sichtbarkeit erst in der Delivery-Schicht festlegen

## Aktuelle Grenzen

- Es gibt noch kein persistentes Event-Log.
- Reconnect nutzt autoritative Snapshots statt eines öffentlichen Event-Replays.
- Die Android-App konsumiert bislang nur einen kleinen Teil der resultierenden öffentlichen GameState-Nachrichten fachlich.
