package at.aau.pulverfass.app.network.transport

import at.aau.pulverfass.app.network.CLIENT_CONNECTION_ID
import at.aau.pulverfass.shared.network.transport.BinaryMessageReceived
import at.aau.pulverfass.shared.network.transport.Connected
import at.aau.pulverfass.shared.network.transport.Disconnected
import at.aau.pulverfass.shared.network.transport.TransportError
import at.aau.pulverfass.shared.network.transport.TransportEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Technische Transport-Schicht für WebSocket-Verbindungen auf Android.
 *
 * Die Klasse kapselt den Ktor-WebSocket-Client, emittiert reine technische
 * Transportevents und erlaubt das Senden roher ByteArrays an die aktive
 * Verbindung. Fachliche Nachrichtenauswertung gehört nicht in diese Schicht.
 */
class AndroidWebSocketTransport(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val client: HttpClient =
        HttpClient(CIO) {
            install(WebSockets)
        },
) {
    private val sessionMutex = Mutex()
    private var session: DefaultClientWebSocketSession? = null
    private var readJob: Job? = null
    private val _events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)

    /**
     * Strom aller technischen Transportevents dieser Client-Instanz.
     */
    val events: SharedFlow<TransportEvent> = _events.asSharedFlow()

    /**
     * Baut eine WebSocket-Verbindung auf und startet den Read-Loop.
     */
    suspend fun connect(serverUrl: String) {
        disconnect(reason = "Reconnecting")

        val connectedSession =
            client.webSocketSession {
                method = HttpMethod.Get
                url(serverUrl)
            }

        sessionMutex.withLock {
            session = connectedSession
            readJob = startReadLoop(connectedSession)
        }

        _events.emit(Connected(CLIENT_CONNECTION_ID))
    }

    /**
     * Trennt die aktive WebSocket-Verbindung, falls eine existiert.
     */
    suspend fun disconnect(reason: String? = null) {
        val activeSession =
            sessionMutex.withLock {
                val current = session
                session = null
                current
            } ?: return

        runCatching {
            activeSession.close(
                reason =
                    CloseReason(
                        code = CloseReason.Codes.NORMAL,
                        message = reason ?: "Client disconnected",
                    ),
            )
        }

        sessionMutex.withLock {
            readJob?.cancel()
            readJob = null
        }
    }

    /**
     * Sendet rohe Bytes als Binaryframe an die aktive Verbindung.
     *
     * @throws IllegalStateException wenn aktuell keine Verbindung besteht
     */
    suspend fun send(bytes: ByteArray) {
        val activeSession =
            sessionMutex.withLock {
                session
            } ?: throw IllegalStateException("No active websocket session")

        activeSession.send(Frame.Binary(fin = true, data = bytes.copyOf()))
    }

    /**
     * Gibt Ressourcen der Transport-Schicht frei.
     */
    fun close() {
        scope.cancel()
        client.close()
    }

    private fun startReadLoop(activeSession: DefaultClientWebSocketSession): Job =
        scope.launch {
            try {
                for (frame in activeSession.incoming) {
                    when (frame) {
                        is Frame.Binary -> {
                            _events.emit(
                                BinaryMessageReceived(
                                    connectionId = CLIENT_CONNECTION_ID,
                                    bytes = frame.readBytes(),
                                ),
                            )
                        }

                        is Frame.Text -> {
                            _events.emit(
                                TransportError(
                                    connectionId = CLIENT_CONNECTION_ID,
                                    cause =
                                        IllegalArgumentException(
                                            "Text frames are not supported by the protocol",
                                        ),
                                ),
                            )
                        }

                        else -> Unit
                    }
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                _events.emit(TransportError(CLIENT_CONNECTION_ID, cause))
            } finally {
                sessionMutex.withLock {
                    if (session === activeSession) {
                        session = null
                    }
                    readJob = null
                }
                _events.emit(Disconnected(CLIENT_CONNECTION_ID))
            }
        }
}
