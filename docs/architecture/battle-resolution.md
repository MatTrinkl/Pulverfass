# Battle Resolution

## Zweck

Dieses Dokument ist die technische Source of Truth fuer die deterministische Kampfaufloesung in Phase `ATTACK`.

Es ersetzt die bisher offene Placeholder-Annahme, dass `AttackCommand` bereits von aussen mit fertigen Verlustzahlen befuellt wird. Ab jetzt gilt:

- die Kampfaufloesung ist fachlich final definiert
- die Resolver-Implementierung muss exakt dieser Spezifikation folgen
- Golden Vectors und Determinism-Tests pruefen gegen dieses Dokument
- nur der Server darf RNG-Wuerfe ausfuehren und daraus Battle Outcomes ableiten

## Fachliche Entscheidung

Ein Angriff wird als klassische, Risk-aehnliche Würfelrunde aufgeloest:

- Angreifer wuerfelt mit `1..3` Angriffswuerfeln
- Verteidiger wuerfelt mit `1..2` Verteidigungswuerfeln
- beide Seiten sortieren ihre Wuerfel absteigend
- es werden die staerksten Paare verglichen
- bei Gleichstand gewinnt der Verteidiger das Paar

Ein `Attack`-Intent loest genau **eine** Kampf-Runde auf, nicht automatisch eine ganze Angriffskette.

## Autoritaet und Trust Boundary

Die Kampfaufloesung ist **ausschliesslich serverautoritativ**.

Verbindliche Regel:

- nur der Server darf die deterministischen RNG-Wuerfe erzeugen
- nur der Server darf `attackerLosses`, `defenderLosses`, `capture` und `minOccupyingTroops` ableiten
- der Client darf keine Wuerfel werfen
- der Client darf keine Verlustzahlen berechnen oder vorschlagen
- der Client darf keinen RNG-Seed fortschreiben oder einen verdeckten RNG-Zustand halten

Der Client darf ausschliesslich:

- einen Angriffs-Intent absenden
- die fuer genau eine Angriffsaktion eingesetzten `attackTroops` angeben
- mit dem Angriff eine gewuenschte `moveAfterCapture`-Verschiebung mitsenden, die der Server nur im Capture-Fall anwendet
- das serverseitig aufgeloeste Ergebnis darstellen

Damit ist ausgeschlossen, dass manipulierte oder divergierende Client-Berechnungen die Kampfaufloesung beeinflussen.

## Formale Regel

### Eingaben pro Kampf-Runde

Gegeben seien:

- `S`: Truppen im angreifenden Gebiet vor dem Angriff
- `T`: Truppen im verteidigenden Gebiet vor dem Angriff
- `C`: vom Angreifer fuer diese Aktion committe Truppen (`attackTroops`, `C >= 2`)
- `R`: daraus abgeleitete angeforderte Angriffswuerfel
- `seed`: `gameRandomSeed` des aktuellen Spiels
- `rngState`: persistierter RNG-Cursor des aktuellen Spiels **vor** Anwendung des Angriffs

Vorbedingungen:

- `S >= 2`
- `T >= 1`
- `C >= 2`
- `S >= C + 1`

API-Ableitung:

`R = min(3, C)`

### Tatsächliche Wuerfelanzahl

Die effektive Angriffswuerfelzahl ist:

`A = min(R, 3, S - 1)`

Die effektive Verteidigungswuerfelzahl ist:

`D = min(2, T)`

Die Zahl der Vergleiche ist:

`K = min(A, D)`

### Deterministisches RNG-System pro Game

Das Spiel besitzt genau einen serverautoritativ gefuehrten RNG-Zustand:

- `rngSeed`: stabiler Seed des Spiels
- `rngState`: aktueller Cursor vor dem naechsten Draw

Diese Werte leben im `GameState`. Nur der Server darf `rngState` fortschreiben.

Die Kampf-Runde verwendet als Initialzustand fuer den Resolver:

`rngState0 = rngState`

RNG-Algorithmus pro gezogenem Wert: `SplitMix64`

```text
const GAMMA = 0x9E3779B97F4A7C15
const M1    = 0xBF58476D1CE4E5B9
const M2    = 0x94D049BB133111EB

next(state):
    state = state + GAMMA                       // 64-bit wraparound
    z = state
    z = (z xor (z >>> 30)) * M1                // 64-bit wraparound
    z = (z xor (z >>> 27)) * M2                // 64-bit wraparound
    z = z xor (z >>> 31)
    return (state, z)
```

`d6`-Ableitung:

`die = 1 + (z mod 6)`

Dabei ist `mod` als nicht-negativer Rest auf dem 64-bit-Output zu verstehen.

### Zieh-Reihenfolge

Pro Kampf-Runde werden genau `A + D` Zufallswerte konsumiert:

