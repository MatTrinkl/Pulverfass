package at.aau.pulverfass.server.routing

import at.aau.pulverfass.server.ServerNetwork
import at.aau.pulverfass.server.lobby.mapping.DecodedNetworkRequest
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.StartPlayerConfigured
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnStateMachine
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerKickedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.KickPlayerRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequest
import at.aau.pulverfass.shared.message.lobby.request.StartPlayerSetRequest
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStateCatchUpResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStatePrivateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.KickPlayerResponse
import at.aau.pulverfass.shared.message.lobby.response.LeaveLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import at.aau.pulverfass.shared.message.lobby.response.StartPlayerSetResponse
import at.aau.pulverfass.shared.message.lobby.response.StartGameResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.KickPlayerErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartGameErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorResponse
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.codec.MessageCodec
import at.aau.pulverfass.shared.network.receive.ReceivedPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
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
    private val gameStateDelivery =
        GameStateDeliveryDispatcher(
            sendPayload = network::send,
            lobbyMembers = { lobbyCode -> lobbyManager.getLobby(lobbyCode)?.currentState()?.players.orEmpty() },
            connectionIdResolver = connectionIdResolver,
        )
    private val publicGameStateBuilder = PublicGameStateBuilder()
    private val roundHistoryByLobby = ConcurrentHashMap<LobbyCode, RoundHistoryBuffer>()

    init {
        lobbyManager.registerAcceptedEventListener(::broadcastAcceptedLobbyEvent)
    }

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
            if (request.payload is MapGetRequest) {
                routeMapGetRequest(request)
                return
            }
            if (request.payload is GameStateCatchUpRequest) {
                routeGameStateCatchUpRequest(request)
                return
            }
            if (request.payload is GameStatePrivateGetRequest) {
                routeGameStatePrivateGetRequest(request)
                return
            }
            if (request.payload is StartPlayerSetRequest) {
                routeStartPlayerSetRequest(request)
                return
            }
            if (request.payload is TurnStateGetRequest) {
                routeTurnStateGetRequest(request)
                return
            }
            if (request.payload is TurnAdvanceRequest) {
                routeTurnAdvanceRequest(request)
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

    suspend fun onPlayerDisconnected(playerId: PlayerId) {
        val lobbyCode = lobbyManager.findLobbyCodeByPlayer(playerId) ?: return
        val previousTurnState = currentTurnState(lobbyCode)
        val currentState = lobbyManager.getLobby(lobbyCode)?.currentState() ?: return
        val currentTurnState = currentState.turnState ?: return
        if (currentState.status != GameStatus.RUNNING) {
            return
        }
        if (currentTurnState.activePlayerId != playerId || currentTurnState.isPaused) {
            return
        }

        lobbyManager.submit(
            waitingForPlayerTurnStateEvent(
                lobbyCode = lobbyCode,
                turnState = currentTurnState,
                pausedPlayerId = playerId,
            ),
        )
        logger.info(
            "Turn pause triggered by disconnect: lobbyCode={} waitingPlayerId={}",
            lobbyCode.value,
            playerId.value,
        )
        broadcastTurnStateIfChanged(lobbyCode, previousTurnState)
    }

    suspend fun onPlayerConnected(playerId: PlayerId) {
        val lobbyCode = lobbyManager.findLobbyCodeByPlayer(playerId) ?: return
        val previousTurnState = currentTurnState(lobbyCode)
        val currentState = lobbyManager.getLobby(lobbyCode)?.currentState() ?: return
        val currentTurnState = currentState.turnState ?: return
        if (
            currentState.status != GameStatus.RUNNING ||
            !currentTurnState.isPaused ||
            currentTurnState.pauseReason != TurnPauseReasons.WAITING_FOR_PLAYER ||
            currentTurnState.pausedPlayerId != playerId
        ) {
            return
        }

        lobbyManager.submit(
            currentTurnState.toUpdatedEvent(
                lobbyCode = lobbyCode,
                isPaused = false,
                pauseReason = null,
                pausedPlayerId = null,
            ),
        )
        logger.info(
            "Turn resumed after reconnect: lobbyCode={} resumedPlayerId={}",
            lobbyCode.value,
            playerId.value,
        )
        broadcastTurnStateIfChanged(lobbyCode, previousTurnState)
    }

    private suspend fun routeMapGetRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as MapGetRequest

        runCatching {
            val response = buildMapGetResponse(request, payload)
            gameStateDelivery.sendPublicState(request.connectionId, response)
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = mapGetErrorResponse(request, payload, cause)
            network.send(request.connectionId, error)
            hooks.onRoutingError(
                request.connectionId,
                LobbyRoutingError.InvalidRoutingData(
                    reason = error.reason,
                    context =
                        LobbyRoutingContext(
                            connectionId = request.connectionId,
                            messageType = request.receivedPacket.header.type,
                            lobbyCode = payload.lobbyCode,
                        ),
                    cause = cause,
                ),
            )
        }
    }

    private suspend fun routeGameStateCatchUpRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as GameStateCatchUpRequest

        runCatching {
            val response = buildGameStateCatchUpResponse(request, payload)
            gameStateDelivery.sendPublicState(request.connectionId, response)
            roundHistoryBuffer(payload.lobbyCode).recordSnapshot(
                roundIndex = response.turnState.turnCount,
                stateVersion = response.stateVersion,
                trigger = RoundSnapshotTrigger.CATCH_UP_RESPONSE,
            )
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = gameStateCatchUpErrorResponse(request, payload, cause)
            network.send(request.connectionId, error)
            hooks.onRoutingError(
                request.connectionId,
                LobbyRoutingError.InvalidRoutingData(
                    reason = error.reason,
                    context =
                        LobbyRoutingContext(
                            connectionId = request.connectionId,
                            messageType = request.receivedPacket.header.type,
                            lobbyCode = payload.lobbyCode,
                        ),
                    cause = cause,
                ),
            )
        }
    }

    private suspend fun routeGameStatePrivateGetRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as GameStatePrivateGetRequest

        runCatching {
            val response = buildGameStatePrivateGetResponse(request, payload)
            gameStateDelivery.sendPrivateState(request.connectionId, response)
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = gameStatePrivateGetErrorResponse(request, payload, cause)
            network.send(request.connectionId, error)
            hooks.onRoutingError(
                request.connectionId,
                LobbyRoutingError.InvalidRoutingData(
                    reason = error.reason,
                    context =
                        LobbyRoutingContext(
                            connectionId = request.connectionId,
                            messageType = request.receivedPacket.header.type,
                            lobbyCode = payload.lobbyCode,
                        ),
                    cause = cause,
                ),
            )
        }
    }

    private suspend fun routeTurnAdvanceRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as TurnAdvanceRequest
        val previousTurnState = currentTurnState(payload.lobbyCode)

        runCatching {
            val turnStateUpdate = buildTurnAdvanceEvent(request, payload)
            lobbyManager.submit(turnStateUpdate, request.context)
            network.send(request.connectionId, TurnAdvanceResponse(payload.lobbyCode))
            broadcastPhaseBoundaryIfChanged(payload.lobbyCode, previousTurnState)
            broadcastTurnStateIfChanged(payload.lobbyCode, previousTurnState)
            broadcastFullSnapshotOnTurnChangeIfNeeded(payload.lobbyCode, previousTurnState)
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = turnAdvanceErrorResponse(request, payload, cause)
            network.send(request.connectionId, error)
            hooks.onRoutingError(
                request.connectionId,
                LobbyRoutingError.InvalidRoutingData(
                    reason = error.reason,
                    context =
                        LobbyRoutingContext(
                            connectionId = request.connectionId,
                            messageType = request.receivedPacket.header.type,
                            lobbyCode = payload.lobbyCode,
                        ),
                    cause = cause,
                ),
            )
        }
    }

    private suspend fun routeStartPlayerSetRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as StartPlayerSetRequest
        val previousTurnState = currentTurnState(payload.lobbyCode)

        runCatching {
            val event = buildStartPlayerConfiguredEvent(request, payload)
            lobbyManager.submit(event, request.context)
            network.send(
                request.connectionId,
                StartPlayerSetResponse(
                    lobbyCode = payload.lobbyCode,
                    startPlayerId = payload.startPlayerId,
                ),
            )
            broadcastTurnStateIfChanged(payload.lobbyCode, previousTurnState)
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = startPlayerSetErrorResponse(request, payload, cause)
            network.send(request.connectionId, error)
            hooks.onRoutingError(
                request.connectionId,
                LobbyRoutingError.InvalidRoutingData(
                    reason = error.reason,
                    context =
                        LobbyRoutingContext(
                            connectionId = request.connectionId,
                            messageType = request.receivedPacket.header.type,
                            lobbyCode = payload.lobbyCode,
                        ),
                    cause = cause,
                ),
            )
        }
    }

    private suspend fun routeTurnStateGetRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as TurnStateGetRequest

        runCatching {
            val response = buildTurnStateGetResponse(payload)
            gameStateDelivery.sendPublicState(request.connectionId, response)
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = turnStateGetErrorResponse(payload, cause)
            network.send(request.connectionId, error)
            hooks.onRoutingError(
                request.connectionId,
                LobbyRoutingError.InvalidRoutingData(
                    reason = error.reason,
                    context =
                        LobbyRoutingContext(
                            connectionId = request.connectionId,
                            messageType = request.receivedPacket.header.type,
                            lobbyCode = payload.lobbyCode,
                        ),
                    cause = cause,
                ),
            )
        }
    }

    private suspend fun routeDecodedRequest(request: DecodedNetworkRequest) {
        val lobbyCode = lobbyCodeOf(request.payload)
        val previousTurnState = lobbyCode?.let(::currentTurnState)

        when (val result = router.route(request)) {
            is LobbyRoutingResult.Success -> {
                dispatchNetworkMessages(request)
                if (lobbyCode != null) {
                    broadcastTurnStateIfChanged(
                        lobbyCode = lobbyCode,
                        previousTurnState = previousTurnState,
                        force = request.payload is StartGameRequest,
                    )
                }
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

        val event = GameStartedEvent(lobbyCode = payload.lobbyCode)
        gameStateDelivery.broadcastPublicState(payload.lobbyCode, event)
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

    private fun buildMapGetResponse(
        request: DecodedNetworkRequest,
        payload: MapGetRequest,
    ): MapGetResponse {
        val lobby = lobbyManager.getLobby(payload.lobbyCode)
            ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val playerId = request.context.playerId

        if (playerId == null || !state.hasPlayer(playerId)) {
            throw IllegalArgumentException("NOT_IN_GAME")
        }
        if (!state.hasMap()) {
            throw IllegalStateException("MAP_NOT_READY")
        }

        return publicGameStateBuilder.buildMapGetResponse(state)
    }

    private fun buildGameStateCatchUpResponse(
        request: DecodedNetworkRequest,
        payload: GameStateCatchUpRequest,
    ): GameStateCatchUpResponse {
        val lobby = lobbyManager.getLobby(payload.lobbyCode)
            ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val playerId = request.context.playerId

        if (playerId == null || !state.hasPlayer(playerId)) {
            throw IllegalArgumentException("NOT_IN_GAME")
        }
        if (!state.hasMap() || state.resolvedTurnState == null) {
            throw IllegalStateException("SNAPSHOT_NOT_READY")
        }

        val currentVersion = state.stateVersion
        val diff = currentVersion - payload.clientStateVersion
        logger.info(
            "GameState catch-up served: lobbyCode={} playerId={} requestedVersion={} currentVersion={} versionDiff={} reason={} recentRounds={}",
            payload.lobbyCode.value,
            playerId.value,
            payload.clientStateVersion,
            currentVersion,
            diff,
            payload.reason?.name ?: "UNSPECIFIED",
            describeRoundHistory(payload.lobbyCode),
        )

        return publicGameStateBuilder.buildCatchUpResponse(state)
    }

    private fun buildGameStatePrivateGetResponse(
        request: DecodedNetworkRequest,
        payload: GameStatePrivateGetRequest,
    ): GameStatePrivateGetResponse {
        val lobby = lobbyManager.getLobby(payload.lobbyCode)
            ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val contextPlayerId = request.context.playerId

        if (contextPlayerId == null || contextPlayerId != payload.playerId) {
            throw IllegalArgumentException("REQUESTER_MISMATCH")
        }
        if (!state.hasPlayer(payload.playerId)) {
            throw IllegalArgumentException("NOT_IN_GAME")
        }

        val response = GameStatePrivateGetResponse.fromGameState(state, payload.playerId)
        logger.info(
            "Private snapshot served: lobbyCode={} playerId={} stateVersion={}",
            payload.lobbyCode.value,
            payload.playerId.value,
            response.stateVersion,
        )
        return response
    }

    private fun mapGetErrorResponse(
        request: DecodedNetworkRequest,
        payload: MapGetRequest,
        cause: Throwable,
    ): MapGetErrorResponse {
        val code =
            when (cause.message) {
                "GAME_NOT_FOUND" -> MapGetErrorCode.GAME_NOT_FOUND
                "NOT_IN_GAME" -> MapGetErrorCode.NOT_IN_GAME
                "MAP_NOT_READY" -> MapGetErrorCode.MAP_NOT_READY
                else -> MapGetErrorCode.MAP_NOT_READY
            }

        val reason =
            when (code) {
                MapGetErrorCode.GAME_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
                MapGetErrorCode.NOT_IN_GAME -> {
                    val playerId = request.context.playerId
                    if (playerId == null) {
                        "Connection ist keinem Spieler in Lobby '${payload.lobbyCode.value}' zugeordnet."
                    } else {
                        "Spieler '${playerId.value}' ist nicht Teil von Lobby '${payload.lobbyCode.value}'."
                    }
                }
                MapGetErrorCode.MAP_NOT_READY ->
                    "Map-State für Lobby '${payload.lobbyCode.value}' ist noch nicht verfügbar."
            }

        return MapGetErrorResponse(
            code = code,
            reason = reason,
        )
    }

    private fun gameStateCatchUpErrorResponse(
        request: DecodedNetworkRequest,
        payload: GameStateCatchUpRequest,
        cause: Throwable,
    ): GameStateCatchUpErrorResponse {
        val code =
            when (cause.message) {
                "GAME_NOT_FOUND" -> GameStateCatchUpErrorCode.GAME_NOT_FOUND
                "NOT_IN_GAME" -> GameStateCatchUpErrorCode.NOT_IN_GAME
                "SNAPSHOT_NOT_READY" -> GameStateCatchUpErrorCode.SNAPSHOT_NOT_READY
                else -> GameStateCatchUpErrorCode.SNAPSHOT_NOT_READY
            }

        val reason =
            when (code) {
                GameStateCatchUpErrorCode.GAME_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
                GameStateCatchUpErrorCode.NOT_IN_GAME -> {
                    val playerId = request.context.playerId
                    if (playerId == null) {
                        "Connection ist keinem Spieler in Lobby '${payload.lobbyCode.value}' zugeordnet."
                    } else {
                        "Spieler '${playerId.value}' ist nicht Teil von Lobby '${payload.lobbyCode.value}'."
                    }
                }
                GameStateCatchUpErrorCode.SNAPSHOT_NOT_READY ->
                    "Catch-up-Snapshot für Lobby '${payload.lobbyCode.value}' ist noch nicht verfügbar."
            }

        return GameStateCatchUpErrorResponse(code = code, reason = reason)
    }

    private fun gameStatePrivateGetErrorResponse(
        request: DecodedNetworkRequest,
        payload: GameStatePrivateGetRequest,
        cause: Throwable,
    ): GameStatePrivateGetErrorResponse {
        val code =
            when (cause.message) {
                "GAME_NOT_FOUND" -> GameStatePrivateGetErrorCode.GAME_NOT_FOUND
                "NOT_IN_GAME" -> GameStatePrivateGetErrorCode.NOT_IN_GAME
                else -> GameStatePrivateGetErrorCode.REQUESTER_MISMATCH
            }

        val reason =
            when (code) {
                GameStatePrivateGetErrorCode.GAME_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
                GameStatePrivateGetErrorCode.NOT_IN_GAME ->
                    "Spieler '${payload.playerId.value}' ist nicht Teil der Lobby '${payload.lobbyCode.value}'."
                GameStatePrivateGetErrorCode.REQUESTER_MISMATCH -> {
                    val contextPlayerId = request.context.playerId
                    if (contextPlayerId == null) {
                        "Connection ist keinem Spieler fuer Lobby '${payload.lobbyCode.value}' zugeordnet."
                    } else {
                        "Requester '${payload.playerId.value}' passt nicht zur aktuellen Connection '${contextPlayerId.value}'."
                    }
                }
            }

        return GameStatePrivateGetErrorResponse(code = code, reason = reason)
    }

    private fun buildTurnAdvanceEvent(
        request: DecodedNetworkRequest,
        payload: TurnAdvanceRequest,
    ): TurnStateUpdatedEvent {
        val lobby = lobbyManager.getLobby(payload.lobbyCode)
            ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val contextPlayerId = request.context.playerId
        val currentTurnState = state.resolvedTurnState ?: throw IllegalArgumentException("NOT_ACTIVE_PLAYER")

        if (contextPlayerId == null || contextPlayerId != payload.playerId) {
            throw IllegalArgumentException("NOT_ACTIVE_PLAYER")
        }
        if (currentTurnState.activePlayerId != payload.playerId) {
            throw IllegalArgumentException("NOT_ACTIVE_PLAYER")
        }
        if (currentTurnState.isPaused) {
            throw IllegalStateException("GAME_PAUSED")
        }
        if (payload.expectedPhase != null && payload.expectedPhase != currentTurnState.turnPhase) {
            throw IllegalArgumentException("PHASE_MISMATCH")
        }

        val updatedTurnState =
            TurnStateMachine.advance(
                turnState = currentTurnState,
                turnOrder = state.turnOrder,
            )
        val pausedOrAdvancedTurnState =
            if (isPlayerConnected(updatedTurnState.activePlayerId)) {
                updatedTurnState
            } else {
                updatedTurnState.copy(
                    isPaused = true,
                    pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                    pausedPlayerId = updatedTurnState.activePlayerId,
                )
            }

        return pausedOrAdvancedTurnState.toUpdatedEvent(payload.lobbyCode)
    }

    private fun buildStartPlayerConfiguredEvent(
        request: DecodedNetworkRequest,
        payload: StartPlayerSetRequest,
    ): StartPlayerConfigured {
        val lobby = lobbyManager.getLobby(payload.lobbyCode)
            ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val contextPlayerId = request.context.playerId

        if (contextPlayerId == null || contextPlayerId != payload.requesterPlayerId) {
            throw IllegalArgumentException("REQUESTER_MISMATCH")
        }
        if (state.gameStarted || state.status == at.aau.pulverfass.shared.lobby.state.GameStatus.RUNNING) {
            throw IllegalStateException("GAME_ALREADY_STARTED")
        }
        if (state.lobbyOwner != payload.requesterPlayerId) {
            throw IllegalArgumentException("NOT_HOST")
        }
        if (!state.hasPlayer(payload.startPlayerId)) {
            throw IllegalArgumentException("PLAYER_NOT_IN_LOBBY")
        }

        return StartPlayerConfigured(
            lobbyCode = payload.lobbyCode,
            startPlayerId = payload.startPlayerId,
            requesterPlayerId = payload.requesterPlayerId,
        )
    }

    private fun buildTurnStateGetResponse(payload: TurnStateGetRequest): TurnStateGetResponse {
        val lobby = lobbyManager.getLobby(payload.lobbyCode)
            ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        return TurnStateGetResponse.fromGameState(state).also { response ->
            logger.info(
                "Turn state snapshot served: lobbyCode={} activePlayerId={} phase={} turnCount={} paused={}",
                response.lobbyCode.value,
                response.activePlayerId.value,
                response.turnPhase.name,
                response.turnCount,
                response.isPaused,
            )
        }
    }

    private fun turnAdvanceErrorResponse(
        request: DecodedNetworkRequest,
        payload: TurnAdvanceRequest,
        cause: Throwable,
    ): TurnAdvanceErrorResponse {
        val code =
            when (cause.message) {
                "GAME_NOT_FOUND" -> TurnAdvanceErrorCode.GAME_NOT_FOUND
                "GAME_PAUSED" -> TurnAdvanceErrorCode.GAME_PAUSED
                "PHASE_MISMATCH" -> TurnAdvanceErrorCode.PHASE_MISMATCH
                else -> TurnAdvanceErrorCode.NOT_ACTIVE_PLAYER
            }

        val reason =
            when (code) {
                TurnAdvanceErrorCode.GAME_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
                TurnAdvanceErrorCode.GAME_PAUSED ->
                    "Lobby '${payload.lobbyCode.value}' ist pausiert; Turn-Wechsel ist aktuell nicht erlaubt."
                TurnAdvanceErrorCode.PHASE_MISMATCH -> {
                    val expectedPhase = payload.expectedPhase
                    val currentPhase = lobbyManager.getLobby(payload.lobbyCode)?.currentState()?.activeTurnPhase
                    if (expectedPhase == null || currentPhase == null) {
                        "Die erwartete Phase stimmt nicht mit dem autoritativen Serverzustand überein."
                    } else {
                        "Erwartete Phase '${expectedPhase.name}', aktueller Serverzustand ist '${currentPhase.name}'."
                    }
                }
                TurnAdvanceErrorCode.NOT_ACTIVE_PLAYER -> {
                    val currentState = lobbyManager.getLobby(payload.lobbyCode)?.currentState()
                    val activePlayer = currentState?.activePlayer
                    val contextPlayerId = request.context.playerId
                    when {
                        contextPlayerId == null ->
                            "Connection ist keinem aktiven Spieler für Lobby '${payload.lobbyCode.value}' zugeordnet."
                        contextPlayerId != payload.playerId ->
                            "Requester '${payload.playerId.value}' passt nicht zur aktuellen Connection."
                        activePlayer == null ->
                            "Für Lobby '${payload.lobbyCode.value}' ist aktuell kein aktiver Spieler gesetzt."
                        else ->
                            "Nur der aktive Spieler '${activePlayer.value}' darf den Turn-State fortschalten."
                    }
                }
            }

        return TurnAdvanceErrorResponse(code = code, reason = reason)
    }

    private fun startPlayerSetErrorResponse(
        request: DecodedNetworkRequest,
        payload: StartPlayerSetRequest,
        cause: Throwable,
    ): StartPlayerSetErrorResponse {
        val code =
            when (cause.message) {
                "GAME_NOT_FOUND" -> StartPlayerSetErrorCode.GAME_NOT_FOUND
                "NOT_HOST" -> StartPlayerSetErrorCode.NOT_HOST
                "PLAYER_NOT_IN_LOBBY" -> StartPlayerSetErrorCode.PLAYER_NOT_IN_LOBBY
                "GAME_ALREADY_STARTED" -> StartPlayerSetErrorCode.GAME_ALREADY_STARTED
                else -> StartPlayerSetErrorCode.REQUESTER_MISMATCH
            }

        val reason =
            when (code) {
                StartPlayerSetErrorCode.GAME_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
                StartPlayerSetErrorCode.NOT_HOST ->
                    "Nur der Lobby Owner darf den Startspieler für Lobby '${payload.lobbyCode.value}' setzen."
                StartPlayerSetErrorCode.PLAYER_NOT_IN_LOBBY ->
                    "Spieler '${payload.startPlayerId.value}' ist nicht Teil der Lobby '${payload.lobbyCode.value}'."
                StartPlayerSetErrorCode.GAME_ALREADY_STARTED ->
                    "Der Startspieler kann für Lobby '${payload.lobbyCode.value}' nach Spielstart nicht mehr geändert werden."
                StartPlayerSetErrorCode.REQUESTER_MISMATCH -> {
                    val contextPlayerId = request.context.playerId
                    if (contextPlayerId == null) {
                        "Connection ist keinem Spieler für Lobby '${payload.lobbyCode.value}' zugeordnet."
                    } else {
                        "Requester '${payload.requesterPlayerId.value}' passt nicht zur aktuellen Connection '${contextPlayerId.value}'."
                    }
                }
            }

        return StartPlayerSetErrorResponse(code = code, reason = reason)
    }

    private fun turnStateGetErrorResponse(
        payload: TurnStateGetRequest,
        cause: Throwable,
    ): TurnStateGetErrorResponse {
        val code =
            when (cause.message) {
                "GAME_NOT_FOUND" -> TurnStateGetErrorCode.GAME_NOT_FOUND
                else -> TurnStateGetErrorCode.TURN_STATE_NOT_READY
            }

        val reason =
            when (code) {
                TurnStateGetErrorCode.GAME_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
                TurnStateGetErrorCode.TURN_STATE_NOT_READY ->
                    "Turn-State für Lobby '${payload.lobbyCode.value}' ist noch nicht verfügbar."
            }

        return TurnStateGetErrorResponse(code = code, reason = reason)
    }

    private suspend fun broadcastAcceptedLobbyEvent(
        lobbyCode: LobbyCode,
        event: at.aau.pulverfass.shared.lobby.event.LobbyEvent,
        previousState: GameState,
        currentState: GameState,
    ) {
        val publicDelta = publicGameStateBuilder.buildDelta(lobbyCode, event, previousState, currentState)
        if (publicDelta != null) {
            val currentTurnCount = currentState.resolvedTurnState?.turnCount
            logger.info(
                "Public delta broadcast: lobbyCode={} playerId={} fromVersion={} toVersion={} stateVersion={} turnCount={} eventCount={}",
                lobbyCode.value,
                currentState.resolvedTurnState?.activePlayerId?.value,
                publicDelta.fromVersion,
                publicDelta.toVersion,
                currentState.stateVersion,
                currentTurnCount,
                publicDelta.events.size,
            )
            gameStateDelivery.broadcastPublicState(lobbyCode, publicDelta)
            currentTurnCount?.let { turnCount ->
                roundHistoryBuffer(lobbyCode).recordDelta(
                    roundIndex = turnCount,
                    fromVersion = publicDelta.fromVersion,
                    toVersion = publicDelta.toVersion,
                    eventCount = publicDelta.events.size,
                )
            }
        }

        val broadcastPayload =
            when (event) {
                is TerritoryOwnerChangedEvent -> {
                    event.copy(stateVersion = currentState.stateVersion)
                }
                is TerritoryTroopsChangedEvent -> {
                    event.copy(stateVersion = currentState.stateVersion)
                }
                else -> return
        }
        gameStateDelivery.broadcastPublicState(lobbyCode, broadcastPayload)
    }

    private fun lobbyCodeOf(payload: NetworkMessagePayload): LobbyCode? =
        when (payload) {
            is JoinLobbyRequest -> payload.lobbyCode
            is LeaveLobbyRequest -> payload.lobbyCode
            is KickPlayerRequest -> payload.lobbyCode
            is StartGameRequest -> payload.lobbyCode
            is MapGetRequest -> payload.lobbyCode
            is TurnAdvanceRequest -> payload.lobbyCode
            else -> null
        }

    private fun currentTurnState(lobbyCode: LobbyCode): TurnState? =
        lobbyManager.getLobby(lobbyCode)?.currentState()?.turnState

    fun roundHistory(lobbyCode: LobbyCode): List<RoundHistory> = roundHistoryBuffer(lobbyCode).history()

    fun describeRoundHistory(lobbyCode: LobbyCode): String = roundHistoryBuffer(lobbyCode).describe()

    private suspend fun broadcastPhaseBoundaryIfChanged(
        lobbyCode: LobbyCode,
        previousTurnState: TurnState?,
    ) {
        val currentState = lobbyManager.getLobby(lobbyCode)?.currentState() ?: return
        val currentTurnState = currentState.turnState ?: return
        val previousPhase = previousTurnState?.turnPhase ?: return
        if (previousPhase == currentTurnState.turnPhase) {
            return
        }

        val payload =
            PhaseBoundaryEvent(
                lobbyCode = lobbyCode,
                stateVersion = currentState.stateVersion,
                previousPhase = previousPhase,
                nextPhase = currentTurnState.turnPhase,
                activePlayerId = currentTurnState.activePlayerId,
                turnCount = currentTurnState.turnCount,
            )

        logger.info(
            "Phase boundary broadcast: lobbyCode={} playerId={} stateVersion={} previousPhase={} nextPhase={} turnCount={}",
            lobbyCode.value,
            currentTurnState.activePlayerId.value,
            currentState.stateVersion,
            previousPhase.name,
            currentTurnState.turnPhase.name,
            currentTurnState.turnCount,
        )
        gameStateDelivery.broadcastPublicState(
            lobbyCode = lobbyCode,
            payload = payload,
        )
        roundHistoryBuffer(lobbyCode).recordBoundary(payload)
    }

    private suspend fun broadcastTurnStateIfChanged(
        lobbyCode: LobbyCode,
        previousTurnState: TurnState?,
        force: Boolean = false,
    ) {
        val currentState = lobbyManager.getLobby(lobbyCode)?.currentState() ?: return
        val currentTurnState = currentState.turnState ?: return
        if (!force && previousTurnState == currentTurnState) {
            return
        }

        logger.info(
            "Turn state changed: lobbyCode={} activePlayerId={} phase={} turnCount={} paused={} pausedPlayerId={}",
            lobbyCode.value,
            currentTurnState.activePlayerId.value,
            currentTurnState.turnPhase.name,
            currentTurnState.turnCount,
            currentTurnState.isPaused,
            currentTurnState.pausedPlayerId?.value,
        )
        val payload = currentTurnState.toUpdatedEvent(lobbyCode)
        gameStateDelivery.broadcastPublicState(
            lobbyCode = lobbyCode,
            payload = payload,
        )
        roundHistoryBuffer(lobbyCode).recordTurnStateChange(
            stateVersion = currentState.stateVersion,
            event = payload,
        )
    }

    private suspend fun broadcastFullSnapshotOnTurnChangeIfNeeded(
        lobbyCode: LobbyCode,
        previousTurnState: TurnState?,
    ) {
        val previousActivePlayerId = previousTurnState?.activePlayerId ?: return
        val currentState = lobbyManager.getLobby(lobbyCode)?.currentState() ?: return
        if (!currentState.hasMap()) {
            return
        }
        val currentTurnState = currentState.turnState ?: return
        if (previousActivePlayerId == currentTurnState.activePlayerId) {
            return
        }

        val payload = publicGameStateBuilder.buildSnapshotBroadcast(currentState)
        logger.info(
            "Public snapshot broadcast: lobbyCode={} playerId={} stateVersion={} turnCount={} mapHash={}",
            lobbyCode.value,
            currentTurnState.activePlayerId.value,
            payload.stateVersion,
            payload.turnState.turnCount,
            payload.determinism.mapHash,
        )
        gameStateDelivery.broadcastPublicState(
            lobbyCode = lobbyCode,
            payload = payload,
        )
        roundHistoryBuffer(lobbyCode).recordSnapshot(
            roundIndex = payload.turnState.turnCount,
            stateVersion = payload.stateVersion,
            trigger = RoundSnapshotTrigger.TURN_CHANGE_BROADCAST,
        )
    }

    private fun waitingForPlayerTurnStateEvent(
        lobbyCode: LobbyCode,
        turnState: TurnState,
        pausedPlayerId: PlayerId,
    ): TurnStateUpdatedEvent =
        turnState.toUpdatedEvent(
            lobbyCode = lobbyCode,
            isPaused = true,
            pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
            pausedPlayerId = pausedPlayerId,
        )

    private fun TurnState.toUpdatedEvent(
        lobbyCode: LobbyCode,
        isPaused: Boolean = this.isPaused,
        pauseReason: String? = this.pauseReason,
        pausedPlayerId: PlayerId? = this.pausedPlayerId,
    ): TurnStateUpdatedEvent =
        TurnStateUpdatedEvent(
            lobbyCode = lobbyCode,
            activePlayerId = activePlayerId,
            turnPhase = turnPhase,
            turnCount = turnCount,
            startPlayerId = startPlayerId,
            isPaused = isPaused,
            pauseReason = pauseReason,
            pausedPlayerId = pausedPlayerId,
        )

    private fun isPlayerConnected(playerId: PlayerId): Boolean = connectionIdResolver(playerId) != null

    private fun roundHistoryBuffer(lobbyCode: LobbyCode): RoundHistoryBuffer =
        roundHistoryByLobby.computeIfAbsent(lobbyCode) { RoundHistoryBuffer() }

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
