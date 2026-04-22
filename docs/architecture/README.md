# Architektur-Dokumentation

Dieser Ordner beschreibt die aktuell implementierten Kernsysteme des Projekts. Die Dokumente orientieren sich am Code im aktuellen Branch und sind als technische Ergänzung zu KDoc/Dokka gedacht.

## Dokumente

- `lobby-event-system.md`
  Domain-Events, Reducer, GameState und Runtime-Verarbeitung.
- `map-config.md`
  Map-Definition, Loader, Validierung, Hashing und Server-Integration.
- `turn-state-machine.md`
  Turn-/Phasenlogik, Requests, Broadcasts und Pause/Resume-Regeln.
- `gamestate-versioning.md`
  `stateVersion`, Delta-Übertragung und 2-Runden-History-Buffer.
- `game-state-visibility.md`
  Öffentliche und private GameState-Payloads sowie Delivery-Guards.
- `network-api-integration.md`
  Technische Integration von Shared Codec, ServerNetwork, Router und App-Netzwerkpfad.
- `client-systems.md`
  Entwickleranleitung für die derzeit vorhandenen Android-Client-Systeme.

## Lesereihenfolge

Für einen Gesamteinstieg:

1. `lobby-event-system.md`
2. `map-config.md`
3. `turn-state-machine.md`
4. `gamestate-versioning.md`
5. `game-state-visibility.md`
6. `network-api-integration.md`
7. `client-systems.md`

## Status

- Die Server-Architektur ist für Lobby-, Turn- und GameState-Sync bereits weitgehend verdrahtet.
- Die App-Architektur deckt aktuell produktiv den Lobby-Flow ab.
- Gameplay-spezifische Client-State-Verarbeitung ist noch kein vollständiges System und wird in den einzelnen Dokumenten explizit als Lücke markiert.
