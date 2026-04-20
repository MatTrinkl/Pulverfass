# Turn State Machine

## Ziel

Der Turn-State einer Lobby wird serverseitig deterministisch geführt. Zu jedem Zeitpunkt gibt es höchstens einen aktiven Spieler und genau eine aktive Phase.

## Modell

- `TurnPhase` definiert die feste Phasenfolge:
  - `REINFORCEMENTS`
  - `ATTACK`
  - `FORTIFY`
  - `DRAW_CARD`
- `TurnState` hält:
  - `activePlayerId`
  - `turnPhase`
  - `turnCount`
  - `startPlayerId`
  - `isPaused`
  - `pauseReason`
  - `pausedPlayerId`

Der `GameState` enthält `turnState` als fachliche Quelle of truth. Die älteren Felder `activePlayer` und `turnNumber` bleiben vorerst als kompatible Spiegelwerte bestehen.

## Reihenfolge

- Die Spielerreihenfolge basiert deterministisch auf der stabilen `turnOrder` der Lobby.
- Die Phasenreihenfolge ist fest:
  - `REINFORCEMENTS -> ATTACK -> FORTIFY -> DRAW_CARD`
- Nach `DRAW_CARD` wird auf den nächsten Spieler gewechselt und die Phase auf `REINFORCEMENTS` zurückgesetzt.

## Rundenzähler

- `turnCount` startet bei `1`, sobald ein `TurnState` initialisiert ist.
- `turnCount` erhöht sich nur dann, wenn nach einem Spielerwechsel wieder `startPlayerId` aktiv wird.
- Ein bloßer Phasenwechsel innerhalb desselben Spielerzugs erhöht `turnCount` nicht.

## Spielstart / Turn-Init

- Beim `GameStarted`-Übergang wird der initiale `TurnState` deterministisch erzeugt.
- Initialwerte sind:
  - `activePlayerId = startPlayerId`
  - `turnPhase = REINFORCEMENTS`
  - `turnCount = 1`
  - `isPaused = false`
- Der Reducer bildet diesen Initialzustand intern als kombiniertes `TurnStateUpdatedEvent` ab, damit dieselbe atomare Update-Logik wie bei späteren Turn-Transitions verwendet wird.
- Der Server broadcastet den resultierenden `TurnStateUpdatedEvent` direkt an alle Lobby-Mitglieder, sobald sich der Turn-State durch den Spielstart geändert hat.

## Event-Semantik

- `TurnEnded` schaltet die Turn-State-Machine deterministisch weiter.
- Solange die aktuelle Phase nicht `DRAW_CARD` ist, bleibt der aktive Spieler gleich und nur die Phase wechselt.
- In `DRAW_CARD` beendet `TurnEnded` den Spielerzug vollständig und aktiviert den nächsten Spieler.
- Die eigentliche Mutation des Turn-States im `GameState` läuft ausschließlich über `TurnStateUpdatedEvent`.
- `TurnStateUpdatedEvent` aktualisiert `activePlayerId`, `turnPhase`, `turnCount`, `startPlayerId` sowie den Pause-Zustand atomar.

## Disconnect / Pause

- Der Server pausiert das Spiel nur dann, wenn ein disconnected Spieler gerade `activePlayerId` ist oder beim nächsten erfolgreichen Advance `activePlayerId` würde.
- Für diesen Fall wird der Turn-State mit
  - `isPaused = true`
  - `pauseReason = WAITING_FOR_PLAYER`
  - `pausedPlayerId = activePlayerId`
  aktualisiert.
- Solange `isPaused = true` ist, werden weitere `TurnAdvanceRequest`s serverseitig mit `GAME_PAUSED` abgewiesen.
- Sobald genau der wartende Spieler reconnectet, wird derselbe Turn-State per kombiniertem Update wieder mit `isPaused = false` fortgesetzt; `activePlayerId`, `turnPhase` und `turnCount` bleiben dabei unverändert.

## Integrierter Flow

```text
Lobby Setup
Host -> StartPlayerSetRequest
Server -> StartPlayerSetResponse (an Host)
Server -> TurnStateUpdatedEvent (an Lobby)

Game Start
Host -> StartGameRequest
Server -> StartGameResponse (an Host)
Server -> GameStartedEvent (an Lobby)
Server -> TurnStateUpdatedEvent(activePlayer=startPlayer, phase=REINFORCEMENTS, turnCount=1)

Turn Advance
Active Player -> TurnAdvanceRequest(expectedPhase=current)
Server validates requester, pause-state, expectedPhase
Server -> TurnAdvanceResponse (an Requester)
Server -> TurnStateUpdatedEvent (an Lobby)

Pause on Disconnect
Connection lost for active player or next would-be active player
Server -> TurnStateUpdatedEvent(isPaused=true, pauseReason=WAITING_FOR_PLAYER, pausedPlayerId=activePlayerId)

Resume on Reconnect
Paused player reconnects
Server -> TurnStateUpdatedEvent(isPaused=false, pausedPlayerId=null)
```

## Router / Server

- `MainServerLobbyRoutingService` behandelt `StartPlayerSetRequest`, `StartGameRequest`, `TurnAdvanceRequest` und `TurnStateGetRequest` direkt auf der Routing-Ebene.
- Turn-State-Broadcasts werden zentral nur dann gesendet, wenn sich der autoritative `TurnState` einer Lobby tatsächlich geändert hat.
- Die Logging-Ausgaben auf Server-Seite protokollieren bei jedem Turn-State-Wechsel mindestens:
  - `lobbyCode`
  - `activePlayerId`
  - `turnPhase`
  - `turnCount`

## Legacy-Kompatibilität

- Falls ältere Tests oder Call-Sites noch keinen expliziten `turnState` setzen, kann `GameState.resolvedTurnState` vorübergehend aus `activePlayer`, `turnOrder` und `turnNumber` einen Fallback ableiten.
- Neue Logik soll ausschließlich `turnState` bzw. `resolvedTurnState` verwenden.
