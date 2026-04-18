# Network API Integration

## 1. Integration auf Server- und Appseite

### Zielbild

Die fachliche Integration soll nur noch mit `NetworkMessagePayload` arbeiten.  
Transport, Packet-Framing, Header-Serialisierung und WebSocket-Handling bleiben im Netzwerk-Layer.

Öffentliche Einstiegsschnittstelle ist:

- [Network.kt](../shared/src/main/kotlin/at/aau/pulverfass/shared/network/Network.kt)

Wichtige Produktivklassen:

- [ServerNetwork.kt](../server/src/main/kotlin/at/aau/pulverfass/server/ServerNetwork.kt)
- [Application.kt](../server/src/main/kotlin/at/aau/pulverfass/server/Application.kt)
- [MessageCodec.kt](../shared/src/main/kotlin/at/aau/pulverfass/shared/network/codec/MessageCodec.kt)

### Serverseite

Auf der Serverseite ist die Integration bereits vorhanden.

Was integriert werden muss:

- `ServerNetwork` erzeugen oder injizieren
- `Application.module(network)` bzw. `createServer(network = ...)` verwenden
- Fachlogik auf `network.events` hängen
- Antworten über `network.send(connectionId, payload)` senden

Beispiel:

```kotlin
import at.aau.pulverfass.server.ServerNetwork
import at.aau.pulverfass.server.createServer
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.network.Network
import at.aau.pulverfass.shared.network.message.JoinLobbyRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val network = ServerNetwork()
val server = createServer(host = "0.0.0.0", port = 8080, network = network)

CoroutineScope(Dispatchers.Default).launch {
    network.events.collect { event ->
        when (event) {
            is Network.Event.Connected<ConnectionId> -> {
                println("Client verbunden: ${event.connectionId.value}")
            }
            is Network.Event.MessageReceived<ConnectionId> -> {
                when (val payload = event.payload) {
                    is JoinLobbyRequest -> {
                        println("Join-Lobby von ${payload.playerDisplayName} für ${payload.lobbyCode}")
                    }
                }
            }
            is Network.Event.Disconnected<ConnectionId> -> {
                println("Client getrennt: ${event.connectionId.value}")
            }
            is Network.Event.Error<ConnectionId> -> {
                println("Netzwerkfehler: ${event.cause.message}")
            }
        }
    }
}

server.start(wait = true)
```

Senden:

```kotlin
network.send(
    connectionId,
    JoinLobbyRequest(lobbyCode = LobbyCode("AB12"), playerDisplayName = "alice"),
)
```

Was der Server dabei automatisch übernimmt:

- WebSocket-Verbindung auf `/ws`
- Binary-Only-Verhalten
- `NetworkMessagePayload -> ByteArray`
- `ByteArray -> NetworkMessagePayload`
- Connect-, Message-, Disconnect- und Error-Events

### Appseite

Die App-Integration fehlt aktuell noch als Produktivklasse.  
Für die App muss eine Client-Implementierung derselben Schnittstelle gebaut werden, zum Beispiel `AppNetwork`.

Diese Klasse sollte:

- `Network<...>` implementieren
- eine Ktor-WebSocket-Verbindung zum Server auf `ws://<host>:<port>/ws` aufbauen
- eingehende Binary Frames mit `MessageCodec.decodePayload(...)` dekodieren
- ausgehende Payloads mit `MessageCodec.encode(...)` senden
- `Network.Event.Connected`, `MessageReceived`, `Disconnected` und `Error` emittieren

Minimaler Ablauf für die App:

1. WebSocket zum Server auf `/ws` öffnen
2. Binary Frames lesen
3. `MessageCodec.decodePayload(bytes)` aufrufen
4. Ergebnis als `Network.Event.MessageReceived` weitergeben
5. Beim Senden nur `NetworkMessagePayload` entgegennehmen

Beispiel für den technischen Kern einer späteren `AppNetwork`-Klasse:

