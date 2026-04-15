# LobbyEvent System

Das LobbyEvent System ist eine ereignisgesteuerte Architektur für die Verwaltung von Lobby-Zuständen im Risiko-Spiel. Es folgt dem Event Sourcing Pattern mit einem funktionalen Reducer-Ansatz.

## Überblick

Das System besteht aus drei Hauptkomponenten:

1. **Event-Hierarchie** (`LobbyEvent`) - Die verschiedenen Ereignistypen
2. **Reducer** (`LobbyEventReducer`) - Verarbeitet Events und aktualisiert den Zustand
3. **State** (`GameState`) - Der aktuelle Zustand einer Lobby

### Architektur-Prinzipien

- **Deterministische Zustandsübergänge**: Jeder Event führt zu exakt einem neuen Zustand
- **Validierung an einer Stelle**: Alle Zustandsänderungen erfolgen über den Reducer
- **Funktionale Programmierung**: State ist unveränderlich (immutable)
- **Fehlerbehandlung**: Ungültige Zustandsübergänge werfen explizite Exceptions
- **Transport-Unabhängigkeit**: Events sind unabhängig von Netzwerk- oder UI-Details

## Event-Hierarchie

Die Event-Hierarchie ist zweistufig aufgebaut:

```
LobbyEvent (Basisinterface)
├── ExternalLobbyEvent (von außen ausgelöste Events)
│   ├── PlayerJoined
│   ├── PlayerLeft
│   └── TurnEnded
└── InternalLobbyEvent (System- und Lifecycle-Events)
    ├── LobbyCreated
    ├── LobbyClosed
    ├── InvalidActionDetected
    ├── SystemTick
    └── TimeoutTriggered
```

Diese Struktur ermöglicht Clients, mit Root-Events (`LobbyEvent`) zu arbeiten, während neue Event-Typen stabil hinzugefügt werden können.

## Events verwenden

### ExternalLobbyEvent

Diese Events werden durch externe Aktionen ausgelöst (Spielerzüge, Netzwerknachrichten):

#### PlayerJoined
```kotlin
data class PlayerJoined(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val playerDisplayName: String,
) : ExternalLobbyEvent
```
**Bedeutung**: Ein Spieler ist einer Lobby beigetreten.

**Auswirkungen auf den State**:
- Spieler wird zur Spielerliste hinzugefügt
- Spieler wird zur Reihenfolge hinzugefügt
- Lobby-Status wird ggf. auf `RUNNING` aktualisiert (wenn mindestens 2 Spieler)
- Initialisiert `activePlayer`, falls noch keine aktiver Spieler existiert

**Validierung**:
- Wirft `InvalidLobbyEventException`, wenn der Spieler bereits in der Lobby ist

#### PlayerLeft
```kotlin
data class PlayerLeft(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val reason: String? = null,
) : ExternalLobbyEvent
```
**Bedeutung**: Ein Spieler hat eine Lobby verlassen.

**Auswirkungen auf den State**:
- Spieler wird aus der Spielerliste entfernt
- Spieler wird aus der Reihenfolge entfernt
- Wenn der Spieler aktiv war, wird der nächste Spieler in der Reihenfolge aktiv
- Lobby-Status wird ggf. auf `WAITING_FOR_PLAYERS` aktualisiert

**Validierung**:
- Wirft `InvalidLobbyEventException`, wenn der Spieler nicht in der Lobby ist

#### TurnEnded
```kotlin
data class TurnEnded(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId,
) : ExternalLobbyEvent
```
**Bedeutung**: Ein Spieler hat seinen Zug beendet.

**Auswirkungen auf den State**:
- Der nächste Spieler wird zum aktiven Spieler
- Rundennummer wird erhöht

**Validierung**:
- Wirft `InvalidLobbyEventException`, wenn:
  - Lobby nicht im Status `RUNNING` ist
  - Der Spieler nicht der aktive Spieler ist
  - Die Reihenfolge leer ist

### InternalLobbyEvent

Diese Events werden intern vom System erzeugt:

#### LobbyCreated
```kotlin
data class LobbyCreated(
    override val lobbyCode: LobbyCode,
) : InternalLobbyEvent
```
**Bedeutung**: Eine neue Lobby wurde erstellt.

**Auswirkungen auf den State**:
- Lobby-Status wird auf `WAITING_FOR_PLAYERS` gesetzt
- `closedReason` wird auf null gesetzt

#### LobbyClosed
```kotlin
data class LobbyClosed(
    override val lobbyCode: LobbyCode,
    val reason: String? = null,
) : InternalLobbyEvent
```
**Bedeutung**: Eine Lobby wurde geschlossen.

