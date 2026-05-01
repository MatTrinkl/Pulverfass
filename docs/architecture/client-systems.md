# Client-Systeme

## Ziel und Scope

Dieses Dokument beschreibt die aktuell im Android-Modul `:app` vorhandenen Client-Systeme, ihre Verantwortlichkeiten, ihre Initialisierung und den derzeit produktiv nutzbaren Umfang.

Wichtig:
- Der technische Netzwerkpfad ist produktiv vorhanden.
- Der fachliche Client-State ist aktuell im Wesentlichen auf den Lobby-Flow beschränkt.
- Gameplay-spezifische Verarbeitung von Deltas, Snapshots und Turn-/Map-State ist noch nicht als eigenes Client-System implementiert.

## Überblick

Die produktive Client-Kette ist aktuell:

```text
MainActivity
  -> LobbyController
    -> ClientNetwork
      -> AndroidWebSocketTransport
      -> PacketReceiver
      -> PacketSender
```

Darauf setzt die Compose-UI mit `LobbyScreen` und `WaitingRoomScreen` auf.

## Systeme im Detail

### 1. `MainActivity`

Pfad:
- `app/src/main/kotlin/at/aau/pulverfass/app/MainActivity.kt`

Verantwortung:
- Einstiegspunkt der Android-App
- initialisiert Navigation und genau eine `LobbyController`-Instanz
- reicht dieselbe Controller-Instanz an Lobby- und Waiting-Room-Screens weiter
- schließt den Controller via `DisposableEffect`, wenn die Composition verlassen wird

Aktueller Navigationsfluss:
- `LoadScreen`
- `LobbyScreen`
- `WaitingRoomScreen`
- `GameScreen`

Hinweis:
- `GameScreen` ist derzeit nur ein UI-Platzhalter und konsumiert noch keinen autoritativen GameState.

### 2. `AndroidWebSocketTransport`

Pfad:
- `app/src/main/kotlin/at/aau/pulverfass/app/network/transport/AndroidWebSocketTransport.kt`

Verantwortung:
- verwaltet die tatsächliche Ktor-WebSocket-Verbindung
- sendet rohe `ByteArray`s als Binary-Frames
- emittiert technische `TransportEvent`s:
  - `Connected`
  - `BinaryMessageReceived`
  - `Disconnected`
  - `TransportError`

Initialisierung:
- wird standardmäßig durch `ClientNetwork` erzeugt
- kann in Tests oder Spezialfällen injiziert werden

Wichtige Invarianten:
- der Client verwaltet lokal genau eine aktive Verbindung
- Text-Frames werden nicht als produktiver Protokollpfad unterstützt
- `connect()` trennt eine bestehende Verbindung vor einem Reconnect

Typischer Einsatz:

```kotlin
val network = ClientNetwork(scope)
network.connect("ws://10.0.2.2:8080/ws")
```

### 3. `PacketReceiver`

Pfad:
- `app/src/main/kotlin/at/aau/pulverfass/app/network/receive/PacketReceiver.kt`

Verantwortung:
- nimmt technische `TransportEvent`s entgegen
- dekodiert Binary-Nachrichten bis zum `ReceivedPacket`
- emittiert:
  - `packets: SharedFlow<ReceivedPacket>`
  - `errors: SharedFlow<PacketReceiveException>`

Abhängigkeiten:
- `PacketReceiveAdapter` aus `:shared`

Wichtig:
- `PacketReceiver` dekodiert bewusst nur bis zum `MessageHeader` und Payload-JSON
- das Mapping von `ReceivedPacket` auf konkrete `NetworkMessagePayload`s passiert in höheren Schichten, aktuell im `LobbyController`

### 4. `PacketSender`

Pfad:
- `app/src/main/kotlin/at/aau/pulverfass/app/network/send/PacketSender.kt`

Verantwortung:
- sendet rohe Byte-Pakete an den `AndroidWebSocketTransport`

Invariante:
- es wird nur die konstante `CLIENT_CONNECTION_ID` unterstützt

### 5. `ClientNetwork`

Pfad:
- `app/src/main/kotlin/at/aau/pulverfass/app/network/ClientNetwork.kt`