```kotlin
import at.aau.pulverfass.shared.network.Network
import at.aau.pulverfass.shared.network.codec.MessageCodec
import at.aau.pulverfass.shared.network.message.NetworkMessagePayload
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AppNetwork(
    private val client: HttpClient =
        HttpClient {
            install(WebSockets)
        },
) : Network<Unit> {
    private val _events = MutableSharedFlow<Network.Event<Unit>>(extraBufferCapacity = 64)
    private var session: DefaultClientWebSocketSession? = null

    override val events: SharedFlow<Network.Event<Unit>> = _events.asSharedFlow()

    suspend fun connect() {
        val webSocketSession = client.webSocketSession("ws://127.0.0.1:8080/ws")
        session = webSocketSession
        _events.emit(Network.Event.Connected(Unit))

        for (frame in webSocketSession.incoming) {
            when (frame) {
                is Frame.Binary -> {
                    val payload = MessageCodec.decodePayload(frame.readBytes())
                    _events.emit(Network.Event.MessageReceived(Unit, payload))
                }
                else -> Unit
            }
        }
    }

    override suspend fun send(
        connectionId: Unit,
        payload: NetworkMessagePayload,
    ) {
        val webSocketSession = checkNotNull(session) { "WebSocket ist noch nicht verbunden." }
        webSocketSession.send(Frame.Binary(fin = true, data = MessageCodec.encode(payload)))
    }
}
```

Wichtig:

- Die App soll keine Header manuell bauen.
- Die App soll keine `SerializedPacket` oder `PacketCodec` direkt verwenden.
- Diese Details bleiben im Netzwerk-Layer.

## 2. Neue Message-Payloads hinzufügen

Damit eine neue Nachricht im System funktioniert, müssen Server und App dieselbe Payload-Klasse und denselben
`MessageType` kennen.

### Schritt 1: Neuen MessageType anlegen

In [MessageType.kt](../shared/src/main/kotlin/at/aau/pulverfass/shared/network/message/MessageType.kt) einen neuen
Eintrag ergänzen.

Beispiel:

```kotlin
GAME_START_REQUEST(13)
```

Die ID muss eindeutig sein.

### Schritt 2: Payload-Klasse anlegen

In `shared.network.message` eine neue `@Serializable`-Klasse anlegen, die `NetworkMessagePayload` implementiert.

Beispiel:

```kotlin
@Serializable
data class GameStartRequest(
    val lobbyCode: String,
) : NetworkMessagePayload
```

### Schritt 3: Registry erweitern

In [NetworkPayloadRegistry.kt](../shared/src/main/kotlin/at/aau/pulverfass/shared/network/message/NetworkPayloadRegistry.kt)
müssen drei Zuordnungen ergänzt werden:

- `payloadTypeByClass`
- `payloadSerializerByClass`
- `payloadDeserializerByType`

Ohne diese Einträge kann die Nachricht nicht automatisch kodiert oder dekodiert werden.

### Schritt 4: Fachlogik anbinden

Danach kann die Nachricht über `Network.Event.MessageReceived` verarbeitet werden.

Beispiel:

```kotlin
when (val payload = event.payload) {
    is GameStartRequest -> {
        // Fachlogik
    }
}
```

### Schritt 5: Tests ergänzen

Mindestens ergänzen:

- Codec-Test für `MessageCodec.encode/decodePayload`
- Server-Integrationstest für Empfang
- Server-Integrationstest für Versand, falls der Server diese Payload sendet

## Kurzfassung

- Fachcode arbeitet nur mit `Network` und `NetworkMessagePayload`.
- Serverseitig ist das bereits verdrahtet.
- Appseitig fehlt noch eine Client-Implementierung derselben Schnittstelle.
- Neue Nachrichten brauchen immer:
  `MessageType` + Payload-Klasse + Registry-Einträge + Tests.
