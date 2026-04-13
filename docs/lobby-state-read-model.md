# LobbyState ReadModel / Snapshot-Zugriff

## Ziel und Scope

Diese Spezifikation definiert, wie andere Schichten den aktuellen Zustand einer Lobby **lesend** verwenden können, ohne den internen `GameState` unkontrolliert zu mutieren.

Im Scope:
- konsistenter Read-Zugriff auf den aktuellen Lobbyzustand
- kein direkter Fremdschreibzugriff von außen
- kompatibel mit sequentieller Eventverarbeitung
- nutzbar für Logging, Debugging, MainServer und spätere Response-Erzeugung

Out of Scope:
- UI-ViewModels
- Persistenz
- API-Serialisierung
- Spielregeln

## Gewählte Strategie

Es werden **immutable Snapshots** verwendet:

- Lesende Schichten nutzen `LobbyStateReader`.
- Schreibende Verarbeitung läuft über `LobbyStateProcessor.apply(...)` und damit über den `LobbyEventReducer`.
- `currentState()` liefert immer eine Snapshot-Kopie des aktuellen States.

Damit gibt es kein Teilen einer intern gehaltenen, veränderbaren Listen-Referenz.

## API

```kotlin
interface LobbyStateReader {
    fun currentState(): GameState
}

interface LobbyStateProcessor : LobbyStateReader {
    fun apply(event: LobbyEvent, context: EventContext? = null): GameState
}
```

Referenzimplementierung:

- `DefaultLobbyStateProcessor`
  - hält den aktuellen Zustand intern
  - synchronisiert über `ReentrantReadWriteLock`
  - gibt bei Reads/Writes Snapshot-Kopien zurück

## Thread-Safety und Event-Reihenfolge

- **Writes exklusiv:** `apply(...)` verwendet einen Write-Lock.
- **Reads parallel:** mehrere `currentState()`-Aufrufe können parallel laufen.
- **Sequenziell kompatibel:** Events werden effektiv nacheinander auf denselben Zustand angewendet.
- **Kein unkontrollierter Fremdschreibzugriff:** Consumer, die nur ein `LobbyStateReader` erhalten, haben keinen Schreibpfad.

## Nutzungsmuster

### Nur lesen (z. B. Response-Erzeugung)

```kotlin
fun renderLobby(reader: LobbyStateReader): String {
    val snapshot = reader.currentState()
    return "Lobby ${snapshot.lobbyCode.value}: ${snapshot.playerCount} Spieler"
}
```

### Event anwenden + danach lesen

```kotlin
val processor = DefaultLobbyStateProcessor(GameState.initial(lobbyCode))
processor.apply(PlayerJoined(lobbyCode, PlayerId(1)))
val snapshotForLog = processor.currentState()
```

## Testabdeckung

Die Implementierung ist über folgende Tests abgesichert:

1. `aktueller snapshot kann gelesen werden`
2. `lesen veraendert den state nicht`
3. `lesen kollidiert nicht mit eventverarbeitung`
