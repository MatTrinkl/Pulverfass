package at.aau.pulverfass.server

import at.aau.pulverfass.server.ids.IdFactory
import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.server.transport.ServerWebSocketTransport
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.network.Network
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val DEFAULT_HOST = "localhost"
private const val DEFAULT_PORT = 8080
private val logger = LoggerFactory.getLogger("at.aau.pulverfass.server.WebSocketEndpoint")

/**
 * Startet den eingebetteten Ktor-Server mit der Standardkonfiguration.
 */
fun main() {
    createServerWithLobbyRuntime().start(wait = true)
}

/**
 * Erzeugt eine startbare Serverinstanz für das Servermodul.
 *
 * Für die Integration soll ausschließlich [ServerNetwork] injiziert werden.
 * Dadurch bleibt die High-Level-Netzwerk-API der einzige öffentliche Einstieg.
 *
 * @param host Hostadresse des eingebetteten Servers
 * @param port Zielport des eingebetteten Servers
 * @param network serverseitige Netzwerkkomposition für `/ws`
 */
fun createServer(
    host: String = DEFAULT_HOST,
    port: Int = DEFAULT_PORT,
    network: ServerNetwork = ServerNetwork(),
): ApplicationEngine =
    embeddedServer(
        factory = Netty,
        host = host,
        port = port,
    ) {
        module(network)
    }

/**
 * Erzeugt eine startbare Serverinstanz mit aktivem Lobby-Routing zur
 * Verarbeitung von Create/Join/Leave/Kick/Start-Requests.
 */
fun createServerWithLobbyRuntime(
    host: String = DEFAULT_HOST,
    port: Int = DEFAULT_PORT,
    network: ServerNetwork = ServerNetwork(),
): ApplicationEngine =
    embeddedServer(
        factory = Netty,
        host = host,
        port = port,
    ) {
        moduleWithLobbyRuntime(network)
    }

/**
 * Test-Hilfsmethode für Low-Level-Transporttests.
 *
 * Die Transportvariante bleibt bewusst intern, damit Produktionscode nicht am
 * High-Level-Network vorbei integriert wird.
 */
internal fun createServer(
    host: String,
    port: Int,
    transport: ServerWebSocketTransport,
): ApplicationEngine = createServer(host, port, ServerNetwork(transport = transport))

/**
 * Konfiguriert die Ktor-Anwendung mit WebSocket-Unterstützung auf `/ws`.
 *
 * Der Endpunkt delegiert den kompletten Verbindungslebenszyklus an
 * [ServerNetwork]. Text Frames werden gemäß [WebSocketPolicy] aktiv abgelehnt.
 *
 * @param network serverseitige Netzwerkkomposition für die WebSocket-Route
 */
fun Application.module(network: ServerNetwork = ServerNetwork()) {
    install(WebSockets) {
        pingPeriodMillis = 15_000
        timeoutMillis = 15_000
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        webSocket("/ws") {
            handleWebSocketConnection(network)
        }
    }
}

/**
 * Produktionsverdrahtung mit aktiver Lobby-Routing-Pipeline.
 *
 * Im Gegensatz zu [module] wird hier zusÃ¤tzlich die Routing-Service-Schicht
 * gestartet, damit Create/Join/Leave-Requests tatsÃ¤chlich in den Lobby-Layer
 * gelangen und Antworten an Clients zurÃ¼ckflieÃŸen.
 */
fun Application.moduleWithLobbyRuntime(network: ServerNetwork = ServerNetwork()) {
    module(network)
    installLobbyRuntime(network)
}

/**
 * Test-Hilfsmethode für direkte Transporttests ohne High-Level-Eventpfad.
 */
internal fun Application.module(transport: ServerWebSocketTransport) {
    module(ServerNetwork(transport = transport))
}

private fun Application.installLobbyRuntime(network: ServerNetwork) {
    val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val lobbyManager = LobbyManager(serverScope)
    val router =
        MainServerRouter(
            lobbyManager = lobbyManager,
            mapper = DefaultNetworkToLobbyEventMapper(),
        )

    val playersByConnection = ConcurrentHashMap<ConnectionId, PlayerId>()
    val connectionsByPlayer = ConcurrentHashMap<PlayerId, ConnectionId>()
    val nextPlayerId = AtomicLong(1)

    val routingService =
        MainServerLobbyRoutingService(
            network = network,
            router = router,
            lobbyManager = lobbyManager,
            playerIdResolver = { connectionId -> playersByConnection[connectionId] },
            connectionIdResolver = { playerId -> connectionsByPlayer[playerId] },
        )

    serverScope.launch {
        network.events.collect { event ->
            when (event) {
                is Network.Event.Connected<ConnectionId> -> {
                    val playerId = PlayerId(nextPlayerId.getAndIncrement())
                    playersByConnection[event.connectionId] = playerId
                    connectionsByPlayer[playerId] = event.connectionId
                }

                is Network.Event.Disconnected<ConnectionId> -> {
                    val removedPlayer = playersByConnection.remove(event.connectionId)
                    if (removedPlayer != null) {
                        connectionsByPlayer.remove(removedPlayer)
                    }
                }

                else -> Unit
            }
        }
    }

    routingService.start(serverScope)

    environment.monitor.subscribe(ApplicationStopped) {
        runBlocking {
            routingService.stop()
            lobbyManager.shutdownAll()
        }
        serverScope.cancel()
    }
}

/**
 * Behandelt den Lebenszyklus einer einzelnen WebSocket-Verbindung.
 *
 * Für jede Verbindung wird serverseitig eine neue `ConnectionId` vergeben.
 * Binary Frames werden an [ServerNetwork] weitergereicht, Text Frames dagegen
 * aktiv mit dokumentiertem Close-Reason abgelehnt.
 */
private suspend fun DefaultWebSocketServerSession.handleWebSocketConnection(
    network: ServerNetwork,
) {
    val connectionId = IdFactory.nextConnectionId()

    network.onConnected(connectionId, this)

    try {
        for (frame in incoming) {
            when (frame) {
                is Frame.Binary -> network.onBinaryMessage(connectionId, frame.data.copyOf())
                is Frame.Text -> {
                    logger.warn(
                        "Rejecting text websocket frame on connection {} " +
                            "because only binary frames are supported",
                        connectionId.value,
                    )
                    close(
                        CloseReason(
                            CloseReason.Codes.CANNOT_ACCEPT,
                            WebSocketPolicy.TEXT_FRAMES_NOT_SUPPORTED,
                        ),
                    )
                    break
                }

                else -> Unit
            }
        }
    } catch (cause: Throwable) {
        network.onError(connectionId, cause)
        throw cause
    } finally {
        val reason = runCatching { closeReason.await()?.message }.getOrNull()
        network.onDisconnected(connectionId, reason)
    }
}
