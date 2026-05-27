package at.aau.pulverfass.server.routing

import at.aau.pulverfass.server.ServerNetwork
import at.aau.pulverfass.server.lobby.CardSetValidator
import at.aau.pulverfass.server.lobby.mapping.DecodedNetworkRequest
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.persistence.LobbyPersistenceCallbacks
import at.aau.pulverfass.server.session.SessionContextRegistry
import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.command.AttackCommand
import at.aau.pulverfass.shared.lobby.command.DefaultFortifyMoveValidator
import at.aau.pulverfass.shared.lobby.command.DefaultMapCommandRuleService
import at.aau.pulverfass.shared.lobby.command.FortifyMoveCommand
import at.aau.pulverfass.shared.lobby.command.FortifyMoveValidationError
import at.aau.pulverfass.shared.lobby.command.FortifyMoveValidator
import at.aau.pulverfass.shared.lobby.command.InvalidMapCommandException
import at.aau.pulverfass.shared.lobby.command.MapCommandRuleService
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.CardSetTradedInEvent
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsSetEvent
import at.aau.pulverfass.shared.lobby.event.PlayerCardsRemovedEvent
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
import at.aau.pulverfass.shared.lobby.event.StartPlayerConfigured
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.BaseReinforcementRuleEngine
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TradeInProgression
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.lobby.state.TurnStateMachine
import at.aau.pulverfass.shared.message.connection.event.GlobalPlayerCountEvent
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostReason
import at.aau.pulverfass.shared.message.lobby.event.PlayerCountUpdateEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerHandUpdatedEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerKickedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.AttackRequest
import at.aau.pulverfass.shared.message.lobby.request.ConfirmAttackDoneRequest
import at.aau.pulverfass.shared.message.lobby.request.ConfirmReinforcementsDoneRequest
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.FortifyMoveRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.KickPlayerRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.LobbyPlayerCountRequest
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequest
import at.aau.pulverfass.shared.message.lobby.request.PlaceReinforcementsRequest
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.request.StartPlayerSetRequest
import at.aau.pulverfass.shared.message.lobby.request.TradeInCardsRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.AttackResponse
import at.aau.pulverfass.shared.message.lobby.response.ConfirmAttackDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.ConfirmReinforcementsDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.FortifyMoveResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStateCatchUpResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStatePrivateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.KickPlayerResponse
import at.aau.pulverfass.shared.message.lobby.response.LeaveLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.LobbyPlayerCountResponse
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import at.aau.pulverfass.shared.message.lobby.response.PlaceReinforcementsResponse
import at.aau.pulverfass.shared.message.lobby.response.StartGameResponse
import at.aau.pulverfass.shared.message.lobby.response.StartPlayerSetResponse
import at.aau.pulverfass.shared.message.lobby.response.TradeInCardsResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmAttackDoneErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmAttackDoneErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.FortifyMoveErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.FortifyMoveErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.KickPlayerErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.LobbyPlayerCountErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.LobbyPlayerCountErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartGameErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorResponse
import at.aau.pulverfass.shared.message.protocol.MessageType
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
    private val sessionContextRegistry: SessionContextRegistry? = null,
    private val playerIdResolver: (ConnectionId) -> PlayerId?,
    private val connectionIdResolver: (PlayerId) -> ConnectionId? = { null },
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val persistenceCallbacks: LobbyPersistenceCallbacks =
        LobbyPersistenceCallbacks.disabled(),
    private val fortifyMoveValidator: FortifyMoveValidator = DefaultFortifyMoveValidator(),
    private val mapCommandRuleService: MapCommandRuleService =
        DefaultMapCommandRuleService(fortifyMoveValidator = fortifyMoveValidator),
    private val hooks: MainServerLobbyRoutingServiceHooks = MainServerLobbyRoutingServiceHooks(),
) {
    private companion object {
        const val ELIMINATED_SPECTATOR_SUFFIX = "zuschauen."
        const val NO_ACTIVE_PLAYER_SET_SUFFIX = "kein aktiver Spieler gesetzt."
    }

    private val logger = LoggerFactory.getLogger(MainServerLobbyRoutingService::class.java)
    private val lifecycleLock = Any()
    private var routingJob: Job? = null
    private val gameStateDelivery =
        GameStateDeliveryDispatcher(
            sendPayload = network::send,
            lobbyMembers = {
                    lobbyCode ->
                lobbyManager.getLobby(lobbyCode)?.currentState()?.players.orEmpty()
            },
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
        runCatching { routeDecodedPacket(packet) }
            .onFailure { cause -> handlePacketRoutingFailure(packet, cause) }
    }

    private suspend fun routeDecodedPacket(packet: ReceivedPacket) {
        val request = decodeRequest(packet)
        when (val payload = request.payload) {
            is ReconnectRequest ->
                dispatchReconnectLobbySnapshot(
                    connectionId = packet.connectionId,
                    payload = payload,
                )
            is AttackRequest -> routeAttackRequest(request)
            is ConfirmAttackDoneRequest -> routeConfirmAttackDoneRequest(request)
            is ConfirmReinforcementsDoneRequest -> routeConfirmReinforcementsDoneRequest(request)
            is CreateLobbyRequest -> routeCreateLobbyRequest(packet)
            is LobbyPlayerCountRequest -> routeLobbyPlayerCountRequest(request)
            is MapGetRequest -> routeMapGetRequest(request)
            is GameStateCatchUpRequest -> routeGameStateCatchUpRequest(request)
            is GameStatePrivateGetRequest -> routeGameStatePrivateGetRequest(request)
            is FortifyMoveRequest -> routeFortifyMoveRequest(request)
            is PlaceReinforcementsRequest -> routePlaceReinforcementsRequest(request)
            is StartPlayerSetRequest -> routeStartPlayerSetRequest(request)
            is TradeInCardsRequest -> routeTradeInCardsRequest(request)
            is TurnStateGetRequest -> routeTurnStateGetRequest(request)
            is TurnAdvanceRequest -> routeTurnAdvanceRequest(request)
            else -> routeDecodedRequest(request)
        }
    }

    private fun handlePacketRoutingFailure(
        packet: ReceivedPacket,
        cause: Throwable,
    ) {
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

    /**
     * Reagiert auf den Verlust einer aktiven Spielerverbindung.
     *
     * Ein verspätetes Disconnect eines bereits ersetzten Sockets wird ignoriert,
     * damit Reconnects keine falschen `connection lost`-Broadcasts oder
     * Turn-Pausen auslösen.
     *
     * @param connectionId technisch getrennte Verbindung
     * @param playerId fachlich betroffener Spieler
     * @param reason optionaler technischer Close-Reason
     */
    suspend fun onPlayerDisconnected(
        connectionId: ConnectionId,
        playerId: PlayerId,
        reason: String?,
    ) {
        val currentConnectionId = connectionIdResolver(playerId)
        if (currentConnectionId != null && currentConnectionId != connectionId) {
            logger.info(
                "Ignoring stale disconnect after reconnect: playerId={} staleConnectionId={} " +
                    "currentConnectionId={}",
                playerId.value,
                connectionId.value,
                currentConnectionId.value,
            )
            return
        }

        val lobbyCode = lobbyManager.findLobbyCodeByPlayer(playerId) ?: return
        broadcastPlayerConnectionLost(
            lobbyCode = lobbyCode,
            playerId = playerId,
            reason = connectionLostReason(reason),
        )

        // broadcast updated player count to lobby members (disconnect may affect displayed online count)
        broadcastPlayerCount(lobbyCode)
        broadcastGlobalPlayerCount()

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

    /**
     * Reagiert auf eine neue oder wiederhergestellte Verbindung eines Spielers.
     *
     * Falls der laufende Zug wegen `WAITING_FOR_PLAYER` pausiert war und genau
     * dieser Spieler zurückkehrt, hebt der Server die Pause wieder auf.
     *
     * @param playerId fachlich identifizierter Spieler nach Connect/Reconnect
     */
    suspend fun onPlayerConnected(playerId: PlayerId) {
        resumeWaitingTurnForPlayer(playerId)
        val lobbyCode = lobbyManager.findLobbyCodeByPlayer(playerId)
        if (lobbyCode != null) {
            broadcastPlayerCount(lobbyCode)
        }
        broadcastGlobalPlayerCount()
    }

    private suspend fun resumeWaitingTurnForPlayer(playerId: PlayerId) {
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

    private suspend fun routeLobbyPlayerCountRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as LobbyPlayerCountRequest
        logger.info(
            "Routing messageType={} connectionId={} lobbyCode={}",
            request.receivedPacket.header.type.name,
            request.connectionId.value,
            payload.lobbyCode.value,
        )

        runCatching {
            val response = buildLobbyPlayerCountResponse(request, payload)
            network.send(request.connectionId, response)
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = lobbyPlayerCountErrorResponse(payload)
            logger.warn(
                "Routing failed messageType={} connectionId={} lobbyCode={} code={} reason={}",
                MessageType.LOBBY_PLAYER_COUNT_ERROR_RESPONSE.name,
                request.connectionId.value,
                payload.lobbyCode.value,
                error.code.name,
                error.reason,
            )
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
            grantBaseReinforcementsOnPhaseStart(
                lobbyCode = payload.lobbyCode,
                previousTurnState = previousTurnState,
                context = request.context,
            )
            network.send(request.connectionId, TurnAdvanceResponse(payload.lobbyCode))
            broadcastPhaseBoundaryIfChanged(payload.lobbyCode, previousTurnState)
            broadcastTurnStateIfChanged(payload.lobbyCode, previousTurnState)
            broadcastFullSnapshotOnTurnChangeIfNeeded(payload.lobbyCode, previousTurnState)
            autoAdvanceAttackPhaseIfNoValidAttacks(request, payload.lobbyCode, payload.playerId)
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

    private suspend fun routeConfirmAttackDoneRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as ConfirmAttackDoneRequest
        val previousTurnState = currentTurnState(payload.lobbyCode)

        runCatching {
            val turnStateUpdate = buildConfirmAttackDoneEvent(request, payload)
            lobbyManager.submit(turnStateUpdate, request.context)
            val updatedState =
                lobbyManager.getLobby(payload.lobbyCode)?.currentState()
                    ?: throw IllegalStateException("GAME_NOT_FOUND")
            network.send(
                request.connectionId,
                ConfirmAttackDoneResponse(payload.lobbyCode),
            )
            logger.info(
                "Attack phase manually confirmed: lobbyCode={} playerId={} nextPhase={} version={}",
                payload.lobbyCode.value,
                payload.playerId.value,
                turnStateUpdate.turnPhase.name,
                updatedState.stateVersion,
            )
            broadcastPhaseBoundaryIfChanged(payload.lobbyCode, previousTurnState)
            broadcastTurnStateIfChanged(payload.lobbyCode, previousTurnState)
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = confirmAttackDoneErrorResponse(request, payload, cause)
            logger.warn(
                "Attack phase confirm rejected: lobbyCode={} playerId={} reason={}",
                payload.lobbyCode.value,
                payload.playerId.value,
                error.code.name,
            )
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

    private suspend fun routeFortifyMoveRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as FortifyMoveRequest

        runCatching {
            val events = buildFortifyMoveEvents(request, payload)
            events.forEach { event -> lobbyManager.submit(event, request.context) }
            network.send(request.connectionId, FortifyMoveResponse(payload.lobbyCode))
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = fortifyMoveErrorResponse(request, payload, cause)
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

    private suspend fun routeConfirmReinforcementsDoneRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as ConfirmReinforcementsDoneRequest
        val previousTurnState = currentTurnState(payload.lobbyCode)

        runCatching {
            val turnStateUpdate = buildConfirmReinforcementsDoneEvent(request, payload)
            lobbyManager.submit(turnStateUpdate, request.context)
            network.send(
                request.connectionId,
                ConfirmReinforcementsDoneResponse(payload.lobbyCode),
            )
            broadcastPhaseBoundaryIfChanged(payload.lobbyCode, previousTurnState)
            broadcastTurnStateIfChanged(payload.lobbyCode, previousTurnState)
            autoAdvanceAttackPhaseIfNoValidAttacks(request, payload.lobbyCode, payload.playerId)
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = confirmReinforcementsDoneErrorResponse(request, payload, cause)
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

    private suspend fun routePlaceReinforcementsRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as PlaceReinforcementsRequest

        runCatching {
            val events = buildPlaceReinforcementsEvents(request, payload)
            lobbyManager.submitAll(payload.lobbyCode, events, request.context)
            network.send(
                request.connectionId,
                PlaceReinforcementsResponse(lobbyCode = payload.lobbyCode),
            )
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = placeReinforcementsErrorResponse(request, payload, cause)
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

    private suspend fun routeAttackRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as AttackRequest

        runCatching {
            val stateBeforeAttack =
                lobbyManager.getLobby(payload.lobbyCode)?.currentState()
                    ?: throw IllegalStateException("GAME_NOT_FOUND")
            val events = buildAttackEvents(request, payload)
            lobbyManager.submitAll(payload.lobbyCode, events, request.context)
            val updatedState =
                lobbyManager.getLobby(payload.lobbyCode)?.currentState()
                    ?: throw IllegalStateException("GAME_NOT_FOUND")
            val attackResult = summarizeAttackResult(events)
            network.send(
                request.connectionId,
                AttackResponse(
                    lobbyCode = payload.lobbyCode,
                    requestId = payload.requestId,
                ),
            )
            logger.info(
                "Attack resolved: lobbyCode={} from={} to={} attackTroops={} result={} version={}",
                payload.lobbyCode.value,
                payload.fromTerritoryId.value,
                payload.toTerritoryId.value,
                payload.attackTroops,
                attackResult,
                updatedState.stateVersion,
            )
            sendUpdatedHandsAfterEliminationIfNeeded(
                lobbyCode = payload.lobbyCode,
                stateBeforeAttack = stateBeforeAttack,
                events = events,
            )
            autoAdvanceAttackPhaseIfNoValidAttacks(request, payload.lobbyCode, payload.playerId)
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = attackErrorResponse(request, payload, cause)
            logger.warn(
                "Attack rejected: lobbyCode={} from={} to={} attackTroops={} reason={}",
                payload.lobbyCode.value,
                payload.fromTerritoryId.value,
                payload.toTerritoryId.value,
                payload.attackTroops,
                error.code.name,
            )
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

    private suspend fun routeTradeInCardsRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as TradeInCardsRequest

        runCatching {
            val events = buildTradeInCardsEvents(request, payload)
            lobbyManager.submitAll(payload.lobbyCode, events, request.context)
            network.send(
                request.connectionId,
                TradeInCardsResponse(lobbyCode = payload.lobbyCode),
            )
            val updatedState =
                lobbyManager.getLobby(payload.lobbyCode)?.currentState()
                    ?: throw IllegalStateException("GAME_NOT_FOUND")
            gameStateDelivery.sendPrivateState(
                payload.lobbyCode,
                PlayerHandUpdatedEvent.fromGameState(updatedState, payload.playerId),
            )
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = tradeInCardsErrorResponse(request, payload, cause)
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

    private suspend fun grantBaseReinforcementsOnPhaseStart(
        lobbyCode: LobbyCode,
        previousTurnState: TurnState?,
        context: EventContext,
    ) {
        val currentState = lobbyManager.getLobby(lobbyCode)?.currentState() ?: return
        val currentTurnState = currentState.resolvedTurnState ?: return
        if (currentTurnState.turnPhase != TurnPhase.REINFORCEMENTS) {
            return
        }
        // Skip if already in REINFORCEMENTS for the same player (no phase entry occurred)
        if (previousTurnState?.turnPhase == TurnPhase.REINFORCEMENTS &&
            previousTurnState.activePlayerId == currentTurnState.activePlayerId
        ) {
            return
        }

        val breakdown =
            BaseReinforcementRuleEngine.computeBaseReinforcements(
                playerId = currentTurnState.activePlayerId,
                state = currentState,
            )
        lobbyManager.submit(
            PendingReinforcementsSetEvent(
                lobbyCode = lobbyCode,
                playerId = currentTurnState.activePlayerId,
                amount = breakdown.total,
            ),
            context,
        )
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
                    if (request.payload is StartGameRequest) {
                        grantBaseReinforcementsOnPhaseStart(
                            lobbyCode = lobbyCode,
                            previousTurnState = previousTurnState,
                            context = request.context,
                        )
                    }
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

    private suspend fun dispatchReconnectLobbySnapshot(
        connectionId: ConnectionId,
        payload: ReconnectRequest,
    ) {
        if (resolveSessionToken(connectionId) != payload.sessionToken) {
            return
        }

        val reconnectContext =
            sessionContextRegistry?.contextFor(payload.sessionToken)
                ?: return
        val lobbyCode = reconnectContext.lobbyCode ?: return
        val reconnectingPlayerId = reconnectContext.playerId ?: return
        val lobbyState = lobbyManager.getLobby(lobbyCode)?.currentState() ?: return

        if (!lobbyState.players.contains(reconnectingPlayerId)) {
            return
        }

        /*
         * Ein echter Reconnect durchläuft keinen JoinRequest mehr. Deshalb muss
         * der reconnectende Client seine Lobby-Spielerliste erneut erhalten,
         * sonst kann die Android-App Owner-IDs aus dem GameState nicht auf Namen
         * und Farben abbilden. Es wird nur an die neue Verbindung gesendet; die
         * übrigen Clients kennen diese Spieler bereits.
         */
        lobbyState.players.forEach { playerId ->
            val playerDisplayName = lobbyState.playerDisplayNames[playerId] ?: return@forEach
            network.send(
                connectionId,
                PlayerJoinedLobbyEvent(
                    lobbyCode = lobbyCode,
                    playerId = playerId,
                    playerDisplayName = playerDisplayName,
                    isHost = lobbyState.lobbyOwner == playerId,
                ),
            )
        }

        resumeWaitingTurnForPlayer(reconnectingPlayerId)
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
        resolveSessionToken(request.connectionId)?.let { sessionToken ->
            sessionContextRegistry?.updateLobbyContext(
                sessionToken = sessionToken,
                lobbyCode = payload.lobbyCode,
                playerDisplayName = payload.playerDisplayName,
            )
        }

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

        // broadcast updated player count to lobby members
        val count = lobbyState.players.size
        members
            .mapNotNull(connectionIdResolver)
            .distinct()
            .forEach { connectionId ->
                network.send(connectionId, PlayerCountUpdateEvent(payload.lobbyCode, count))
            }
    }

    private suspend fun dispatchLeaveNetworkMessages(
        request: DecodedNetworkRequest,
        payload: LeaveLobbyRequest,
    ) {
        network.send(request.connectionId, LeaveLobbyResponse(payload.lobbyCode))

        val playerId = request.context.playerId ?: return
        resolveSessionToken(request.connectionId)?.let { sessionToken ->
            sessionContextRegistry?.clearLobbyContext(sessionToken)
        }
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

        // broadcast updated player count to lobby members
        val count = lobbyState.players.size
        members
            .mapNotNull(connectionIdResolver)
            .distinct()
            .forEach { connectionId ->
                network.send(connectionId, PlayerCountUpdateEvent(payload.lobbyCode, count))
            }
    }

    private suspend fun dispatchKickNetworkMessages(
        request: DecodedNetworkRequest,
        payload: KickPlayerRequest,
    ) {
        network.send(request.connectionId, KickPlayerResponse())
        sessionContextRegistry
            ?.sessionTokenForPlayer(payload.targetPlayerId)
            ?.let(sessionContextRegistry::clearLobbyContext)

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

        // broadcast updated player count to lobby members
        val count = members.size
        members
            .mapNotNull(connectionIdResolver)
            .distinct()
            .forEach { connectionId ->
                network.send(connectionId, PlayerCountUpdateEvent(payload.lobbyCode, count))
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
        val lobby =
            lobbyManager.getLobby(payload.lobbyCode)
                ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val playerId = request.context.playerId

        require(!(playerId == null || !state.hasPlayer(playerId))) { "NOT_IN_GAME" }
        check(state.hasMap()) { "MAP_NOT_READY" }

        return publicGameStateBuilder.buildMapGetResponse(state)
    }

    private fun buildGameStateCatchUpResponse(
        request: DecodedNetworkRequest,
        payload: GameStateCatchUpRequest,
    ): GameStateCatchUpResponse {
        val lobby =
            lobbyManager.getLobby(payload.lobbyCode)
                ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val playerId = request.context.playerId

        require(!(playerId == null || !state.hasPlayer(playerId))) { "NOT_IN_GAME" }
        check(!(!state.hasMap() || state.resolvedTurnState == null)) { "SNAPSHOT_NOT_READY" }

        val currentVersion = state.stateVersion
        val diff = currentVersion - payload.clientStateVersion
        logger.info(
            "GameState catch-up served: lobbyCode={} playerId={} requestedVersion={} " +
                "currentVersion={} versionDiff={} reason={} recentRounds={}",
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
        val lobby =
            lobbyManager.getLobby(payload.lobbyCode)
                ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val contextPlayerId = request.context.playerId

        require(!(contextPlayerId == null || contextPlayerId != payload.playerId)) {
            "REQUESTER_MISMATCH"
        }
        require(state.hasPlayer(payload.playerId)) { "NOT_IN_GAME" }

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
                        "Connection ist keinem Spieler in Lobby " +
                            "'${payload.lobbyCode.value}' zugeordnet."
                    } else {
                        "Spieler '${playerId.value}' ist nicht Teil von " +
                            "Lobby '${payload.lobbyCode.value}'."
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
                        "Connection ist keinem Spieler in Lobby " +
                            "'${payload.lobbyCode.value}' zugeordnet."
                    } else {
                        "Spieler '${playerId.value}' ist nicht Teil von " +
                            "Lobby '${payload.lobbyCode.value}'."
                    }
                }
                GameStateCatchUpErrorCode.SNAPSHOT_NOT_READY ->
                    "Catch-up-Snapshot für Lobby '${payload.lobbyCode.value}' " +
                        "ist noch nicht verfügbar."
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
                    "Spieler '${payload.playerId.value}' ist nicht Teil der " +
                        "Lobby '${payload.lobbyCode.value}'."
                GameStatePrivateGetErrorCode.REQUESTER_MISMATCH -> {
                    val contextPlayerId = request.context.playerId
                    if (contextPlayerId == null) {
                        "Connection ist keinem Spieler fuer Lobby " +
                            "'${payload.lobbyCode.value}' zugeordnet."
                    } else {
                        "Requester '${payload.playerId.value}' passt nicht " +
                            "zur aktuellen Connection '${contextPlayerId.value}'."
                    }
                }
            }

        return GameStatePrivateGetErrorResponse(code = code, reason = reason)
    }

    private fun buildTurnAdvanceEvent(
        request: DecodedNetworkRequest,
        payload: TurnAdvanceRequest,
    ): TurnStateUpdatedEvent {
        val lobby =
            lobbyManager.getLobby(payload.lobbyCode)
                ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val contextPlayerId = request.context.playerId
        val currentTurnState =
            state.resolvedTurnState
                ?: throw IllegalArgumentException("NOT_ACTIVE_PLAYER")

        require(!(contextPlayerId == null || contextPlayerId != payload.playerId)) {
            "NOT_ACTIVE_PLAYER"
        }
        requirePlayerCanActInMatch(state, payload.playerId)
        require(currentTurnState.activePlayerId == payload.playerId) { "NOT_ACTIVE_PLAYER" }
        check(!(currentTurnState.isPaused)) { "GAME_PAUSED" }
        require(
            !(
                payload.expectedPhase != null &&
                    payload.expectedPhase != currentTurnState.turnPhase
            ),
        ) {
            "PHASE_MISMATCH"
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

    private fun buildFortifyMoveEvents(
        request: DecodedNetworkRequest,
        payload: FortifyMoveRequest,
    ): List<LobbyEvent> {
        val state =
            lobbyManager.getLobby(payload.lobbyCode)?.currentState()
                ?: throw IllegalStateException("GAME_NOT_FOUND")
        val contextPlayerId = request.context.playerId

        require(!(contextPlayerId == null || contextPlayerId != payload.playerId)) {
            "REQUESTER_MISMATCH"
        }
        check(state.resolvedTurnState?.isPaused != true) { "GAME_PAUSED" }

        val validationError =
            fortifyMoveValidator.validateFortifyMove(
                state = state,
                playerId = payload.playerId,
                fromTerritoryId = payload.fromTerritoryId,
                toTerritoryId = payload.toTerritoryId,
                troopCount = payload.troopCount,
            )
        require(validationError == null) { validationError?.name.orEmpty() }

        return mapCommandRuleService.createEvents(
            state = state,
            command =
                FortifyMoveCommand(
                    lobbyCode = payload.lobbyCode,
                    playerId = payload.playerId,
                    fromTerritoryId = payload.fromTerritoryId,
                    toTerritoryId = payload.toTerritoryId,
                    troopCount = payload.troopCount,
                ),
        )
    }

    private fun buildPlaceReinforcementsEvents(
        request: DecodedNetworkRequest,
        payload: PlaceReinforcementsRequest,
    ): List<at.aau.pulverfass.shared.lobby.event.LobbyEvent> {
        val lobby =
            lobbyManager.getLobby(payload.lobbyCode)
                ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val contextPlayerId = request.context.playerId
        val currentTurnState =
            state.resolvedTurnState
                ?: throw IllegalArgumentException("NOT_ACTIVE_PLAYER")

        require(!(contextPlayerId == null || contextPlayerId != payload.playerId)) {
            "REQUESTER_MISMATCH"
        }
        requirePlayerCanActInMatch(state, payload.playerId)
        require(currentTurnState.activePlayerId == payload.playerId) { "NOT_ACTIVE_PLAYER" }
        check(!(currentTurnState.isPaused)) { "GAME_PAUSED" }
        require(currentTurnState.turnPhase == TurnPhase.REINFORCEMENTS) { "PHASE_MISMATCH" }
        val hand = state.handOf(payload.playerId)
        require(!requiresForcedTradeInOnReinforcementPhase(state, payload.playerId, hand)) {
            "FORCED_TRADE_REQUIRED"
        }
        require(payload.placements.isNotEmpty()) { "INVALID_PLACEMENT" }

        payload.placements.forEach { placement ->
            require(placement.amount > 0) { "INVALID_PLACEMENT" }
        }

        val totalPlacement =
            try {
                payload.placements.fold(0) { sum, placement ->
                    Math.addExact(sum, placement.amount)
                }
            } catch (_: ArithmeticException) {
                throw IllegalArgumentException("INVALID_PLACEMENT")
            }
        require(totalPlacement <= state.pendingReinforcementsFor(payload.playerId)) { "OVERSPEND" }

        val projectedTroopCounts = linkedMapOf<TerritoryId, Int>()
        val territoryEvents =
            payload.placements.map { placement ->
                require(state.territoryStateOf(placement.territoryId) != null) {
                    "INVALID_PLACEMENT"
                }
                val ownerId = state.ownerOf(placement.territoryId)
                require(ownerId == payload.playerId) { "TERRITORY_NOT_OWNED" }

                val currentTroopCount =
                    projectedTroopCounts[placement.territoryId]
                        ?: state.troopCountOf(placement.territoryId)
                val updatedTroopCount =
                    try {
                        Math.addExact(currentTroopCount, placement.amount)
                    } catch (_: ArithmeticException) {
                        throw IllegalArgumentException("INVALID_PLACEMENT")
                    }
                projectedTroopCounts[placement.territoryId] = updatedTroopCount

                TerritoryTroopsChangedEvent(
                    lobbyCode = payload.lobbyCode,
                    territoryId = placement.territoryId,
                    troopCount = updatedTroopCount,
                )
            }

        return territoryEvents +
            PendingReinforcementsChangedEvent(
                lobbyCode = payload.lobbyCode,
                playerId = payload.playerId,
                delta = -totalPlacement,
            )
    }

    private fun buildAttackEvents(
        request: DecodedNetworkRequest,
        payload: AttackRequest,
    ): List<at.aau.pulverfass.shared.lobby.event.LobbyEvent> {
        val lobby =
            lobbyManager.getLobby(payload.lobbyCode)
                ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val contextPlayerId = request.context.playerId
        val currentTurnState =
            state.resolvedTurnState
                ?: throw IllegalArgumentException("NOT_ACTIVE_PLAYER")

        require(!(contextPlayerId == null || contextPlayerId != payload.playerId)) {
            "REQUESTER_MISMATCH"
        }
        requirePlayerCanActInMatch(state, payload.playerId)
        require(currentTurnState.activePlayerId == payload.playerId) { "NOT_ACTIVE_PLAYER" }
        check(!(currentTurnState.isPaused)) { "GAME_PAUSED" }
        require(currentTurnState.turnPhase == TurnPhase.ATTACK) { "PHASE_MISMATCH" }
        require(payload.attackTroops >= 2) { "INVALID_REQUEST" }
        require(payload.moveAfterCapture > 0) { "INVALID_MOVE_AFTER_CAPTURE" }
        require(state.territoryStateOf(payload.fromTerritoryId) != null) { "INVALID_REQUEST" }
        require(state.territoryStateOf(payload.toTerritoryId) != null) { "INVALID_REQUEST" }
        require(state.ownerOf(payload.fromTerritoryId) == payload.playerId) {
            "FROM_TERRITORY_NOT_OWNED"
        }

        val defenderId = state.ownerOf(payload.toTerritoryId)
        require(defenderId != null) { "INVALID_REQUEST" }
        require(defenderId != payload.playerId) { "ATTACKING_OWN_TERRITORY" }
        require(state.isAdjacent(payload.fromTerritoryId, payload.toTerritoryId)) { "NOT_ADJACENT" }

        val sourceTroops = state.troopCountOf(payload.fromTerritoryId)
        require(payload.attackTroops <= sourceTroops - 1) { "INSUFFICIENT_TROOPS" }

        return try {
            mapCommandRuleService.createEvents(
                state = state,
                command =
                    AttackCommand(
                        lobbyCode = payload.lobbyCode,
                        playerId = payload.playerId,
                        fromTerritoryId = payload.fromTerritoryId,
                        toTerritoryId = payload.toTerritoryId,
                        requestedAttackDice = minOf(3, payload.attackTroops),
                        committedTroopCount = payload.attackTroops,
                        occupyingTroopCount = payload.moveAfterCapture,
                    ),
            )
        } catch (cause: InvalidMapCommandException) {
            if (cause.reasonCode != null) {
                throw IllegalArgumentException(cause.reasonCode, cause)
            }
            throw cause
        }
    }

    private fun buildTradeInCardsEvents(
        request: DecodedNetworkRequest,
        payload: TradeInCardsRequest,
    ): List<at.aau.pulverfass.shared.lobby.event.LobbyEvent> {
        val lobby =
            lobbyManager.getLobby(payload.lobbyCode)
                ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val contextPlayerId = request.context.playerId
        val currentTurnState =
            state.resolvedTurnState
                ?: throw IllegalArgumentException("NOT_ACTIVE_PLAYER")

        require(!(contextPlayerId == null || contextPlayerId != payload.playerId)) {
            "REQUESTER_MISMATCH"
        }
        requirePlayerCanActInMatch(state, payload.playerId)
        require(currentTurnState.activePlayerId == payload.playerId) { "NOT_ACTIVE_PLAYER" }
        check(!(currentTurnState.isPaused)) { "GAME_PAUSED" }
        require(currentTurnState.turnPhase == TurnPhase.REINFORCEMENTS) { "PHASE_MISMATCH" }
        require(payload.cardIds.size == 3) { "INVALID_REQUEST" }
        require(payload.cardIds.distinct().size == payload.cardIds.size) { "INVALID_REQUEST" }

        val cardsById = state.handOf(payload.playerId).associateBy { card -> card.cardId }
        val selectedCards =
            payload.cardIds.map { cardId ->
                cardsById[cardId] ?: throw IllegalArgumentException("CARDS_NOT_OWNED")
            }
        require(CardSetValidator.isValidSet(selectedCards.map { card -> card.type })) {
            "INVALID_SET"
        }

        val tradeIndex = state.tradedInSetCount + 1
        val tradeValue = TradeInProgression.tradeInValue(tradeIndex)

        return listOf(
            CardSetTradedInEvent(
                lobbyCode = payload.lobbyCode,
                playerId = payload.playerId,
                cardIds = payload.cardIds,
                value = tradeValue,
                tradeIndex = tradeIndex,
            ),
            PendingReinforcementsChangedEvent(
                lobbyCode = payload.lobbyCode,
                playerId = payload.playerId,
                delta = tradeValue,
            ),
            PlayerCardsRemovedEvent(
                lobbyCode = payload.lobbyCode,
                playerId = payload.playerId,
                cardIds = payload.cardIds,
            ),
        )
    }

    private fun buildConfirmAttackDoneEvent(
        request: DecodedNetworkRequest,
        payload: ConfirmAttackDoneRequest,
    ): TurnStateUpdatedEvent =
        buildTurnAdvanceEvent(
            request = request,
            payload =
                TurnAdvanceRequest(
                    lobbyCode = payload.lobbyCode,
                    playerId = payload.playerId,
                    expectedPhase = TurnPhase.ATTACK,
                ),
        )

    private fun buildConfirmReinforcementsDoneEvent(
        request: DecodedNetworkRequest,
        payload: ConfirmReinforcementsDoneRequest,
    ): TurnStateUpdatedEvent {
        val lobby =
            lobbyManager.getLobby(payload.lobbyCode)
                ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val contextPlayerId = request.context.playerId
        val currentTurnState =
            state.resolvedTurnState
                ?: throw IllegalArgumentException("NOT_ACTIVE_PLAYER")

        require(!(contextPlayerId == null || contextPlayerId != payload.playerId)) {
            "REQUESTER_MISMATCH"
        }
        requirePlayerCanActInMatch(state, payload.playerId)
        require(currentTurnState.activePlayerId == payload.playerId) { "NOT_ACTIVE_PLAYER" }
        check(!(currentTurnState.isPaused)) { "GAME_PAUSED" }
        require(currentTurnState.turnPhase == TurnPhase.REINFORCEMENTS) { "PHASE_MISMATCH" }
        val hand = state.handOf(payload.playerId)
        require(!requiresForcedTradeInOnReinforcementPhase(state, payload.playerId, hand)) {
            "FORCED_TRADE_REQUIRED"
        }
        require(state.pendingReinforcementsFor(payload.playerId) == 0) {
            "PENDING_REINFORCEMENTS_REMAINING"
        }

        return buildTurnAdvanceEvent(
            request = request,
            payload =
                TurnAdvanceRequest(
                    lobbyCode = payload.lobbyCode,
                    playerId = payload.playerId,
                    expectedPhase = TurnPhase.REINFORCEMENTS,
                ),
        )
    }

    private fun buildStartPlayerConfiguredEvent(
        request: DecodedNetworkRequest,
        payload: StartPlayerSetRequest,
    ): StartPlayerConfigured {
        val lobby =
            lobbyManager.getLobby(payload.lobbyCode)
                ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val contextPlayerId = request.context.playerId

        require(!(contextPlayerId == null || contextPlayerId != payload.requesterPlayerId)) {
            "REQUESTER_MISMATCH"
        }
        check(
            !(
                state.gameStarted ||
                    state.status == at.aau.pulverfass.shared.lobby.state.GameStatus.RUNNING
            ),
        ) { "GAME_ALREADY_STARTED" }
        require(state.lobbyOwner == payload.requesterPlayerId) { "NOT_HOST" }
        require(state.hasPlayer(payload.startPlayerId)) { "PLAYER_NOT_IN_LOBBY" }

        return StartPlayerConfigured(
            lobbyCode = payload.lobbyCode,
            startPlayerId = payload.startPlayerId,
            requesterPlayerId = payload.requesterPlayerId,
        )
    }

    private fun buildTurnStateGetResponse(payload: TurnStateGetRequest): TurnStateGetResponse {
        val lobby =
            lobbyManager.getLobby(payload.lobbyCode)
                ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        return TurnStateGetResponse.fromGameState(state).also { response ->
            logger.info(
                "Turn state snapshot served: lobbyCode={} activePlayerId={} " +
                    "phase={} turnCount={} paused={}",
                response.lobbyCode.value,
                response.activePlayerId.value,
                response.turnPhase.name,
                response.turnCount,
                response.isPaused,
            )
        }
    }

    private fun buildLobbyPlayerCountResponse(
        request: DecodedNetworkRequest,
        payload: LobbyPlayerCountRequest,
    ): LobbyPlayerCountResponse {
        val state =
            lobbyManager.getLobby(payload.lobbyCode)?.currentState()
                ?: throw IllegalStateException("LOBBY_NOT_FOUND")

        return LobbyPlayerCountResponse(
            lobbyCode = payload.lobbyCode,
            playerCount = state.players.size,
        ).also { response ->
            logger.info(
                "Sent messageType={} connectionId={} lobbyCode={} playerCount={}",
                MessageType.LOBBY_PLAYER_COUNT_RESPONSE.name,
                request.connectionId.value,
                response.lobbyCode.value,
                response.playerCount,
            )
        }
    }

    private suspend fun broadcastPlayerCount(lobbyCode: LobbyCode) {
        val count = lobbyManager.getLobby(lobbyCode)?.currentState()?.players?.size ?: 0
        lobbyManager.getLobby(lobbyCode)
            ?.currentState()
            ?.players
            .orEmpty()
            .mapNotNull(connectionIdResolver)
            .distinct()
            .forEach { connectionId ->
                network.send(connectionId, PlayerCountUpdateEvent(lobbyCode, count))
            }
    }

    private suspend fun broadcastGlobalPlayerCount() {
        val count = network.connectionManager.all().size
        val event = GlobalPlayerCountEvent(playerCount = count)
        network.connectionManager.broadcast(MessageCodec.encode(event))
    }

    private fun fortifyMoveErrorResponse(
        request: DecodedNetworkRequest,
        payload: FortifyMoveRequest,
        cause: Throwable,
    ): FortifyMoveErrorResponse {
        val code =
            when (cause.message) {
                "GAME_NOT_FOUND" -> FortifyMoveErrorCode.GAME_NOT_FOUND
                "REQUESTER_MISMATCH" -> FortifyMoveErrorCode.REQUESTER_MISMATCH
                "GAME_PAUSED" -> FortifyMoveErrorCode.GAME_PAUSED
                FortifyMoveValidationError.NOT_ACTIVE_PLAYER.name ->
                    FortifyMoveErrorCode.NOT_ACTIVE_PLAYER
                FortifyMoveValidationError.WRONG_PHASE.name -> FortifyMoveErrorCode.WRONG_PHASE
                FortifyMoveValidationError.TERRITORY_NOT_OWNED.name ->
                    FortifyMoveErrorCode.TERRITORY_NOT_OWNED
                FortifyMoveValidationError.NO_PATH.name -> FortifyMoveErrorCode.NO_PATH
                FortifyMoveValidationError.INSUFFICIENT_TROOPS.name ->
                    FortifyMoveErrorCode.INSUFFICIENT_TROOPS
                FortifyMoveValidationError.FORTIFY_ALREADY_USED.name ->
                    FortifyMoveErrorCode.FORTIFY_ALREADY_USED
                else -> FortifyMoveErrorCode.NOT_ACTIVE_PLAYER
            }

        val reason =
            when (code) {
                FortifyMoveErrorCode.GAME_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
                FortifyMoveErrorCode.REQUESTER_MISMATCH -> {
                    val contextPlayerId = request.context.playerId
                    if (contextPlayerId == null) {
                        "Connection ist keinem Spieler für Lobby " +
                            "'${payload.lobbyCode.value}' zugeordnet."
                    } else {
                        "Requester '${payload.playerId.value}' passt nicht " +
                            "zur aktuellen Connection '${contextPlayerId.value}'."
                    }
                }
                FortifyMoveErrorCode.GAME_PAUSED ->
                    "Lobby '${payload.lobbyCode.value}' ist pausiert; " +
                        "Fortify ist aktuell nicht erlaubt."
                FortifyMoveErrorCode.NOT_ACTIVE_PLAYER -> {
                    val activePlayer =
                        lobbyManager.getLobby(payload.lobbyCode)?.currentState()?.activePlayer
                    "Nur der aktive Spieler '${activePlayer?.value}' darf Fortify ausführen."
                }
                FortifyMoveErrorCode.WRONG_PHASE -> {
                    val currentPhase =
                        lobbyManager.getLobby(payload.lobbyCode)?.currentState()?.activeTurnPhase
                    "Fortify ist nur in Phase 'FORTIFY' erlaubt, aktuell ist " +
                        "'${currentPhase?.name}'."
                }
                FortifyMoveErrorCode.TERRITORY_NOT_OWNED ->
                    "Fortify von '${payload.fromTerritoryId.value}' nach " +
                        "'${payload.toTerritoryId.value}' erfordert zwei eigene Territorien."
                FortifyMoveErrorCode.NO_PATH ->
                    "Fortify von '${payload.fromTerritoryId.value}' nach " +
                        "'${payload.toTerritoryId.value}' benötigt einen zusammenhängenden " +
                        "Pfad über eigene Gebiete."
                FortifyMoveErrorCode.INSUFFICIENT_TROOPS -> {
                    val sourceTroops =
                        lobbyManager.getLobby(payload.lobbyCode)
                            ?.currentState()
                            ?.territoryStateOf(payload.fromTerritoryId)
                            ?.troopCount
                    "Fortify von '${payload.fromTerritoryId.value}' nach " +
                        "'${payload.toTerritoryId.value}' muss mindestens eine Truppe " +
                        "zurücklassen: vorhanden=$sourceTroops, bewegt=${payload.troopCount}."
                }
                FortifyMoveErrorCode.FORTIFY_ALREADY_USED ->
                    "Fortify wurde in diesem Zug bereits verwendet."
            }

        return FortifyMoveErrorResponse(code = code, reason = reason)
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
                    "Lobby '${payload.lobbyCode.value}' ist pausiert; " +
                        "Turn-Wechsel ist aktuell nicht erlaubt."
                TurnAdvanceErrorCode.PHASE_MISMATCH -> {
                    val expectedPhase = payload.expectedPhase
                    val currentPhase =
                        lobbyManager.getLobby(
                            payload.lobbyCode,
                        )?.currentState()?.activeTurnPhase
                    if (expectedPhase == null || currentPhase == null) {
                        "Die erwartete Phase stimmt nicht mit dem " +
                            "autoritativen Serverzustand überein."
                    } else {
                        "Erwartete Phase '${expectedPhase.name}', aktueller " +
                            "Serverzustand ist '${currentPhase.name}'."
                    }
                }
                TurnAdvanceErrorCode.NOT_ACTIVE_PLAYER -> {
                    val currentState = lobbyManager.getLobby(payload.lobbyCode)?.currentState()
                    if (currentState?.isSpectator(payload.playerId) == true) {
                        spectatorOnlyReason(payload.playerId)
                    } else {
                        val activePlayer = currentState?.activePlayer
                        val contextPlayerId = request.context.playerId
                        when {
                            contextPlayerId == null ->
                                "Connection ist keinem aktiven Spieler für Lobby " +
                                    "'${payload.lobbyCode.value}' zugeordnet."
                            contextPlayerId != payload.playerId ->
                                "Requester '${payload.playerId.value}' passt nicht " +
                                    "zur aktuellen Connection."
                            activePlayer == null ->
                                noActivePlayerConfigured(payload.lobbyCode)
                            else ->
                                "Nur der aktive Spieler '${activePlayer.value}' " +
                                    "darf den Turn-State fortschalten."
                        }
                    }
                }
            }

        return TurnAdvanceErrorResponse(code = code, reason = reason)
    }

    private fun placeReinforcementsErrorResponse(
        request: DecodedNetworkRequest,
        payload: PlaceReinforcementsRequest,
        cause: Throwable,
    ): PlaceReinforcementsErrorResponse {
        val code =
            when (cause.message) {
                "GAME_NOT_FOUND" -> PlaceReinforcementsErrorCode.GAME_NOT_FOUND
                "REQUESTER_MISMATCH" -> PlaceReinforcementsErrorCode.REQUESTER_MISMATCH
                "GAME_PAUSED" -> PlaceReinforcementsErrorCode.GAME_PAUSED
                "PHASE_MISMATCH" -> PlaceReinforcementsErrorCode.PHASE_MISMATCH
                "TERRITORY_NOT_OWNED" -> PlaceReinforcementsErrorCode.TERRITORY_NOT_OWNED
                "OVERSPEND" -> PlaceReinforcementsErrorCode.OVERSPEND
                "INVALID_PLACEMENT" -> PlaceReinforcementsErrorCode.INVALID_PLACEMENT
                "FORCED_TRADE_REQUIRED" -> PlaceReinforcementsErrorCode.FORCED_TRADE_REQUIRED
                else -> PlaceReinforcementsErrorCode.NOT_ACTIVE_PLAYER
            }

        val reason =
            when (code) {
                PlaceReinforcementsErrorCode.GAME_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
                PlaceReinforcementsErrorCode.REQUESTER_MISMATCH -> {
                    val contextPlayerId = request.context.playerId
                    if (contextPlayerId == null) {
                        connectionNotAssignedToLobby(payload.lobbyCode)
                    } else {
                        "Requester '${payload.playerId.value}' passt nicht " +
                            "zur aktuellen Connection '${contextPlayerId.value}'."
                    }
                }
                PlaceReinforcementsErrorCode.NOT_ACTIVE_PLAYER -> {
                    val currentState = lobbyManager.getLobby(payload.lobbyCode)?.currentState()
                    if (currentState?.isSpectator(payload.playerId) == true) {
                        spectatorOnlyReason(payload.playerId)
                    } else {
                        val activePlayer = currentState?.activePlayer
                        when {
                            activePlayer == null ->
                                noActivePlayerConfigured(payload.lobbyCode)
                            else ->
                                "Nur der aktive Spieler '${activePlayer.value}' darf in der " +
                                    "Reinforcements-Phase Truppen platzieren."
                        }
                    }
                }
                PlaceReinforcementsErrorCode.GAME_PAUSED ->
                    "Lobby '${payload.lobbyCode.value}' ist pausiert; " +
                        "Truppenplatzierung ist aktuell nicht erlaubt."
                PlaceReinforcementsErrorCode.PHASE_MISMATCH -> {
                    val currentPhase =
                        lobbyManager.getLobby(
                            payload.lobbyCode,
                        )?.currentState()?.activeTurnPhase
                    if (currentPhase == null) {
                        "Die Reinforcements-Phase ist für Lobby '${payload.lobbyCode.value}' " +
                            "aktuell nicht aktiv."
                    } else {
                        "Truppenplatzierung ist nur in Phase 'REINFORCEMENTS' erlaubt, " +
                            "aktueller Serverzustand ist '${currentPhase.name}'."
                    }
                }
                PlaceReinforcementsErrorCode.TERRITORY_NOT_OWNED ->
                    "Alle Zielterritorien müssen Spieler '${payload.playerId.value}' gehören."
                PlaceReinforcementsErrorCode.OVERSPEND -> {
                    val remainingPending =
                        lobbyManager
                            .getLobby(payload.lobbyCode)
                            ?.currentState()
                            ?.pendingReinforcementsFor(payload.playerId)
                            ?: 0
                    "Angeforderte Verstärkungen (${payload.placements.sumOf { it.amount }}) " +
                        "überschreiten den verbleibenden Pool von $remainingPending."
                }
                PlaceReinforcementsErrorCode.INVALID_PLACEMENT ->
                    "Die Verstärkungsplatzierung muss mindestens ein Ziel enthalten und alle " +
                        "Mengen müssen positiv sein."
                PlaceReinforcementsErrorCode.FORCED_TRADE_REQUIRED ->
                    "Truppenplatzierung ist gesperrt: Spieler '${payload.playerId.value}' hat " +
                        "mindestens 5 Karten und kann ein Set abgeben."
            }

        return PlaceReinforcementsErrorResponse(code = code, reason = reason)
    }

    private fun attackErrorResponse(
        request: DecodedNetworkRequest,
        payload: AttackRequest,
        cause: Throwable,
    ): AttackErrorResponse {
        val code =
            when (cause.message) {
                "GAME_NOT_FOUND" -> AttackErrorCode.GAME_NOT_FOUND
                "REQUESTER_MISMATCH" -> AttackErrorCode.REQUESTER_MISMATCH
                "NOT_ACTIVE_PLAYER" -> AttackErrorCode.NOT_ACTIVE_PLAYER
                "PLAYER_ELIMINATED" -> AttackErrorCode.NOT_ACTIVE_PLAYER
                "GAME_PAUSED" -> AttackErrorCode.GAME_PAUSED
                "PHASE_MISMATCH" -> AttackErrorCode.PHASE_MISMATCH
                "FROM_TERRITORY_NOT_OWNED" -> AttackErrorCode.FROM_TERRITORY_NOT_OWNED
                "ATTACKING_OWN_TERRITORY" -> AttackErrorCode.ATTACKING_OWN_TERRITORY
                "NOT_ADJACENT" -> AttackErrorCode.NOT_ADJACENT
                "INSUFFICIENT_TROOPS" -> AttackErrorCode.INSUFFICIENT_TROOPS
                "INVALID_MOVE_AFTER_CAPTURE" -> AttackErrorCode.INVALID_MOVE_AFTER_CAPTURE
                else -> AttackErrorCode.INVALID_REQUEST
            }

        val reason =
            when (code) {
                AttackErrorCode.GAME_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
                AttackErrorCode.REQUESTER_MISMATCH -> {
                    val contextPlayerId = request.context.playerId
                    if (contextPlayerId == null) {
                        connectionNotAssignedToLobby(payload.lobbyCode)
                    } else {
                        "Requester '${payload.playerId.value}' passt nicht zur aktuellen " +
                            "Connection '${contextPlayerId.value}'."
                    }
                }
                AttackErrorCode.NOT_ACTIVE_PLAYER -> {
                    val currentState = lobbyManager.getLobby(payload.lobbyCode)?.currentState()
                    if (currentState?.isSpectator(payload.playerId) == true) {
                        spectatorOnlyReason(payload.playerId)
                    } else {
                        val activePlayer = currentState?.activePlayer
                        when {
                            activePlayer == null ->
                                noActivePlayerConfigured(payload.lobbyCode)
                            else ->
                                "Nur der aktive Spieler '${activePlayer.value}' darf angreifen."
                        }
                    }
                }
                AttackErrorCode.GAME_PAUSED ->
                    "Lobby '${payload.lobbyCode.value}' ist pausiert; Angriffe sind aktuell " +
                        "nicht erlaubt."
                AttackErrorCode.PHASE_MISMATCH -> {
                    val currentPhase =
                        lobbyManager.getLobby(payload.lobbyCode)?.currentState()?.activeTurnPhase
                    if (currentPhase == null) {
                        "Die Attack-Phase ist für Lobby '${payload.lobbyCode.value}' aktuell " +
                            "nicht aktiv."
                    } else {
                        "Angriffe sind nur in Phase 'ATTACK' erlaubt, aktueller " +
                            "Serverzustand ist '${currentPhase.name}'."
                    }
                }
                AttackErrorCode.FROM_TERRITORY_NOT_OWNED ->
                    "Das Ursprungsterritorium '${payload.fromTerritoryId.value}' gehört nicht " +
                        "Spieler '${payload.playerId.value}'."
                AttackErrorCode.ATTACKING_OWN_TERRITORY ->
                    "Das Zielterritorium '${payload.toTerritoryId.value}' gehört bereits dem " +
                        "angreifenden Spieler."
                AttackErrorCode.NOT_ADJACENT ->
                    "Ein Angriff ist nur zwischen direkt benachbarten Territorien erlaubt."
                AttackErrorCode.INSUFFICIENT_TROOPS -> {
                    val sourceTroops =
                        lobbyManager
                            .getLobby(payload.lobbyCode)
                            ?.currentState()
                            ?.territoryStateOf(payload.fromTerritoryId)
                            ?.troopCount
                            ?: 0
                    "Für den Angriff müssen mindestens ${payload.attackTroops + 1} Truppen auf " +
                        "'${payload.fromTerritoryId.value}' stehen; vorhanden sind $sourceTroops."
                }
                AttackErrorCode.INVALID_MOVE_AFTER_CAPTURE ->
                    cause.cause?.message
                        ?: cause.message
                        ?: "moveAfterCapture ist für eine erfolgreiche Eroberung ungültig."
                AttackErrorCode.INVALID_REQUEST ->
                    "Der Angriff ist ungültig. Prüfe Territorien, Truppenzahl und Anfrageformat."
            }

        return AttackErrorResponse(
            code = code,
            reason = reason,
            requestId = payload.requestId,
        )
    }

    private fun tradeInCardsErrorResponse(
        request: DecodedNetworkRequest,
        payload: TradeInCardsRequest,
        cause: Throwable,
    ): TradeInCardsErrorResponse {
        val code =
            when (cause.message) {
                "GAME_NOT_FOUND" -> TradeInCardsErrorCode.GAME_NOT_FOUND
                "REQUESTER_MISMATCH" -> TradeInCardsErrorCode.REQUESTER_MISMATCH
                "GAME_PAUSED" -> TradeInCardsErrorCode.GAME_PAUSED
                "PHASE_MISMATCH" -> TradeInCardsErrorCode.PHASE_MISMATCH
                "CARDS_NOT_OWNED" -> TradeInCardsErrorCode.CARDS_NOT_OWNED
                "INVALID_SET" -> TradeInCardsErrorCode.INVALID_SET
                "INVALID_REQUEST" -> TradeInCardsErrorCode.INVALID_REQUEST
                else -> TradeInCardsErrorCode.NOT_ACTIVE_PLAYER
            }

        val reason =
            when (code) {
                TradeInCardsErrorCode.GAME_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
                TradeInCardsErrorCode.REQUESTER_MISMATCH -> {
                    val contextPlayerId = request.context.playerId
                    if (contextPlayerId == null) {
                        "Connection ist keinem Spieler fuer Lobby " +
                            "'${payload.lobbyCode.value}' zugeordnet."
                    } else {
                        "Requester '${payload.playerId.value}' passt nicht " +
                            "zur aktuellen Connection '${contextPlayerId.value}'."
                    }
                }
                TradeInCardsErrorCode.NOT_ACTIVE_PLAYER -> {
                    val currentState = lobbyManager.getLobby(payload.lobbyCode)?.currentState()
                    if (currentState?.isSpectator(payload.playerId) == true) {
                        spectatorOnlyReason(payload.playerId)
                    } else {
                        val activePlayer = currentState?.activePlayer
                        when {
                            activePlayer == null ->
                                noActivePlayerConfiguredAscii(payload.lobbyCode)
                            else ->
                                "Nur der aktive Spieler '${activePlayer.value}' darf " +
                                    "waehrend der Reinforcements-Phase Karten eintauschen."
                        }
                    }
                }
                TradeInCardsErrorCode.GAME_PAUSED ->
                    "Lobby '${payload.lobbyCode.value}' ist pausiert; Karten-Trade-In ist " +
                        "aktuell nicht erlaubt."
                TradeInCardsErrorCode.PHASE_MISMATCH -> {
                    val currentPhase =
                        lobbyManager.getLobby(payload.lobbyCode)?.currentState()?.activeTurnPhase
                    if (currentPhase == null) {
                        "Die Reinforcements-Phase ist fuer Lobby '${payload.lobbyCode.value}' " +
                            "aktuell nicht aktiv."
                    } else {
                        "Karten-Trade-In ist nur in Phase 'REINFORCEMENTS' erlaubt, " +
                            "aktueller Serverzustand ist '${currentPhase.name}'."
                    }
                }
                TradeInCardsErrorCode.CARDS_NOT_OWNED ->
                    "Alle eingetauschten Karten muessen Spieler " +
                        "'${payload.playerId.value}' gehoeren."
                TradeInCardsErrorCode.INVALID_SET ->
                    "Die ausgewaehlten Karten bilden kein gueltiges Trade-In-Set."
                TradeInCardsErrorCode.INVALID_REQUEST ->
                    "Ein Karten-Trade-In muss genau drei unterschiedliche Karten enthalten."
            }

        return TradeInCardsErrorResponse(code = code, reason = reason)
    }

    private fun confirmAttackDoneErrorResponse(
        request: DecodedNetworkRequest,
        payload: ConfirmAttackDoneRequest,
        cause: Throwable,
    ): ConfirmAttackDoneErrorResponse {
        val code =
            when (cause.message) {
                "GAME_NOT_FOUND" -> ConfirmAttackDoneErrorCode.GAME_NOT_FOUND
                "REQUESTER_MISMATCH" -> ConfirmAttackDoneErrorCode.REQUESTER_MISMATCH
                "GAME_PAUSED" -> ConfirmAttackDoneErrorCode.GAME_PAUSED
                "PHASE_MISMATCH" -> ConfirmAttackDoneErrorCode.PHASE_MISMATCH
                else -> ConfirmAttackDoneErrorCode.NOT_ACTIVE_PLAYER
            }

        val reason =
            when (code) {
                ConfirmAttackDoneErrorCode.GAME_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
                ConfirmAttackDoneErrorCode.REQUESTER_MISMATCH -> {
                    val contextPlayerId = request.context.playerId
                    if (contextPlayerId == null) {
                        connectionNotAssignedToLobby(payload.lobbyCode)
                    } else {
                        "Requester '${payload.playerId.value}' passt nicht " +
                            "zur aktuellen Connection '${contextPlayerId.value}'."
                    }
                }
                ConfirmAttackDoneErrorCode.NOT_ACTIVE_PLAYER -> {
                    val currentState = lobbyManager.getLobby(payload.lobbyCode)?.currentState()
                    if (currentState?.isSpectator(payload.playerId) == true) {
                        spectatorOnlyReason(payload.playerId)
                    } else {
                        val activePlayer = currentState?.activePlayer
                        when {
                            activePlayer == null ->
                                noActivePlayerConfigured(payload.lobbyCode)
                            else ->
                                "Nur der aktive Spieler '${activePlayer.value}' darf " +
                                    "die Attack-Phase beenden."
                        }
                    }
                }
                ConfirmAttackDoneErrorCode.GAME_PAUSED ->
                    "Lobby '${payload.lobbyCode.value}' ist pausiert; " +
                        "Phasenwechsel ist aktuell nicht erlaubt."
                ConfirmAttackDoneErrorCode.PHASE_MISMATCH -> {
                    val currentPhase =
                        lobbyManager.getLobby(payload.lobbyCode)?.currentState()?.activeTurnPhase
                    if (currentPhase == null) {
                        "Die Attack-Phase ist für Lobby '${payload.lobbyCode.value}' aktuell " +
                            "nicht aktiv."
                    } else {
                        "Bestätigung ist nur in Phase 'ATTACK' erlaubt, aktueller " +
                            "Serverzustand ist '${currentPhase.name}'."
                    }
                }
            }

        return ConfirmAttackDoneErrorResponse(code = code, reason = reason)
    }

    private fun confirmReinforcementsDoneErrorResponse(
        request: DecodedNetworkRequest,
        payload: ConfirmReinforcementsDoneRequest,
        cause: Throwable,
    ): ConfirmReinforcementsDoneErrorResponse {
        val code =
            when (cause.message) {
                "GAME_NOT_FOUND" -> ConfirmReinforcementsDoneErrorCode.GAME_NOT_FOUND
                "REQUESTER_MISMATCH" -> ConfirmReinforcementsDoneErrorCode.REQUESTER_MISMATCH
                "GAME_PAUSED" -> ConfirmReinforcementsDoneErrorCode.GAME_PAUSED
                "PHASE_MISMATCH" -> ConfirmReinforcementsDoneErrorCode.PHASE_MISMATCH
                "PENDING_REINFORCEMENTS_REMAINING" ->
                    ConfirmReinforcementsDoneErrorCode.PENDING_REINFORCEMENTS_REMAINING
                "FORCED_TRADE_REQUIRED" -> ConfirmReinforcementsDoneErrorCode.FORCED_TRADE_REQUIRED
                else -> ConfirmReinforcementsDoneErrorCode.NOT_ACTIVE_PLAYER
            }

        val reason =
            when (code) {
                ConfirmReinforcementsDoneErrorCode.GAME_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
                ConfirmReinforcementsDoneErrorCode.REQUESTER_MISMATCH -> {
                    val contextPlayerId = request.context.playerId
                    if (contextPlayerId == null) {
                        connectionNotAssignedToLobby(payload.lobbyCode)
                    } else {
                        "Requester '${payload.playerId.value}' passt nicht " +
                            "zur aktuellen Connection '${contextPlayerId.value}'."
                    }
                }
                ConfirmReinforcementsDoneErrorCode.NOT_ACTIVE_PLAYER -> {
                    val currentState = lobbyManager.getLobby(payload.lobbyCode)?.currentState()
                    if (currentState?.isSpectator(payload.playerId) == true) {
                        spectatorOnlyReason(payload.playerId)
                    } else {
                        val activePlayer = currentState?.activePlayer
                        when {
                            activePlayer == null ->
                                noActivePlayerConfigured(payload.lobbyCode)
                            else ->
                                "Nur der aktive Spieler '${activePlayer.value}' darf " +
                                    "die Reinforcements-Phase beenden."
                        }
                    }
                }
                ConfirmReinforcementsDoneErrorCode.GAME_PAUSED ->
                    "Lobby '${payload.lobbyCode.value}' ist pausiert; " +
                        "Phasenwechsel ist aktuell nicht erlaubt."
                ConfirmReinforcementsDoneErrorCode.PHASE_MISMATCH -> {
                    val currentPhase =
                        lobbyManager.getLobby(payload.lobbyCode)?.currentState()?.activeTurnPhase
                    if (currentPhase == null) {
                        "Die Reinforcements-Phase ist für Lobby " +
                            "'${payload.lobbyCode.value}' aktuell nicht aktiv."
                    } else {
                        "Bestätigung ist nur in Phase 'REINFORCEMENTS' erlaubt, " +
                            "aktueller Serverzustand ist '${currentPhase.name}'."
                    }
                }
                ConfirmReinforcementsDoneErrorCode.PENDING_REINFORCEMENTS_REMAINING ->
                    "Die Reinforcements-Phase kann erst beendet werden, wenn " +
                        "keine ausstehenden Verstärkungen mehr vorhanden sind."
                ConfirmReinforcementsDoneErrorCode.FORCED_TRADE_REQUIRED ->
                    "Die Reinforcements-Phase kann erst beendet werden, wenn " +
                        "die Pflichtabgabe von Karten erfüllt ist."
            }

        return ConfirmReinforcementsDoneErrorResponse(code = code, reason = reason)
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
                    "Nur der Lobby Owner darf den Startspieler für Lobby " +
                        "'${payload.lobbyCode.value}' setzen."
                StartPlayerSetErrorCode.PLAYER_NOT_IN_LOBBY ->
                    "Spieler '${payload.startPlayerId.value}' ist nicht Teil " +
                        "der Lobby '${payload.lobbyCode.value}'."
                StartPlayerSetErrorCode.GAME_ALREADY_STARTED ->
                    "Der Startspieler kann für Lobby " +
                        "'${payload.lobbyCode.value}' nach Spielstart nicht " +
                        "mehr geändert werden."
                StartPlayerSetErrorCode.REQUESTER_MISMATCH -> {
                    val contextPlayerId = request.context.playerId
                    if (contextPlayerId == null) {
                        connectionNotAssignedToLobby(payload.lobbyCode)
                    } else {
                        "Requester '${payload.requesterPlayerId.value}' passt " +
                            "nicht zur aktuellen Connection '${contextPlayerId.value}'."
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

    private fun lobbyPlayerCountErrorResponse(
        payload: LobbyPlayerCountRequest,
    ): LobbyPlayerCountErrorResponse {
        val code = LobbyPlayerCountErrorCode.LOBBY_NOT_FOUND

        val reason =
            when (code) {
                LobbyPlayerCountErrorCode.LOBBY_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
            }

        return LobbyPlayerCountErrorResponse(
            lobbyCode = payload.lobbyCode,
            code = code,
            reason = reason,
        )
    }

    private suspend fun broadcastAcceptedLobbyEvent(
        lobbyCode: LobbyCode,
        event: at.aau.pulverfass.shared.lobby.event.LobbyEvent,
        previousState: GameState,
        currentState: GameState,
    ) {
        persistenceCallbacks.onLobbyEventAccepted(
            event = event,
            previousState = previousState,
            currentState = currentState,
        )
        val publicDelta =
            publicGameStateBuilder.buildDelta(
                lobbyCode,
                event,
                previousState,
                currentState,
            )
        if (publicDelta != null) {
            val currentTurnCount = currentState.resolvedTurnState?.turnCount
            logger.info(
                "Public delta broadcast: lobbyCode={} playerId={} fromVersion={} " +
                    "toVersion={} stateVersion={} turnCount={} eventCount={}",
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
            is AttackRequest -> payload.lobbyCode
            is FortifyMoveRequest -> payload.lobbyCode
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

    fun roundHistory(lobbyCode: LobbyCode): List<RoundHistory> =
        roundHistoryBuffer(
            lobbyCode,
        ).history()

    fun describeRoundHistory(lobbyCode: LobbyCode): String =
        roundHistoryBuffer(
            lobbyCode,
        ).describe()

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
            "Phase boundary broadcast: lobbyCode={} playerId={} stateVersion={} " +
                "previousPhase={} nextPhase={} turnCount={}",
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
        persistenceCallbacks.onPhaseBoundaryBroadcast(payload)
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
            "Turn state changed: lobbyCode={} activePlayerId={} phase={} " +
                "turnCount={} paused={} pausedPlayerId={}",
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
            "Public snapshot broadcast: lobbyCode={} playerId={} stateVersion={} " +
                "turnCount={} mapHash={}",
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
        persistenceCallbacks.onSnapshotBroadcast(
            currentState = currentState,
            payload = payload,
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

    private suspend fun broadcastPlayerConnectionLost(
        lobbyCode: LobbyCode,
        playerId: PlayerId,
        reason: PlayerConnectionLostReason,
    ) {
        val members = lobbyManager.getLobby(lobbyCode)?.currentState()?.players.orEmpty()
        val event =
            PlayerConnectionLostEvent(
                lobbyCode = lobbyCode,
                playerId = playerId,
                reason = reason,
            )

        logger.info(
            "Broadcasting messageType={} lobbyCode={} playerId={} reason={}",
            MessageType.LOBBY_PLAYER_CONNECTION_LOST_BROADCAST.name,
            lobbyCode.value,
            playerId.value,
            reason.name,
        )

        members
            .filter { memberId -> memberId != playerId }
            .mapNotNull(connectionIdResolver)
            .distinct()
            .forEach { activeConnectionId ->
                network.send(activeConnectionId, event)
            }
    }

    private fun connectionLostReason(reason: String?): PlayerConnectionLostReason {
        val normalizedReason = reason?.lowercase().orEmpty()
        return if (
            normalizedReason.contains("ping") ||
            normalizedReason.contains("pong") ||
            normalizedReason.contains("timeout")
        ) {
            PlayerConnectionLostReason.HEARTBEAT_TIMEOUT
        } else {
            PlayerConnectionLostReason.SOCKET_CLOSED
        }
    }

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

    private fun isPlayerConnected(playerId: PlayerId): Boolean =
        connectionIdResolver(playerId) != null

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

    private fun resolveSessionToken(connectionId: ConnectionId) =
        network.sessionManager.getByConnectionId(connectionId)?.sessionToken

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

    private fun connectionNotAssignedToLobby(lobbyCode: LobbyCode): String =
        "Connection ist keinem Spieler für Lobby '${lobbyCode.value}' zugeordnet."

    private fun spectatorOnlyReason(playerId: PlayerId): String =
        "Spieler '${playerId.value}' ist eliminiert und kann nur noch $ELIMINATED_SPECTATOR_SUFFIX"

    private fun noActivePlayerConfigured(lobbyCode: LobbyCode): String =
        "Für Lobby '${lobbyCode.value}' ist aktuell $NO_ACTIVE_PLAYER_SET_SUFFIX"

    private fun noActivePlayerConfiguredAscii(lobbyCode: LobbyCode): String =
        "Fuer Lobby '${lobbyCode.value}' ist aktuell $NO_ACTIVE_PLAYER_SET_SUFFIX"

    private fun requirePlayerCanActInMatch(
        state: GameState,
        playerId: PlayerId,
    ) {
        require(!state.isSpectator(playerId)) { "PLAYER_ELIMINATED" }
    }

    private fun requiresForcedTradeInOnReinforcementPhase(
        state: GameState,
        playerId: PlayerId,
        hand: List<at.aau.pulverfass.shared.lobby.state.CardState> = state.handOf(playerId),
    ): Boolean =
        state.tradeRequiredOnNextReinforcementPhaseFor(playerId) ||
            (hand.size >= 5 && CardSetValidator.canMakeAnySet(hand))

    private suspend fun sendUpdatedHandsAfterEliminationIfNeeded(
        lobbyCode: LobbyCode,
        stateBeforeAttack: GameState,
        events: List<at.aau.pulverfass.shared.lobby.event.LobbyEvent>,
    ) {
        val eliminationEvents =
            events.filterIsInstance<PlayerEliminatedEvent>()
                .filter { event -> stateBeforeAttack.handOf(event.playerId).isNotEmpty() }
        if (eliminationEvents.isEmpty()) {
            return
        }

        val updatedState =
            lobbyManager.getLobby(lobbyCode)?.currentState()
                ?: throw IllegalStateException("GAME_NOT_FOUND")
        eliminationEvents
            .flatMap { event -> listOf(event.eliminatedByPlayerId, event.playerId) }
            .distinct()
            .forEach { playerId ->
                gameStateDelivery.sendPrivateState(
                    lobbyCode,
                    PlayerHandUpdatedEvent.fromGameState(updatedState, playerId),
                )
            }
    }

    private suspend fun autoAdvanceAttackPhaseIfNoValidAttacks(
        request: DecodedNetworkRequest,
        lobbyCode: LobbyCode,
        playerId: PlayerId,
    ) {
        val state =
            lobbyManager.getLobby(lobbyCode)?.currentState()
                ?: return
        val currentTurnState = state.resolvedTurnState ?: return
        if (
            state.status != GameStatus.RUNNING ||
            currentTurnState.isPaused ||
            currentTurnState.turnPhase != TurnPhase.ATTACK ||
            currentTurnState.activePlayerId != playerId ||
            state.hasAnyValidAttack(playerId)
        ) {
            return
        }

        val previousTurnState = currentTurnState
        val turnStateUpdate =
            buildTurnAdvanceEvent(
                request = request,
                payload =
                    TurnAdvanceRequest(
                        lobbyCode = lobbyCode,
                        playerId = playerId,
                        expectedPhase = TurnPhase.ATTACK,
                    ),
            )
        lobbyManager.submit(turnStateUpdate, request.context)
        val updatedState =
            lobbyManager.getLobby(lobbyCode)?.currentState()
                ?: return
        logger.info(
            "Attack phase auto-advanced: lobbyCode={} playerId={} nextPhase={} version={}",
            lobbyCode.value,
            playerId.value,
            turnStateUpdate.turnPhase.name,
            updatedState.stateVersion,
        )
        broadcastPhaseBoundaryIfChanged(lobbyCode, previousTurnState)
        broadcastTurnStateIfChanged(lobbyCode, previousTurnState)
    }

    private fun summarizeAttackResult(
        events: List<at.aau.pulverfass.shared.lobby.event.LobbyEvent>,
    ): String {
        val resolved = events.filterIsInstance<AttackResolvedEvent>().firstOrNull()
        val eliminated = events.any { it is PlayerEliminatedEvent }
        if (resolved == null) {
            return if (eliminated) "elimination" else "unknown"
        }

        return when {
            eliminated -> "elimination"
            resolved.capture -> "capture"
            else -> "battle"
        }
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
