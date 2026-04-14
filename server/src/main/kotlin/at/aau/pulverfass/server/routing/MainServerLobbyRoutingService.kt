package at.aau.pulverfass.server.routing

import at.aau.pulverfass.server.ServerNetwork
import at.aau.pulverfass.server.lobby.mapping.DecodedNetworkRequest
import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.network.codec.MessageCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Bindet den technischen Servereingang an den MainServerRouter.
 *
 * Die Klasse liest [at.aau.pulverfass.shared.network.receive.ReceivedPacket]s aus
 * dem Netzwerkpfad, dekodiert die Payload und übergibt das Ergebnis als neutrales
 * Requestmodell an den Routing-Layer.
 */
class MainServerLobbyRoutingService(
    private val network: ServerNetwork,
    private val router: MainServerRouter,
    private val playerIdResolver: (ConnectionId) -> PlayerId?,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val hooks: MainServerLobbyRoutingServiceHooks = MainServerLobbyRoutingServiceHooks(),
) {
    private val logger = LoggerFactory.getLogger(MainServerLobbyRoutingService::class.java)
    private val lifecycleLock = Any()
    private var routingJob: Job? = null

    /**
     * Startet die kontinuierliche Verarbeitung eingehender, dekodierter Pakete.
     *
     * Jeder Paketinput wird zu einem [DecodedNetworkRequest] aufgebaut und über
     * den Router in den Lobby-Layer weitergegeben.
     */
    fun start(scope: CoroutineScope) {
        synchronized(lifecycleLock) {
            check(routingJob == null) { "MainServerLobbyRoutingService is already started." }
            routingJob =
                scope.launch {
                    network.packetReceiver.packets.collect { packet ->
                        runCatching {
                            val payload = MessageCodec.decodePayload(packet)
                            val request =
                                DecodedNetworkRequest(
                                    receivedPacket = packet,
                                    payload = payload,
                                    context =
                                        EventContext(
                                            connectionId = packet.connectionId,
                                            playerId = playerIdResolver(packet.connectionId),
                                            occurredAtEpochMillis = nowEpochMillis(),
                                        ),
                                )
                            when (val result = router.route(request)) {
                                is LobbyRoutingResult.Success ->
                                    hooks.onRouted(packet.connectionId)
                                is LobbyRoutingResult.Failure ->
                                    hooks.onRoutingError(packet.connectionId, result.error)
                            }
                        }.onFailure { cause ->
                            logger.warn(
                                "Failed to route packet for connection {}",
                                packet.connectionId.value,
                                cause,
                            )
                            hooks.onRoutingError(
                                packet.connectionId,
                                LobbyRoutingError.InvalidRoutingData(
                                    reason = cause.message ?: "Technischer Routingfehler.",
                                    context =
                                        LobbyRoutingContext(
                                            connectionId = packet.connectionId,
                                            messageType = packet.header.type,
                                        ),
                                    cause = cause,
                                ),
                            )
                        }
                    }
                }
        }
    }

    /**
     * Stoppt die Paketverarbeitung kontrolliert.
     */
    suspend fun stop() {
        val activeJob =
            synchronized(lifecycleLock) {
                val current = routingJob
                routingJob = null
                current
            } ?: return

        activeJob.cancel()
        activeJob.join()
    }
}

data class MainServerLobbyRoutingServiceHooks(
    /** Wird bei erfolgreichem Routing eines Pakets ausgelöst. */
    val onRouted: (ConnectionId) -> Unit = {},
    /** Wird bei Routing-/Validierungsfehlern ausgelöst. */
    val onRoutingError: (ConnectionId, LobbyRoutingError) -> Unit = { _, _ -> },
)