**Auswirkungen auf den State**:
- Lobby-Status wird auf `CLOSED` gesetzt
- Aktiver Spieler wird auf null gesetzt
- `closedReason` wird gespeichert

**Typische Gründe**: `"finished"`, `"timeout"`, `"all_players_left"`

#### InvalidActionDetected
```kotlin
data class InvalidActionDetected(
    override val lobbyCode: LobbyCode,
    val playerId: PlayerId? = null,
    val reason: String,
) : InternalLobbyEvent
```
**Bedeutung**: Ein ungültiger Spielerzug wurde erkannt.

**Auswirkungen auf den State**:
- `lastInvalidActionReason` wird aktualisiert
- State ändert sich sonst nicht (für Debugging/Logging)

#### SystemTick
```kotlin
data class SystemTick(
    override val lobbyCode: LobbyCode,
    val tick: Long,
) : InternalLobbyEvent {
    init {
        require(tick >= 0) { "SystemTick.tick darf nicht negativ sein, war aber $tick." }
    }
}
```
**Bedeutung**: Interner Taktimpuls für periodische Verarbeitung.

**Auswirkungen auf den State**:
- Keine Zustandsänderung (idempotent)
- Nützlich für Heartbeat und Timeout-Tracking

**Validierung**:
- `tick` darf nicht negativ sein

#### TimeoutTriggered
```kotlin
data class TimeoutTriggered(
    override val lobbyCode: LobbyCode,
    val target: String,
    val timeoutMillis: Long,
) : InternalLobbyEvent
```
**Bedeutung**: Ein Timeout ist für einen bestimmten Aspekt eingetreten.

**Auswirkungen auf den State**:
- Keine direkte Zustandsänderung
- Wird typischerweise mit `LobbyClosed` kombiniert

**Typische Targets**: `"turn"`, `"heartbeat"`, `"login"`

## Der Reducer

Der `DefaultLobbyEventReducer` ist die zentrale Komponente für Zustandsänderungen.

### Interface
```kotlin
interface LobbyEventReducer {
    fun apply(
        state: GameState,
        event: LobbyEvent,
        context: EventContext? = null,
    ): GameState
}
```

### Verwendung

```kotlin
val reducer = DefaultLobbyEventReducer()
val newState = reducer.apply(
    state = currentState,
    event = PlayerJoined(lobbyCode, playerId, "Alice"),
    context = eventContext
)
```

### Verhalten

1. **Determinismus**: Für den gleichen Input (State + Event) wird immer der gleiche Output produziert
2. **Validierung**: Ungültige Zustandsübergänge werfen sofort eine Exception
3. **Immutabilität**: Der ursprüngliche State wird nicht verändert
4. **Metadaten**: Der neue State wird mit EventContext-Metadaten angereichert

### Fehlerbehandlung

Der Reducer wirft folgende Exceptions:

#### LobbyCodeMismatchException
```kotlin
exception LobbyCodeMismatchException(
    expectedLobbyCode: LobbyCode,
    actualLobbyCode: LobbyCode
)
```
Das Event bezieht sich auf eine andere Lobby als der State.

#### InvalidLobbyEventException
```kotlin
exception InvalidLobbyEventException(message: String)
```
Der Zustandsübergang ist ungültig, z.B.:
- Spieler tritt bei, ist aber bereits in der Lobby
- Zug endet von Spieler, der nicht aktiv ist
- Zug im Status, der nicht RUNNING ist

#### Allgemeine Regel
**Alle Fehler sollten explizit behandelt werden** - es gibt kein stilles Fehlschlag. Dies erleichtert Debugging und verhindert Zustandsinkorherenzen.

## Events erzeugen

### Pattern Matching

Kotlin's `when` mit sealed Interfaces ermöglicht typsicheres Pattern Matching:

```kotlin
val event: LobbyEvent = PlayerJoined(lobbyCode, playerId, "Alice")

// Auf Root-Ebene
when (event) {
    is ExternalLobbyEvent -> println("Externe Aktion: ${event.lobbyCode}")
    is InternalLobbyEvent -> println("Interner Event: ${event.lobbyCode}")
}

// Auf spezifischer Ebene
when (event) {
    is PlayerJoined -> println("Spieler ${event.playerId} beigetreten")
    is PlayerLeft -> println("Spieler ${event.playerId} verlassen: ${event.reason}")
    is TurnEnded -> println("Spieler ${event.playerId} beendet Zug")
    // InternalLobbyEvents...
    is LobbyCreated -> println("Lobby erstellt")
    is LobbyClosed -> println("Lobby geschlossen: ${event.reason}")
    is InvalidActionDetected -> println("Ungültig: ${event.reason}")
    is SystemTick -> println("Tick: ${event.tick}")
    is TimeoutTriggered -> println("Timeout: ${event.target}")
}
```

