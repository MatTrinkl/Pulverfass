# GameState Versioning

`stateVersion` ist die server-authoritative, monotone Revision eines `GameState` pro Lobby.

## Regeln

- Initialer Wert ist `0`.
- Jede erfolgreiche State-Änderung über den Reducer erhöht `stateVersion` deterministisch um `1`.
- Clients verwenden `stateVersion`, um Snapshots und Delta-Events zu ordnen und Lücken zu erkennen.
- `stateVersion` ist Teil des `GameState` selbst und kein abgeleiteter Hilfswert mehr.

## Abgrenzung zu `processedEventCount`

- `processedEventCount` bleibt als technischer Zähler im Runtime-System erhalten.
- Beide Werte steigen im aktuellen System gemeinsam an.
- Fachlich maßgeblich für Snapshot-/Delta-Konsistenz ist ausschließlich `stateVersion`.

## Relevante Payloads

- `MapGetResponse.stateVersion`
- `GameStateCatchUpResponse.stateVersion`
- `GameStatePrivateGetResponse.stateVersion`
- `GameStateDeltaEvent.fromVersion` / `toVersion`
- `PhaseBoundaryEvent.stateVersion`
- `GameStateSnapshotBroadcast.stateVersion`
- Map-Delta-Events wie `TerritoryOwnerChangedEvent.stateVersion`
- spätere Replay-/Catch-up-Nachrichten können dieselbe Revision verwenden

## Delta-Sync

- Nach jeder akzeptierten öffentlichen GameState-Änderung sendet der Server ein `GameStateDeltaEvent` als S2L-Broadcast.
- `fromVersion` und `toVersion` markieren den autoritativen Versionsbereich des Deltas.
- Die enthaltenen `events` sind in deterministischer Reihenfolge anzuwenden.
- Bestehende granularere Broadcasts können während der Übergangsphase parallel existieren; die Delta-Nachricht ist der primäre Sync-Pfad.

## Round-History Buffer

- Der Server hält pro Lobby einen flüchtigen `RoundHistoryBuffer` für maximal die letzten `2` Runden.
- Gespeichert werden nur öffentliche Observability-Metadaten:
- `GameStateDeltaEvent`-Metadaten (`fromVersion`, `toVersion`, Event-Anzahl)
- `PhaseBoundaryEvent`
- `TurnStateUpdatedEvent` inklusive zugehöriger `stateVersion`
- Snapshot-Metadaten für `GameStateSnapshotBroadcast` und `GameStateCatchUpResponse`
- Private Daten werden bewusst nicht im Buffer abgelegt.
- Beim Eintritt in eine dritte neuere Runde wird die älteste Runde deterministisch entfernt.
- Der Buffer dient Debugging/Diagnose und ist Grundlage für spätere Erweiterungen wie `events-since`, bleibt aber bewusst nicht persistent.