1. zuerst `A` Angriffswuerfel in Eingabereihenfolge
2. danach `D` Verteidigungswuerfel in Eingabereihenfolge

Es duerfen keine weiteren RNG-Zugriffe fuer Animation, Logging, Sorting oder Tiebreaks stattfinden.

Nach der Kampf-Runde wird der zuletzt fortgeschriebene Cursor als neues
`rngState` des `GameState` persistiert.

### Vergleichsregel

Seien:

- `a1 >= a2 >= a3` die absteigend sortierten Angriffswuerfel
- `d1 >= d2` die absteigend sortierten Verteidigungswuerfel

Dann werden fuer `i = 1..K` die Paare `(ai, di)` verglichen.

Verlustregel pro Paar:

- falls `ai > di`: Verteidiger verliert 1 Truppe
- sonst: Angreifer verliert 1 Truppe

Mathematisch:

`attackerLosses = sum(i = 1..K) [ai <= di]`

`defenderLosses = sum(i = 1..K) [ai > di]`

mit:

`attackerLosses + defenderLosses = K`

### Resultierender Zustand

Nach der Kampf-Runde gilt:

- `S' = S - attackerLosses`
- `T' = T - defenderLosses`

Capture liegt genau dann vor, wenn:

`T' = 0`

### Besetzung nach Capture

Bei erfolgreicher Eroberung muss der Angreifer mindestens so viele Truppen ins Zielgebiet verschieben, wie er in der **letzten erfolgreichen Kampf-Runde** als Angriffswuerfel benutzt hat.

Also:

`minOccupyingTroops = A`

Zulaessiger Besetzungsbereich:

`occupyingTroops in [A, S' - 1]`

Es muss also immer mindestens eine Truppe im Ursprungsgebiet verbleiben.

API-Regel fuer Phase 2:

- der Client sendet `moveAfterCapture`
- der Server verwendet diesen Wert nur dann, wenn `T' = 0`
- sonst wird `moveAfterCapture` ignoriert
- im Capture-Fall gilt serverseitig:
  - `moveAfterCapture >= minOccupyingTroops`
  - `moveAfterCapture <= S' - 1`

## Balancing-Rationale

- Die Regel ist fuer Spieler sofort lesbar und an das bekannte Risiko-Modell angelehnt.
- Der Angreifer bekommt durch bis zu 3 Wuerfel einen kontrollierbaren Vorteil.
- Der Verteidiger behaelt durch Gleichstandsgewinn eine stabile Gegenwehr fuer starke Defensivpositionen.
- Maximal 2 Verluste pro Runde begrenzen die Varianz und machen Frontlinien berechenbarer als eine offene Prozentformel.
- Der persistierte RNG-Cursor ist replay-, reconnect- und persistence-freundlich, weil die RNG-Fortschreibung explizit im Eventlog und Snapshot-Zustand liegt.

## Integrationsfolgen

### Angriffskommando

Die aktuelle Form in
[`MapCommand.kt`](../../shared/src/main/kotlin/at/aau/pulverfass/shared/lobby/command/MapCommand.kt)
modelliert den Angriff nur noch als fachlichen Intent:

- `fromTerritoryId`
- `toTerritoryId`
- `requestedAttackDice`
- optional `committedTroopCount`
- optional `occupyingTroopCount`

`attackerLosses`, `defenderLosses`, Rohwuerfel und RNG-Fortschritte sind kein
Teil des Client-Requests mehr. Diese Werte entstehen ausschliesslich
serverseitig im Battle-Resolver auf Basis von `gameRandomSeed`,
`gameRandomState` und den fachlichen Eingaben des Angriffs.

### Minimale Resolver-Ausgabe

Die Resolver-Schicht muss mindestens liefern:

- effektive Angriffswuerfel `A`
- effektive Verteidigungswuerfel `D`
- geworfene Angriffswuerfel
- geworfene Verteidigungswuerfel
- `attackerLosses`
- `defenderLosses`
- `capture`
- `minOccupyingTroops` bei Capture

## Acceptance Criteria

- Battle Outcomes entsprechen exakt der oben definierten Formel.
- Gleichstand zaehlt immer als Verlust des Angreifers.
- Eine Kampf-Runde konsumiert exakt `A + D` RNG-Werte in der festgelegten Reihenfolge.
- RNG-Wuerfe werden ausschliesslich serverseitig erzeugt.
- Client-Nachrichten enthalten keinen voraufgeloesten Kampf-Output.
- Determinism-Tests bleiben gruen.
- Golden Vectors muessen unveraendert reproduzierbar sein.

## Golden Test Vectors

Die folgenden Vektoren sind verbindlich fuer Resolver- und Determinism-Tests.

Hinweis:

- die folgenden Vektoren beschreiben die Resolver-Ergebnisse
- im laufenden Spiel wird der Startzustand dafuer explizit als `rngStateBefore`
  im `GameState` und im `AttackResolvedEvent` getragen
- die hier frueher verwendete Ableitung `seed xor stateVersion` kann als
  Initialisierungspolitik fuer `rngStateBefore` dienen, ist aber nicht mehr der
  implizite Laufzeitmechanismus

### Vektor 1: Capture mit 3 gegen 2 Wuerfel

- `seed = 0x0000000000000001`
- `stateVersion = 3`
- `rngStateBefore = 0x0000000000000002`
- `sourceTroops = 5`
- `targetTroops = 2`
- `requestedAttackDice = 3`

Erwartet:

- `attackDice = 3`
- `defendDice = 2`
- `attackerRaw = [5, 3, 4]`
- `defenderRaw = [1, 2]`
- `attackerSorted = [5, 4, 3]`
- `defenderSorted = [2, 1]`
- `attackerLosses = 0`
- `defenderLosses = 2`
- `capture = true`
- `minOccupyingTroops = 3`

### Vektor 2: Gleichstand bevoorteilt den Verteidiger

- `seed = 0x0000000000000001`
- `stateVersion = 17`
- `rngStateBefore = 0x0000000000000010`
- `sourceTroops = 5`
- `targetTroops = 2`
- `requestedAttackDice = 3`

Erwartet:

- `attackDice = 3`
- `defendDice = 2`
- `attackerRaw = [6, 2, 5]`
- `defenderRaw = [5, 5]`
- `attackerSorted = [6, 5, 2]`
- `defenderSorted = [5, 5]`
- `attackerLosses = 1`
- `defenderLosses = 1`
- `capture = false`

### Vektor 3: Verteidiger verliert zwei Truppen ohne Capture

- `seed = 0x0000000000000001`
- `stateVersion = 18`
- `rngStateBefore = 0x0000000000000013`
- `sourceTroops = 5`
- `targetTroops = 4`
- `requestedAttackDice = 3`

Erwartet:

- `attackDice = 3`
- `defendDice = 2`
- `attackerRaw = [1, 3, 6]`
- `defenderRaw = [2, 1]`
- `attackerSorted = [6, 3, 1]`
- `defenderSorted = [2, 1]`
- `attackerLosses = 0`
- `defenderLosses = 2`
- `capture = false`

### Vektor 4: Verteidiger mit nur einer Truppe verteidigt mit genau einem Wuerfel

- `seed = 0x000000000000002A`
- `stateVersion = 41`
- `rngStateBefore = 0x0000000000000003`
- `sourceTroops = 4`
- `targetTroops = 1`
- `requestedAttackDice = 3`

Erwartet:

- `attackDice = 3`
- `defendDice = 1`
- `attackerRaw = [4, 4, 4]`
- `defenderRaw = [6]`
- `attackerSorted = [4, 4, 4]`
- `defenderSorted = [6]`
- `attackerLosses = 1`
- `defenderLosses = 0`
- `capture = false`

### Vektor 5: Angriffswuerfel werden durch `S - 1` gecappt

- `seed = 0x123456789ABCDEF0`
- `stateVersion = 7`
- `rngStateBefore = 0x123456789ABCDEF7`
- `sourceTroops = 3`
- `targetTroops = 2`
- `requestedAttackDice = 3`

Erwartet:

- `attackDice = 2`
- `defendDice = 2`
- `attackerRaw = [1, 6]`
- `defenderRaw = [5, 5]`
- `attackerSorted = [6, 1]`
- `defenderSorted = [5, 5]`
- `attackerLosses = 1`
- `defenderLosses = 1`
- `capture = false`

### Vektor 6: Stabile Mehrfachrunde mit grossem Seed

- `seed = 0xCAFEBABEDEADBEEF`
- `stateVersion = 1234`
- `rngStateBefore = 0xCAFEBABEDEADBA3D`
- `sourceTroops = 8`
- `targetTroops = 6`
- `requestedAttackDice = 3`

Erwartet:

- `attackDice = 3`
- `defendDice = 2`
- `attackerRaw = [5, 2, 4]`
- `defenderRaw = [4, 1]`
- `attackerSorted = [5, 4, 2]`
- `defenderSorted = [4, 1]`
- `attackerLosses = 0`
- `defenderLosses = 2`
- `capture = false`

## Folgearbeiten

Wenn die Resolver-Implementierung startet, soll sie sich in dieser Reihenfolge an diesem Dokument ausrichten:

1. deterministische RNG-Hilfsfunktion
2. Battle-Resolver fuer genau eine Kampf-Runde
3. Golden-Vector-Tests
4. Ersatz des Placeholders in der bestehenden Attack-Pipeline