Der Kotlin-Compiler erzwingt, dass alle Fälle abgedeckt werden.

## Neue Events hinzufügen

### Schritt 1: Entscheiden, ob External oder Internal

- **External**: Events, die durch Benutzerinteraktion oder Netzwerknachricht ausgelöst werden
- **Internal**: System- und Lifecycle-Events

### Schritt 2: Event-Klasse erstellen

Erstelle eine neue Datei unter `shared/src/main/kotlin/at/aau/pulverfass/shared/lobby/event/`:

```kotlin
// MyNewEvent.kt
package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode

/**
 * Beschreibung des Events.
 *
 * @property lobbyCode betroffene Lobby
 * @property customProperty Event-spezifische Daten
 */
data class MyNewEvent(
    override val lobbyCode: LobbyCode,
    val customProperty: String,
) : ExternalLobbyEvent  // oder InternalLobbyEvent
```

**Wichtig**:
- Das Event muss `LobbyEvent` implementieren
- Eine Unterklasse von `ExternalLobbyEvent` oder `InternalLobbyEvent` sein
- `lobbyCode` ist immer erforderlich
- KDoc sollte alle Properties dokumentieren

### Schritt 3: Reducer-Logik hinzufügen

Editiere `DefaultLobbyEventReducer.kt`:

```kotlin
class DefaultLobbyEventReducer : LobbyEventReducer {
    override fun apply(
        state: GameState,
        event: LobbyEvent,
        context: EventContext?,
    ): GameState {
        if (event.lobbyCode != state.lobbyCode) {
            throw LobbyCodeMismatchException(...)
        }

        val updatedState = when (event) {
            // ... existierende Events ...
            
            is MyNewEvent -> onMyNewEvent(state, event)
        }

        return updatedState.withMetadata(context)
    }

    private fun onMyNewEvent(
        state: GameState,
        event: MyNewEvent,
    ): GameState {
        // Validierung
        if (/* ungültiger Zustand */) {
            throw InvalidLobbyEventException("Beschreibung des Fehlers")
        }

        // Zustandsänderung
        return state.copy(
            // ... aktualisierte Properties ...
        )
    }
}
```

**Validierungs-Checkliste**:
- Ist der State in einem gültigen Zustand für dieses Event?
- Sind alle erforderlichen Daten im Event vorhanden?
- Können Spieler-IDs validiert werden?
- Sollten bestimmte Lobby-Status ausgeschlossen werden?

### Schritt 4: Tests schreiben

Editiere `DefaultLobbyEventReducerTest.kt`:

```kotlin
@Test
fun `should update state when MyNewEvent is applied`() {
    val initialState = createGameState(/* ... */)
    val event = MyNewEvent(lobbyCode, "test-data")
    
    val newState = reducer.apply(initialState, event)
    
    // Assertions für erwartete Zustandsänderungen
    assertEquals(/* expected property */, newState./* property */)
}

@Test
fun `should throw when MyNewEvent applied in invalid state`() {
    val invalidState = createGameState(status = GameStatus.CLOSED)
    val event = MyNewEvent(lobbyCode, "test-data")
    
    assertThrows<InvalidLobbyEventException> {
        reducer.apply(invalidState, event)
    }
}
```

### Schritt 5: Exhaustiveness überprüfen

Der Kotlin-Compiler erzwingt, dass alle `when`-Ausdrücke mit Events alle möglichen Fälle abdecken. Nach dem Hinzufügen eines neuen Events:

1. Der Reducer muss eine neue `is MyNewEvent` Klausel hinzufügen
2. Alle `when`-Ausdrücke in Consumer-Code müssen aktualisiert werden

Dies ist ein großer Vorteil der sealed-Interface-Struktur - **Breaking Changes sind sofort erkannt**.

## Best Practices

### 1. Immutabilität bewahren
```kotlin
// ✅ Gut: Neuer State wird kopiert
return state.copy(players = state.players + playerId)

// ❌ Falsch: Mutation in-place
state.players.add(playerId)
return state
```

### 2. Validierung vor Mutation
```kotlin
// ✅ Gut: Validierung zuerst, dann Mutation
if (!state.players.contains(playerId)) {
    throw InvalidLobbyEventException("Player nicht gefunden")
}
return state.copy(activePlayer = playerId)

// ❌ Falsch: Mutation kann zu inkonsistentem State führen
return state.copy(activePlayer = playerId)
// Crash passiert danach...
```

### 3. EventContext verwenden
```kotlin
// ✅ Gut: Metadaten für Tracing/Debugging
reducer.apply(state, event, eventContext)

// ❌ Ohne Kontext: Schwerer zu debuggen
reducer.apply(state, event)
```

