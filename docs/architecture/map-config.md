# Map Config

Die Map-Definition liegt im `shared` als JSON-Konfiguration und wird zur Laufzeit geladen und validiert.

## Speicherort

- Default-Datei: `shared/src/main/resources/config/maps/default-map.json`
- Loader: `at.aau.pulverfass.shared.map.config.MapConfigLoader`
- Validator: `at.aau.pulverfass.shared.map.config.MapConfigValidator`

## Schema

```json
{
  "schemaVersion": 1,
  "territories": [
    {
      "territoryId": "north_harbor",
      "edges": [
        { "targetId": "west_gate" },
        { "targetId": "south_bay" }
      ]
    }
  ],
  "continents": [
    {
      "continentId": "north_realm",
      "territoryIds": ["north_harbor"],
      "bonusValue": 2
    }
  ]
}
```

## Felder

- `schemaVersion`: Muss aktuell `1` sein.
- `territories`: Liste aller Territorien.
- `territoryId`: Fachliche ID, Kleinbuchstaben mit optional `_` oder `-`.
- `edges`: Nachbarschaften eines Territoriums.
- `targetId`: Ziel-Territorium einer gerichteten Kante.
- `continents`: Liste aller Kontinente.
- `continentId`: Fachliche ID des Kontinents.
- `territoryIds`: Territorien des Kontinents.
- `bonusValue`: Bonus bei vollständiger Kontrolle des Kontinents.

## Legacy-Kompatibilität

Übergangsweise akzeptiert der Loader zusätzlich das Legacy-Feld `adjacentTerritoryIds` pro Territory:

```json
{
  "territoryId": "north_harbor",
  "adjacentTerritoryIds": ["west_gate", "south_bay"]
}
```

Alle Legacy-Einträge werden intern zu normalen `edges` normalisiert.

## Validierungsregeln

- `territoryId` und `continentId` müssen eindeutig sein.
- Alle referenzierten Ziel-Territorien müssen existieren.
- Kanten müssen symmetrisch sein.
- Pro Ziel darf ein Territory höchstens eine Kante definieren.
- Kontinente dürfen nur existierende Territorien referenzieren.
- Ein Territory darf höchstens einem Kontinent zugeordnet sein.

Bei Verstößen bricht der Loader deterministisch mit einer `MapConfigValidationException` ab.

## Runtime-Integration

- Der Server lädt die Default-Map beim Startup zentral über `ClasspathMapDefinitionRepository`.
- `Application.installLobbyRuntime(...)` injiziert diese Definition in den `LobbyManager`.
- Neue Lobbys starten dadurch deterministisch mit `GameState.initial(lobbyCode, mapDefinition)`.
- `territoryStates` enthalten nur den mutierbaren Zustand (`ownerId`, `troopCount`).
- Adjacency bleibt readonly in der `MapDefinition` und wird im Runtime-Code ausschließlich über `edges` abgefragt.
- `MapGetResponse` trennt Definition (`definition`) und Zustand (`territoryStates`) explizit.

## Kantenmodell

- Das Runtime-Modell verwendet untypisierte `edges`.
- Das Legacy-Feld `adjacentTerritoryIds` dient nur noch als Loader-Kompatibilität und wird beim Laden zu `edges` normalisiert.
- Es gibt aktuell bewusst keinen `EdgeType`; ältere Anforderungen dazu sind durch die spätere Modellentscheidung ersetzt.

## Versioning und Hashing

- Jede geladene `MapDefinition` besitzt einen abgeleiteten `MapIdentifier`.
- Aktuell ist der fachliche Identifier der stabile `mapHash` auf Basis von SHA-256.
- Der Hash wird aus einer kanonischen JSON-Repräsentation der validierten Definition berechnet.
- Die Kanonisierung ist unabhängig von Whitespace und der Feldreihenfolge im Roh-JSON.
- Für den Hash werden `schemaVersion`, alle `territories`, deren `edges` sowie alle `continents` berücksichtigt.
- Die kanonische Repräsentation sortiert Territorien nach `territoryId`, Kanten nach `targetId`, Kontinente nach `continentId` und `territoryIds` innerhalb eines Kontinents lexikografisch.
- `MapGetResponse` liefert `schemaVersion`, `mapHash` und `stateVersion`, sodass Clients Definitionen eindeutig vergleichen und autoritative Snapshots einem konkreten State-Stand zuordnen können.

## Reconnect

- Der minimale Reconnect-Pfad läuft aktuell über einen vollständigen Snapshot via `MapGetRequest` / `MapGetResponse`.
- `stateVersion` entspricht der Anzahl der bereits angewendeten Lobby-Events (`GameState.processedEventCount`).
- Delta-Broadcasts für Map-Events tragen ebenfalls `stateVersion`, damit Clients Reihenfolge und Snapshot-Stände vergleichen können.
- Ein Catch-up per Event-Replay seit Version ist noch nicht implementiert; bei Reconnect ist der Snapshot die autoritative Quelle.
