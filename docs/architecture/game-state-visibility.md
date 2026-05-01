# GameState Visibility

## Ziel

GameState-Daten werden technisch in zwei Sichtbarkeitsklassen getrennt:

- `PublicGameStatePayload`: darf als S2L an alle Lobby-Mitglieder gesendet werden
- `PrivateGameStatePayload`: darf nur als S2C an `recipientPlayerId` gesendet werden

Damit ist die Delivery-Regel nicht mehr nur Konvention, sondern Teil der API.

## Shared Marker-Typen

Im Shared-Layer gelten folgende Marker:

- `PublicGameStatePayload`
- `PrivateGameStatePayload`
- `PublicGameEvent`
- `PrivateGameEvent`

Aktuell sind insbesondere diese Payloads öffentlich klassifiziert:

- `GameStateDeltaEvent`
- `GameStateSnapshotBroadcast`
- `GameStateCatchUpResponse`
- `MapGetResponse`
- `TurnStateGetResponse`
- öffentliche GameState-Events wie `TurnStateUpdatedEvent`, `TerritoryOwnerChangedEvent`, `TerritoryTroopsChangedEvent`, `GameStartedEvent`

Aktuell ist insbesondere diese Payload privat klassifiziert:

- `GameStatePrivateGetResponse`

## Server Delivery

Der Server nutzt für autoritative GameState-Nachrichten den `GameStateDeliveryDispatcher`.
Die öffentliche Projektion aus dem fachlichen `GameState` läuft zentral über den `PublicGameStateBuilder`.

Regeln:

- `PublicGameStateBuilder` ist die einzige Quelle für öffentliche Full-Snapshots und öffentliche Delta-Payloads
- öffentliche Snapshots gehen per `sendPublicState(...)` an genau eine Connection
- öffentliche Deltas/Broadcasts gehen per `broadcastPublicState(...)` oder `broadcastPublicDelta(...)` an alle Lobby-Mitglieder
- private Payloads gehen entweder per `sendPrivateState(connectionId, ...)` als autorisierte direkte Antwort oder per `sendPrivateState(lobbyCode, ...)` an den in der Payload enthaltenen Empfänger
- `sendPrivateState(...)` validiert zusätzlich, dass `recipientPlayerId` Mitglied der Lobby ist

## Leak-Guards

- `GameStateDeltaEvent` akzeptiert ausschließlich `List<PublicGameEvent>`
- private Payloads haben keinen Broadcast-Pfad im Dispatcher
- zukünftige private Snapshots oder Events müssen `PrivateGameStatePayload` bzw. `PrivateGameEvent` implementieren

Dadurch wird es technisch unkomfortabel, private Daten versehentlich in öffentliche Deltas oder Broadcasts einzuschleusen.
