# Server Module

Das Servermodul stellt einen Ktor-Server mit WebSocket-Unterstuetzung bereit.

## WebSocket-Konfiguration

- Das Ktor-Plugin `WebSockets` wird in `Application.module()` installiert.
- Der WebSocket-Endpunkt ist unter `/ws` verfuegbar.
- Die serverseitige Transport-Schicht `ServerWebSocketTransport` verwaltet aktive WebSocket-Sessions unabhaengig von Spiellogik.
- Pro Verbindung wird serverseitig eine `ConnectionId` vergeben.
- Transport-Events werden als `SharedFlow` emittiert: `Connected`, `BinaryMessageReceived`, `Disconnected` und optional `TransportError`.
- Binary Frames werden als rohe ByteArrays weitergereicht und koennen ueber `send(connectionId, bytes)` auch wieder an bestehende Verbindungen gesendet werden.
- Fuer den technischen Outbound-Pfad verpackt `PacketSendAdapter` ein `SerializedPacket` ueber `PacketCodec` in Wire-Bytes und sendet diese via `ServerWebSocketTransport`.
- Text Frames werden in Serie 1 aktiv gemaess `WebSocketPolicy` abgelehnt: Der Server schliesst die Verbindung mit `CANNOT_ACCEPT` und der Nachricht `Text frames are not supported on /ws.`.
- Weitere fachliche Nachrichtenverarbeitung ist bewusst noch nicht angeschlossen.

## Start und Stop

- Lokal starten: `./gradlew :server:run`
- Fuer programmatischen Start/Stop steht `createServer(host, port)` zur Verfuegung.
- Sauberes Stoppen erfolgt ueber `ApplicationEngine.stop(gracePeriodMillis, timeoutMillis)`.