### 4. Aussagekräftige Exception-Meldungen
```kotlin
// ✅ Gut: Kontextinformation
throw InvalidLobbyEventException(
    "TurnEnded von '$playerId' ist ungültig, aktiver Spieler ist " +
        "'${state.activePlayer}'."
)

// ❌ Zu kurz
throw InvalidLobbyEventException("Invalid turn")
```

### 5. Events nicht nach Netzwerk senden, bevor sie reduziert wurden

```kotlin
// ✅ Gut: Lokale Reduktion zuerst, dann senden
val newState = reducer.apply(state, event)
broadcastToOtherPlayers(event)

// ❌ Falsch: Event könnte lokal inkonsistent sein
broadcastToOtherPlayers(event)
val newState = reducer.apply(state, event) // Was wenn das fehlschlägt?
```

## Debugging

### Logging von Events

```kotlin
try {
    val newState = reducer.apply(state, event, context)
    logger.info("Event reduced: ${event::class.simpleName} on ${event.lobbyCode}")
} catch (e: InvalidLobbyEventException) {
    logger.error("Event failed: ${event::class.simpleName} - ${e.message}")
    throw e
}
```

### Zustandsübergänge nachverfolgbar machen

```kotlin
val previousStatus = state.status
val newState = reducer.apply(state, event)
val newStatus = newState.status

if (previousStatus != newStatus) {
    logger.info("Status transition: $previousStatus -> $newStatus")
}
```

### InvalidActionDetected Events analysieren

```kotlin
if (event is InvalidActionDetected) {
    logger.warn(
        "Invalid action by player ${event.playerId} in lobby ${event.lobbyCode}: " +
            event.reason
    )
    // Spieler-spezifisches Logging/Monitoring
}
```

## Migrationen und Erweiterungen

### Ein neuer Spielerstatus hinzufügen

Wenn du den GameState mit einem neuen Feld erweiterst:

1. **GameState aktualisieren**
   ```kotlin
   data class GameState(
       // ... existierende Properties ...
       val newProperty: SomeType,
   )
   ```

2. **Reducer-Logik entsprechend anpassen**
   ```kotlin
   private fun onPlayerJoined(
       state: GameState,
       playerId: PlayerId,
       playerDisplayName: String,
   ): GameState {
       return state.copy(
           // ... existiende Updates ...
           newProperty = calculateNewPropertyValue(state)
       )
   }
   ```

3. **Tests aktualisieren** - `createGameState()` muss den neuen Wert setzen

### Ein Subtyp eines bestehenden Events hinzufügen

Dies ist normalerweise nicht empfohlen, da Events bereits datengerecht sind:

```kotlin
// ❌ Nicht empfohlen
sealed interface PlayerActionEvent : ExternalLobbyEvent
data class AttackStarted(...) : PlayerActionEvent
data class AttackEnded(...) : PlayerActionEvent

// ✅ Besser: Separate Events
data class AttackStarted(...) : ExternalLobbyEvent
data class AttackEnded(...) : ExternalLobbyEvent
```

## FAQ

**F: Können Events nach ihrer Anwendung revert werden?**  
A: Nein. Das System ist append-only und event-gesteuert. Um einen State zu "reverten", müsste man den Event-Log von vorne neu reduzieren. Dies ist bewusst so designt, um ein Source-of-Truth zu haben.

**F: Wie können Events persistiert werden?**  
A: Das ist nicht Teil dieses Systems. Events können in eine Event-Sourcing-Datenbank geschrieben werden, z.B. in ein Append-Only-Log. Der Reducer arbeitet mit jedem beliebigen Event-Source.

**F: Was ist der Unterschied zwischen ExternalLobbyEvent und InternalLobbyEvent?**  
A: External = von außerhalb des Lobby-Systems ausgelöst (Spieler klickt auf Button). Internal = vom System selbst erzeugt (Timeout, Status-Change). Dies ermöglicht stabiles Pattern Matching auf der Root-Ebene.

**F: Sollen Events über das Netzwerk als JSON serialisiert werden?**  
A: Nein. Events sind Domain-Model und sollten nicht direkt serialisiert werden. Stattdessen gibt es NetworkMessagePayloads, die bewusst getrennt sind. Dies ermöglicht, das Domain-Model unabhängig von Transport-Details zu ändern.

**F: Was passiert, wenn zwei Events gleichzeitig eintreffen?**  
A: Der Reducer ist deterministisch, aber nicht threadsafe. Die Anwendung muss selbst Serialisierung erzwingen, z.B. durch einen EventQueue oder Mutex rund um den Reducer-Aufruf.

