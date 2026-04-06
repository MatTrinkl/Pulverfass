# Server Module

Das Servermodul stellt einen Ktor-Server mit WebSocket-Unterstuetzung bereit.

## WebSocket-Konfiguration

- Das Ktor-Plugin `WebSockets` wird in `Application.module()` installiert.
- Der WebSocket-Endpunkt ist unter `/ws` verfuegbar.
- Text Frames werden in Serie 1 aktiv abgelehnt: Der Server schliesst die Verbindung mit `CANNOT_ACCEPT` und der Nachricht `Text frames are not supported on /ws.`.
- Weitere fachliche Nachrichtenverarbeitung ist bewusst noch nicht angeschlossen.

## Start und Stop

- Lokal starten: `./gradlew :server:run`
- Fuer programmatischen Start/Stop steht `createServer(host, port)` zur Verfuegung.
- Sauberes Stoppen erfolgt ueber `ApplicationEngine.stop(gracePeriodMillis, timeoutMillis)`.