Verantwortung:
- komponiert den technischen Client-Netzwerkstack
- verbindet Transport, Paketempfang und Paketversand
- stellt eine kleine High-Level-API für App-Schichten bereit:
  - `connect(serverUrl)`
  - `disconnect(reason)`
  - `sendPayload(payload)`
  - `close()`

Wichtig:
- `ClientNetwork` implementiert aktuell nicht direkt das `shared.network.Network`-Interface
- stattdessen kapselt es die für die Android-App benötigten technischen Teile
- `sendPayload` serialisiert Payloads mit `MessageCodec.encode(...)`

Lifecycle:
- die aufrufende Schicht muss einen `CoroutineScope` bereitstellen
- `close()` schließt Ressourcen des Transports, beendet aber nicht automatisch jede fachliche State-Schicht oberhalb

### 6. `LobbyController`

Pfad:
- `app/src/main/kotlin/at/aau/pulverfass/app/lobby/LobbyController.kt`

Verantwortung:
- UI-nahe Orchestrierung des aktuellen Client-Verhaltens
- hält den `LobbyUiState`
- reagiert auf Transportzustand und eingehende Pakete
- kapselt die derzeit vorhandenen fachlichen Client-Aktionen:
  - `connect()`
  - `disconnect()`
  - `createLobby(...)`
  - `joinLobby(...)`
  - `leaveLobby()`
  - Eingabefeld-Updates für Server-URL, Spielername und Lobbycode

Aktuell verarbeitete Payloads:
- `CreateLobbyResponse`
- `CreateLobbyErrorResponse`
- `JoinLobbyResponse`
- `JoinLobbyErrorResponse`
- `PlayerJoinedLobbyEvent`
- `PlayerLeftLobbyEvent`
- `PlayerKickedLobbyEvent`

Nicht verarbeitet, obwohl technisch empfangbar:
- `MapGetResponse`
- `TurnStateGetResponse`
- `GameStateDeltaEvent`
- `PhaseBoundaryEvent`
- `GameStateSnapshotBroadcast`
- `GameStateCatchUpResponse`
- `GameStatePrivateGetResponse`

Das ist eine bewusste aktuelle Produktgrenze der App.

## Initialisierung und Verwendung

Die aktuelle produktive Initialisierung passiert in `MainActivity`:

```kotlin
val lobbyController = remember { LobbyController() }

DisposableEffect(Unit) {
    onDispose {
        lobbyController.close()
    }
}
```

Der Controller wird dann an `LobbyScreen` und `WaitingRoomScreen` weitergereicht.

### Create-Lobby-Flow

1. `LobbyScreen` ruft `controller.createLobby { ... }` auf
2. `LobbyController` stellt bei Bedarf zuerst die Verbindung her
3. danach sendet der Controller `CreateLobbyRequest`
4. bei `CreateLobbyResponse` sendet der Controller automatisch `JoinLobbyRequest`
5. bei `JoinLobbyResponse` aktualisiert der Controller den State und ruft den Navigation-Callback auf

### Join-Lobby-Flow

1. `LobbyScreen` ruft `controller.joinLobby { ... }` auf
2. der Controller validiert Spielername und Lobbycode
3. bei Bedarf wird zuerst verbunden
4. danach wird `JoinLobbyRequest` gesendet
5. `JoinLobbyResponse` aktualisiert `activeLobbyCode` und löst Navigation aus

### Leave-Lobby-Flow

1. `WaitingRoomScreen` ruft `controller.leaveLobby()` auf
2. der Controller sendet `LeaveLobbyRequest`
3. lokaler Lobby-State wird sofort zurückgesetzt
4. die UI navigiert zurück

## Reihenfolge und Lifecycle-Anforderungen

- Ein `LobbyController` sollte pro UI-Fluss genau einmal erzeugt und wiederverwendet werden.
- `connect()` ohne Spielernamen ist absichtlich blockiert.
- `createLobby()` und `joinLobby()` dürfen vor `connect()` aufgerufen werden; der Controller übernimmt das Auto-Connect.
- Die Verbindung sollte über `close()` beim Verlassen der App-/Feature-Instanz freigegeben werden.
- Der Controller erwartet, dass eingehende Pakete in derselben Instanz verarbeitet werden, die auch die Requests gesendet hat.

