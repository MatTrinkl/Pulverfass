package at.aau.pulverfass.server.routing

import at.aau.pulverfass.server.ServerNetwork
import at.aau.pulverfass.server.lobby.mapping.DecodedNetworkRequest
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerKickedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.KickPlayerRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.KickPlayerResponse
import at.aau.pulverfass.shared.message.lobby.response.LeaveLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.StartGameResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.KickPlayerErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartGameErrorResponse
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.codec.MessageCodec
import at.aau.pulverfass.shared.network.receive.ReceivedPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.random.Random

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
    private val lobbyManager: LobbyManager,
    private val playerIdResolver: (ConnectionId) -> PlayerId?,
    private val connectionIdResolver: (PlayerId) -> ConnectionId? = { null },
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
                        routePacket(packet)
                    }
                }
        }
    }

    private suspend fun routePacket(packet: ReceivedPacket) {
        runCatching {
            val request = decodeRequest(packet)
            if (request.payload is CreateLobbyRequest) {
                routeCreateLobbyRequest(packet)
                return
            }
            routeDecodedRequest(request)
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

    private fun decodeRequest(packet: ReceivedPacket): DecodedNetworkRequest {
        val payload = MessageCodec.decodePayload(packet)
        return DecodedNetworkRequest(
            receivedPacket = packet,
            payload = payload,
            context =
                EventContext(
                    connectionId = packet.connectionId,
                    playerId = playerIdResolver(packet.connectionId),
                    occurredAtEpochMillis = nowEpochMillis(),
                ),
        )
    }

    private suspend fun routeCreateLobbyRequest(packet: ReceivedPacket) {
        runCatching {
            handleCreateLobbyRequest(packet.connectionId)
            hooks.onRouted(packet.connectionId)
        }.onFailure { cause ->
            dispatchCreateErrorResponse(
                connectionId = packet.connectionId,
                reason = cause.message ?: "Lobby konnte nicht erstellt werden.",
            )
            hooks.onRoutingError(
                packet.connectionId,
                LobbyRoutingError.InvalidRoutingData(
                    reason = cause.message ?: "Lobby konnte nicht erstellt werden.",
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

    private suspend fun routeDecodedRequest(request: DecodedNetworkRequest) {
        when (val result = router.route(request)) {
            is LobbyRoutingResult.Success -> {
                dispatchNetworkMessages(request)
                hooks.onRouted(request.connectionId)
            }

            is LobbyRoutingResult.Failure -> {
                dispatchErrorResponse(request, result.error.reason)
                hooks.onRoutingError(request.connectionId, result.error)
            }
        }
    }

    private suspend fun dispatchNetworkMessages(request: DecodedNetworkRequest) {
        when (val payload = request.payload) {
            is JoinLobbyRequest -> dispatchJoinNetworkMessages(request, payload)
            is LeaveLobbyRequest -> dispatchLeaveNetworkMessages(request, payload)
            is KickPlayerRequest -> dispatchKickNetworkMessages(request, payload)
            is StartGameRequest -> dispatchStartGameNetworkMessages(request, payload)
            else -> return
        }
    }

    private suspend fun handleCreateLobbyRequest(connectionId: ConnectionId) {
        val lobbyCode = createLobbyWithUniqueCode()
        network.send(connectionId, CreateLobbyResponse(lobbyCode = lobbyCode))
    }

    private suspend fun dispatchCreateErrorResponse(
        connectionId: ConnectionId,
        reason: String,
    ) {
        network.send(connectionId, CreateLobbyErrorResponse(reason))
    }

    private suspend fun dispatchJoinNetworkMessages(
        request: DecodedNetworkRequest,
        payload: JoinLobbyRequest,
    ) {
        network.send(request.connectionId, JoinLobbyResponse(payload.lobbyCode))

        val playerId = request.context.playerId ?: return
        val lobbyState = lobbyManager.getLobby(payload.lobbyCode)?.currentState() ?: return
        val members = lobbyState.players

        members
            .filter { existingPlayerId -> existingPlayerId != playerId }
            .forEach { existingPlayerId ->
                val existingName = lobbyState.playerDisplayNames[existingPlayerId] ?: return@forEach
                network.send(
                    request.connectionId,
                    PlayerJoinedLobbyEvent(
                        lobbyCode = payload.lobbyCode,
                        playerId = existingPlayerId,
                        playerDisplayName = existingName,
                        isHost = lobbyState.lobbyOwner == existingPlayerId,
                    ),
                )
            }

        val event =
            PlayerJoinedLobbyEvent(
                lobbyCode = payload.lobbyCode,
                playerId = playerId,
                playerDisplayName = payload.playerDisplayName,
                isHost = lobbyState.lobbyOwner == playerId,
            )

        members
            .mapNotNull(connectionIdResolver)
            .distinct()
            .forEach { connectionId ->
                network.send(connectionId, event)
            }
    }

    private suspend fun dispatchLeaveNetworkMessages(
        request: DecodedNetworkRequest,
        payload: LeaveLobbyRequest,
    ) {
        network.send(request.connectionId, LeaveLobbyResponse(payload.lobbyCode))

        val playerId = request.context.playerId ?: return
        val lobby = lobbyManager.getLobby(payload.lobbyCode) ?: return
        val lobbyState = lobby.currentState()
        val members = lobbyState.players
        val event =
            PlayerLeftLobbyEvent(
                lobbyCode = payload.lobbyCode,
                playerId = playerId,
                newHost = lobbyState.lobbyOwner,
            )

        members
            .mapNotNull(connectionIdResolver)
            .distinct()
            .forEach { connectionId ->
                network.send(connectionId, event)
            }
    }

    private suspend fun dispatchKickNetworkMessages(
        request: DecodedNetworkRequest,
        payload: KickPlayerRequest,
    ) {
        network.send(request.connectionId, KickPlayerResponse())

        val members = lobbyManager.getLobby(payload.lobbyCode)?.currentState()?.players.orEmpty()
        val event =
            PlayerKickedLobbyEvent(
                lobbyCode = payload.lobbyCode,
                targetPlayerId = payload.targetPlayerId,
                requesterPlayerId = payload.requesterPlayerId,
            )

        members
            .mapNotNull(connectionIdResolver)
            .distinct()
            .forEach { connectionId ->
                network.send(connectionId, event)
            }
    }

    private suspend fun dispatchStartGameNetworkMessages(
        request: DecodedNetworkRequest,
        payload: StartGameRequest,
    ) {
        network.send(request.connectionId, StartGameResponse())

        val members = lobbyManager.getLobby(payload.lobbyCode)?.currentState()?.players.orEmpty()
        val event = GameStartedEvent(lobbyCode = payload.lobbyCode)

        members
            .mapNotNull(connectionIdResolver)
            .distinct()
            .forEach { connectionId ->
                network.send(connectionId, event)
            }
    }

    private suspend fun dispatchErrorResponse(
        request: DecodedNetworkRequest,
        reason: String,
    ) {
        val payload = errorResponseFor(request.payload, reason) ?: return
        network.send(request.connectionId, payload)
    }

    private fun errorResponseFor(
        payload: NetworkMessagePayload,
        reason: String,
    ): NetworkMessagePayload? =
        when (payload) {
            CreateLobbyRequest -> CreateLobbyErrorResponse(reason)
            is JoinLobbyRequest -> JoinLobbyErrorResponse(reason)
            is KickPlayerRequest -> KickPlayerErrorResponse(reason)
            is StartGameRequest -> StartGameErrorResponse(reason)
            else -> null
        }

    private fun createLobbyWithUniqueCode(): LobbyCode {
        repeat(10_000) {
            val candidate = LobbyCode(generateLobbyCodeValue())
            val created =
                runCatching { lobbyManager.createLobby(candidate) }
                    .getOrNull()
            if (created != null) {
                return candidate
            }
        }
        throw IllegalStateException("Konnte keinen eindeutigen Lobby-Code erzeugen.")
    }

    private fun generateLobbyCodeValue(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return buildString(4) {
            repeat(4) {
                append(alphabet[Random.nextInt(alphabet.length)])
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

/**
 * Optionale Lifecycle-Hooks für Beobachtbarkeit und Tests des Routing-Flows.
 */
data class MainServerLobbyRoutingServiceHooks(
    /** Wird bei erfolgreichem Routing eines Pakets ausgelöst. */
    val onRouted: (ConnectionId) -> Unit = {},
    /** Wird bei Routing-/Validierungsfehlern ausgelöst. */
    val onRoutingError: (ConnectionId, LobbyRoutingError) -> Unit = { _, _ -> },
)