## Abhängigkeiten zwischen den Systemen

### Technische Abhängigkeiten

- `LobbyController` hängt von `ClientNetwork` ab
- `ClientNetwork` hängt von `AndroidWebSocketTransport`, `PacketReceiver` und `PacketSender` ab
- `PacketReceiver` und `PacketSender` hängen von Shared-Codecs und Transporttypen aus `:shared` ab

### Fachliche Abhängigkeiten

- Die App nutzt aktuell nur Lobby-bezogene Requests/Responses und Player-Events produktiv
- Für GameState-Sync fehlen noch dedizierte Client-Controller oder Store-Systeme

## Erweiterungspunkte

### Neue Payload im Client konsumieren

Wenn eine neue Server-Nachricht im Client fachlich genutzt werden soll:

1. Nachricht im `LobbyController.handlePayload(...)` oder in einer neuen darüberliegenden State-Schicht behandeln
2. `LobbyUiState` oder einen neuen fachlichen State-Typ erweitern
3. UI-Komponenten an den neuen State anbinden
4. Tests für Happy Path und Fehlerpfade ergänzen

Für GameState-Sync ist mittelfristig empfehlenswert:
- ein eigener Client-Store oder Controller für autoritative Snapshots und Deltas
- klare Trennung zwischen technischem Empfang und fachlicher State-Anwendung
- explizite Behandlung von `stateVersion` und Desync-Fällen

### Neue Client-Systeme hinzufügen

Neue fachliche Systeme sollten nicht direkt im `AndroidWebSocketTransport` oder `PacketReceiver` landen. Die empfohlene Schichtung ist:

1. technische Transport-/Paketebene
2. fachliche Controller/Store-Ebene
3. UI-Bindung

## Tests

Vorhandene Tests:

- `app/src/test/kotlin/at/aau/pulverfass/app/network/ClientNetworkTest.kt`
- `app/src/test/kotlin/at/aau/pulverfass/app/network/transport/AndroidWebSocketTransportTest.kt`
- `app/src/test/kotlin/at/aau/pulverfass/app/network/receive/PacketReceiverTest.kt`
- `app/src/test/kotlin/at/aau/pulverfass/app/network/send/PacketSenderTest.kt`
- `app/src/test/kotlin/at/aau/pulverfass/app/lobby/LobbyControllerTest.kt`

Was diese Tests aktuell belegen:

- Verbindungsaufbau und Fehlerverhalten des technischen Client-Netzwerks
- Dekodierung eingehender Pakete
- Sende-Invarianten der Single-Connection-Pipeline
- produktiver Create-/Join-Lobby-Flow des `LobbyController`

## Produktiver Stand vs. Lücken

### Produktiv nutzbar

- WebSocket-Verbindung zum Server
- Versenden beliebiger `NetworkMessagePayload`s
- Empfangen und Dekodieren technischer Pakete
- Lobby-Create/Join/Leave-Flow
- Anzeige einer einfachen Spielerliste im Waiting Room

### Unvollständig oder Platzhalter

- `GameScreen` ist ein Platzhalter
- kein Client-Store für Map-/Turn-State
- keine Anwendung von Delta-Sync
- kein clientseitiges Catch-up-/Reconnect-System
- keine private State-Darstellung jenseits des Transportpfads

## Typische Fallstricke

- `LobbyController` verarbeitet nur einen Teil der inzwischen existierenden Server-Payloads; neue GameState-Nachrichten werden sonst still ignoriert.
- `sendPayload(...)` ohne aktive WebSocket-Verbindung schlägt fehl.
- `leaveLobby()` setzt den lokalen Lobby-State sofort zurück; wenn künftig serverautoritatives Leave-Handling benötigt wird, muss dieser Pfad ggf. nachgeschärft werden.
- `LobbyControllerConfig` enthält UI-Texte und Infrastrukturparameter gemeinsam; für größere Features könnte eine Trennung in Netzwerk-Config und UI-Strings sinnvoller sein.
