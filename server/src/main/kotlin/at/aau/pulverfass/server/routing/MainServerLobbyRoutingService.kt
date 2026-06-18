package at.aau.pulverfass.server.routing

import at.aau.pulverfass.server.ServerNetwork
import at.aau.pulverfass.server.WebSocketPolicy
import at.aau.pulverfass.server.lobby.CardSetValidator
import at.aau.pulverfass.server.lobby.mapping.DecodedNetworkRequest
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.logging.ServerLoggers
import at.aau.pulverfass.server.persistence.LobbyPersistenceCallbacks
import at.aau.pulverfass.server.session.SessionContextRegistry
import at.aau.pulverfass.shared.event.CorrelationId
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
import at.aau.pulverfass.shared.lobby.command.MIN_ATTACK_COMMITTED_TROOPS
import at.aau.pulverfass.shared.lobby.command.MapCommandRuleService
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.CardDrawnEvent
import at.aau.pulverfass.shared.lobby.event.CardSetTradedInEvent
import at.aau.pulverfass.shared.lobby.event.CheatReinforcementBonusUsedEvent
import at.aau.pulverfass.shared.lobby.event.LobbyCreated
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.event.MatchEndReason
import at.aau.pulverfass.shared.lobby.event.MatchEndedEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsSetEvent
import at.aau.pulverfass.shared.lobby.event.PlayerCardsRemovedEvent
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
import at.aau.pulverfass.shared.lobby.event.StartPlayerConfigured
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.normalizePlayerDisplayName
import at.aau.pulverfass.shared.lobby.normalizePlayerDisplayNameOrFallback
import at.aau.pulverfass.shared.lobby.state.BaseReinforcementRuleEngine
import at.aau.pulverfass.shared.lobby.state.CardState
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.lobby.state.TradeInProgression
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.lobby.state.TurnStateMachine
import at.aau.pulverfass.shared.message.connection.ConnectionStatus
import at.aau.pulverfass.shared.message.connection.event.GlobalPlayerCountEvent
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.lobby.event.CharacterSelectedBroadcast
import at.aau.pulverfass.shared.message.lobby.event.ConnectionStatusUpdateEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostReason
import at.aau.pulverfass.shared.message.lobby.event.PlayerCountUpdateEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerHandUpdatedEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerKickedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PrivateGameStatePayload
import at.aau.pulverfass.shared.message.lobby.event.PublicGameStatePayload
import at.aau.pulverfass.shared.message.lobby.request.AttackRequest
import at.aau.pulverfass.shared.message.lobby.request.CharacterSelectRequest
import at.aau.pulverfass.shared.message.lobby.request.ClaimCheatReinforcementBonusRequest
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
import at.aau.pulverfass.shared.message.lobby.request.ReportCheatRequest
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.request.StartPlayerSetRequest
import at.aau.pulverfass.shared.message.lobby.request.TradeInCardsRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.AttackResponse
import at.aau.pulverfass.shared.message.lobby.response.CharacterSelectResponse
import at.aau.pulverfass.shared.message.lobby.response.ClaimCheatReinforcementBonusResponse
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
import at.aau.pulverfass.shared.message.lobby.response.ReportCheatResponse
import at.aau.pulverfass.shared.message.lobby.response.StartGameResponse
import at.aau.pulverfass.shared.message.lobby.response.StartPlayerSetResponse
import at.aau.pulverfass.shared.message.lobby.response.TradeInCardsResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CharacterSelectErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ClaimCheatReinforcementBonusErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ClaimCheatReinforcementBonusErrorResponse
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
import at.aau.pulverfass.shared.message.lobby.response.error.ReportCheatErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ReportCheatErrorResponse
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
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
    private val publicStatePayloadMaxBytes: Int = WebSocketPolicy.MAX_FRAME_SIZE_BYTES.toInt(),
    private val privateStatePayloadMaxBytes: Int = WebSocketPolicy.MAX_FRAME_SIZE_BYTES.toInt(),
    private val fortifyMoveValidator: FortifyMoveValidator = DefaultFortifyMoveValidator(),
    private val mapCommandRuleService: MapCommandRuleService =
        DefaultMapCommandRuleService(fortifyMoveValidator = fortifyMoveValidator),
    private val attackAutoAdvanceDelayMillis: Long = ATTACK_AUTO_ADVANCE_DELAY_MILLIS,
    private val hooks: MainServerLobbyRoutingServiceHooks = MainServerLobbyRoutingServiceHooks(),
) {
    private companion object {
        const val ELIMINATED_SPECTATOR_SUFFIX = "zuschauen."
        const val NO_ACTIVE_PLAYER_SET_SUFFIX = "kein aktiver Spieler gesetzt."
        const val PAYLOAD_LIMIT_EXCEEDED_SUFFIX =
            "ueberschreitet die konfigurierte Transportgrenze."
        const val PAYLOAD_LIMIT_DETAILS_PREFIX =
            "als die konfigurierte Grenze von "
        const val REINFORCEMENTS_PHASE_END_BLOCKED_PREFIX =
            "Die Reinforcements-Phase kann erst beendet werden, wenn "
        const val RECONNECT_SNAPSHOT_SKIPPED_WITH_LOBBY_PREFIX =
            "Reconnect snapshot skipped connectionId={} lobbyCode={} "
        const val CREATE_LOBBY_ERROR_CODE = "CREATE_LOBBY_FAILED"
        const val ROUTING_ERROR_CODE = "ROUTING_ERROR"
        const val CHARACTER_ALREADY_ASSIGNED_ERROR_CODE = "CHARACTER_ALREADY_ASSIGNED"
        const val PLAYER_CONTEXT_MISSING_ERROR_CODE = "PLAYER_CONTEXT_MISSING"
        const val ATTACK_AUTO_ADVANCE_DELAY_MILLIS = 2_500L

        /*
         * Cheat-Meldungen haben nur ein kurzes Zeitfenster. Die Idee dahinter:
         * Andere Spieler sollen den Cheat melden können, sobald er sichtbar wird,
         * aber niemand soll viele Runden später noch rückwirkend Bonus erhalten.
         */
        const val CHEAT_REPORT_WINDOW_MILLIS = 20_000L
        const val CHEAT_REPORT_REWARD = 3
        const val CHEAT_REPORT_PENALTY = -3
    }

    /**
     * Serverinterner Marker für ein offenes Meldefenster.
     *
     * Es wird nur der Ablaufzeitpunkt gespeichert. Ein eigener Timer wäre hier
     * unnötig, weil erst beim Eintreffen einer Meldung geprüft werden muss, ob
     * das Fenster noch gültig ist.
     */
    private data class CheatReportWindow(
        val expiresAtMillis: Long,
    )

    /**
     * Eindeutiger Schlüssel für "dieser Reporter hat diesen Cheater bereits
     * korrekt gemeldet".
     *
     * Der Schummel-Verstärkungsbonus selbst ist pro Spieler einmalig. Der
     * Schlüssel enthält deshalb bewusst keine Ablaufzeit: Eine frühe Meldung
     * vor der sichtbaren Platzierung und eine spätere Meldung im Meldefenster
     * gehören zur selben Cheat-Aktion und dürfen nicht doppelt belohnt werden.
     */
    private data class CheatReportKey(
        val reporterPlayerId: PlayerId,
        val accusedPlayerId: PlayerId,
    )

    /**
     * Ergebnis der serverseitigen Auswertung einer Cheat-Meldung.
     *
     * [correct] steuert die UI-Rückmeldung. [modifierDelta] ist der konkrete
     * Bonus oder Malus, der für die nächste Verstärkungsphase des Reporters
     * vorgemerkt wird.
     */
    private data class CheatReportResult(
        val correct: Boolean,
        val modifierDelta: Int,
    )

    /**
     * Zusammenfassung aller vorgemerkten Cheat-Folgen für den aktiven Spieler.
     *
     * [modifier] betrifft den Reporter einer Meldung. [zeroReinforcements]
     * betrifft den Spieler, der korrekt beim Cheaten erwischt wurde.
     */
    private data class CheatReinforcementAdjustment(
        val modifier: Int,
        val zeroReinforcements: Boolean,
    )

    private val logger = ServerLoggers.technical("MainServerLobbyRoutingService")
    private val lifecycleLock = Any()
    private var routingJob: Job? = null
    private val requestSequence = AtomicLong(1)
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
    private val charactersByLobby = ConcurrentHashMap<LobbyCode, ConcurrentHashMap<String, Long>>()

    private data class TurnAdvancePlan(
        val events: List<LobbyEvent>,
        val drawnCardEvent: CardDrawnEvent? = null,
    ) {
        val matchEnded: Boolean = events.any { event -> event is MatchEndedEvent }
    }

    /*
     * Die Cheat-Meldelogik bleibt auf dem Server.
     * Die App darf nur melden, aber nicht selbst entscheiden, ob die Meldung stimmt.
     * Alle zugehörigen Maps werden gemeinsam unter diesem Lock verändert, damit
     * parallele Reports keine doppelten oder widersprüchlichen Boni erzeugen.
     */
    private val cheatReportLock = Any()

    /*
     * Nach dem Cheat ist der Vorteil für andere Spieler noch nicht sicher sichtbar.
     * Deshalb wartet der Server bis zur nächsten Verstärkungsplatzierung dieses Spielers.
     * Erst dann können die anderen Spieler den Verdacht überhaupt sehen.
     */
    private val pendingVisibleCheatByLobby = mutableMapOf<LobbyCode, MutableSet<PlayerId>>()

    /*
     * Speichert pro Lobby, welcher Spieler gerade ein offenes 20-Sekunden-Meldefenster hat.
     * Es wird kein eigener Timer gestartet; beim Melden wird einfach die Ablaufzeit geprüft.
     * Das spart einen Hintergrundjob und reicht für diese Regel völlig aus.
     */
    private val cheatReportWindowsByLobby =
        mutableMapOf<LobbyCode, MutableMap<PlayerId, CheatReportWindow>>()

    /*
     * Merkt sich, wer eine konkrete Cheat-Aktion schon gemeldet hat.
     * Dadurch kann derselbe Spieler nicht mehrfach für denselben Cheat-Bonus bekommen.
     */
    private val cheatReportsByLobby = mutableMapOf<LobbyCode, MutableSet<CheatReportKey>>()

    /*
     * Bonus oder Malus wird nicht sofort angewendet, sondern erst in der nächsten
     * Verstärkungsphase des meldenden Spielers.
     * Mehrere falsche Meldungen addieren sich, werden später aber bei 0 begrenzt.
     */
    private val nextReinforcementModifierByLobby =
        mutableMapOf<LobbyCode, MutableMap<PlayerId, Int>>()

    /*
     * Wenn ein Spieler korrekt beim Cheaten erwischt wird, bekommt er in seiner
     * nächsten Verstärkungsphase 0 Truppen. Auch diese Strafe bleibt am Server.
     */
    private val nextZeroReinforcementPenaltyByLobby =
        mutableMapOf<LobbyCode, MutableSet<PlayerId>>()

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
        logRequestReceived(request)
        when (val payload = request.payload) {
            is ReconnectRequest ->
                dispatchReconnectLobbySnapshot(
                    connectionId = packet.connectionId,
                    payload = payload,
                )
            is AttackRequest -> routeAttackRequest(request)
            is ClaimCheatReinforcementBonusRequest ->
                routeClaimCheatReinforcementBonusRequest(request)
            is ConfirmAttackDoneRequest -> routeConfirmAttackDoneRequest(request)
            is ConfirmReinforcementsDoneRequest -> routeConfirmReinforcementsDoneRequest(request)
            is CreateLobbyRequest -> routeCreateLobbyRequest(request)
            is LobbyPlayerCountRequest -> routeLobbyPlayerCountRequest(request)
            is MapGetRequest -> routeMapGetRequest(request)
            is GameStateCatchUpRequest -> routeGameStateCatchUpRequest(request)
            is GameStatePrivateGetRequest -> routeGameStatePrivateGetRequest(request)
            is FortifyMoveRequest -> routeFortifyMoveRequest(request)
            is PlaceReinforcementsRequest -> routePlaceReinforcementsRequest(request)
            is ReportCheatRequest -> routeReportCheatRequest(request)
            is StartPlayerSetRequest -> routeStartPlayerSetRequest(request)
            is TradeInCardsRequest -> routeTradeInCardsRequest(request)
            is TurnStateGetRequest -> routeTurnStateGetRequest(request)
            is TurnAdvanceRequest -> routeTurnAdvanceRequest(request)
            is CharacterSelectRequest -> routeCharacterSelectRequest(request)
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
                    correlationId = CorrelationId("srv-${requestSequence.getAndIncrement()}"),
                ),
        )
    }

    private fun logRequestReceived(request: DecodedNetworkRequest) {
        logger.info("Request received {}", requestLogFields(request))
    }

    private fun logRequestCompleted(
        request: DecodedNetworkRequest,
        responseType: String,
        lobbyCode: LobbyCode? = lobbyCodeOf(request.payload),
        extraFields: Array<Pair<String, Any?>> = emptyArray(),
    ) {
        logger.info(
            "Request completed {}",
            requestLogFields(
                request = request,
                lobbyCode = lobbyCode,
                extraFields =
                    arrayOf(
                        "responseType" to responseType,
                        *extraFields,
                    ),
            ),
        )
    }

    private fun logRequestRejected(
        request: DecodedNetworkRequest,
        errorCode: String,
        reason: String,
        cause: Throwable,
        lobbyCode: LobbyCode? = lobbyCodeOf(request.payload),
    ) {
        val fields =
            requestLogFields(
                request = request,
                lobbyCode = lobbyCode,
                extraFields =
                    arrayOf(
                        "errorCode" to errorCode,
                        "reason" to reason,
                    ),
            )

        if (isUnexpectedRoutingCause(cause)) {
            logger.warn("Request rejected {}", fields, cause)
        } else {
            logger.warn("Request rejected {}", fields)
        }
    }

    private fun isUnexpectedRoutingCause(cause: Throwable): Boolean =
        cause !is IllegalArgumentException && cause !is IllegalStateException

    private fun requestLogFields(
        request: DecodedNetworkRequest,
        lobbyCode: LobbyCode? = lobbyCodeOf(request.payload),
        playerId: PlayerId? = playerIdOf(request.payload) ?: request.context.playerId,
        extraFields: Array<Pair<String, Any?>> = emptyArray(),
    ): String =
        logFields(
            "connectionId" to request.connectionId.value,
            "playerId" to playerId?.value,
            "lobbyId" to lobbyCode?.value,
            "messageType" to request.header.type.name,
            "requestId" to request.context.correlationId?.value,
            "clientRequestId" to clientRequestIdOf(request.payload),
            *extraFields,
        )

    private fun logFields(vararg fields: Pair<String, Any?>): String =
        fields.joinToString(separator = " ") { (key, value) ->
            "$key=${formatLogValue(value)}"
        }

    private fun formatLogValue(value: Any?): String =
        when (value) {
            null -> "null"
            is String ->
                if (value.any(Char::isWhitespace)) {
                    "\"${value.replace("\"", "\\\"")}\""
                } else {
                    value
                }
            else -> value.toString()
        }

    private suspend fun routeCreateLobbyRequest(request: DecodedNetworkRequest) {
        runCatching {
            val lobbyCode = handleCreateLobbyRequest(request)
            logRequestCompleted(
                request = request,
                responseType = CreateLobbyResponse::class.simpleName ?: "CreateLobbyResponse",
                lobbyCode = lobbyCode,
            )
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val reason = cause.message ?: "Lobby konnte nicht erstellt werden."
            dispatchCreateErrorResponse(
                connectionId = request.connectionId,
                reason = reason,
            )
            logRequestRejected(
                request = request,
                errorCode = CREATE_LOBBY_ERROR_CODE,
                reason = reason,
                cause = cause,
            )
            hooks.onRoutingError(
                request.connectionId,
                LobbyRoutingError.InvalidRoutingData(
                    reason = reason,
                    context =
                        LobbyRoutingContext(
                            connectionId = request.connectionId,
                            messageType = request.receivedPacket.header.type,
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
        broadcastConnectionStatusUpdate(
            lobbyCode = lobbyCode,
            playerId = playerId,
            status = ConnectionStatus.DISCONNECTED,
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
     * Aktualisiert den globalen Verbindungszähler nach einem technischen Socket-Connect.
     *
     * Fachliche Spielerzustände werden erst nach Join oder Reconnect aktualisiert,
     * damit ein verzögert verarbeitetes Connect-Event keinen Reconnect doppelt meldet.
     */
    suspend fun onConnectionOpened() {
        broadcastGlobalPlayerCount()
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
            broadcastConnectionStatusUpdate(
                lobbyCode = lobbyCode,
                playerId = playerId,
                status = ConnectionStatus.CONNECTED,
            )
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
            requirePublicStatePayloadWithinLimit(response)
            gameStateDelivery.sendPublicState(request.connectionId, response)
            logRequestCompleted(
                request = request,
                responseType = MapGetResponse::class.simpleName ?: "MapGetResponse",
                extraFields =
                    arrayOf(
                        "stateVersion" to response.stateVersion,
                        "mapHash" to response.mapHash,
                    ),
            )
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = mapGetErrorResponse(request, payload, cause)
            network.send(request.connectionId, error)
            logRequestRejected(
                request = request,
                errorCode = error.code.name,
                reason = error.reason,
                cause = cause,
            )
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
            requirePublicStatePayloadWithinLimit(response)
            gameStateDelivery.sendPublicState(request.connectionId, response)
            roundHistoryBuffer(payload.lobbyCode).recordSnapshot(
                roundIndex = response.turnState.turnCount,
                stateVersion = response.stateVersion,
                trigger = RoundSnapshotTrigger.CATCH_UP_RESPONSE,
            )
            logRequestCompleted(
                request = request,
                responseType =
                    GameStateCatchUpResponse::class.simpleName ?: "GameStateCatchUpResponse",
                extraFields =
                    arrayOf(
                        "stateVersion" to response.stateVersion,
                        "clientStateVersion" to payload.clientStateVersion,
                    ),
            )
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = gameStateCatchUpErrorResponse(request, payload, cause)
            network.send(request.connectionId, error)
            logRequestRejected(
                request = request,
                errorCode = error.code.name,
                reason = error.reason,
                cause = cause,
            )
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
            requirePrivateStatePayloadWithinLimit(response)
            gameStateDelivery.sendPrivateState(request.connectionId, response)
            logRequestCompleted(
                request = request,
                responseType =
                    GameStatePrivateGetResponse::class.simpleName ?: "GameStatePrivateGetResponse",
                extraFields = arrayOf("stateVersion" to response.stateVersion),
            )
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = gameStatePrivateGetErrorResponse(request, payload, cause)
            network.send(request.connectionId, error)
            logRequestRejected(
                request = request,
                errorCode = error.code.name,
                reason = error.reason,
                cause = cause,
            )
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
            val turnAdvancePlan = buildTurnAdvancePlan(request, payload)
            val matchEnded = turnAdvancePlan.matchEnded
            lobbyManager.submitAll(
                payload.lobbyCode,
                turnAdvancePlan.events,
                request.context,
            )
            if (turnAdvancePlan.drawnCardEvent != null) {
                val updatedState =
                    lobbyManager.getLobby(payload.lobbyCode)?.currentState()
                        ?: throw IllegalStateException("GAME_NOT_FOUND")
                sendPrivateStateUpdateBestEffort(
                    lobbyCode = payload.lobbyCode,
                    payload = PlayerHandUpdatedEvent.fromGameState(updatedState, payload.playerId),
                    context = "draw-card private hand update",
                )
            }
            if (!matchEnded) {
                grantBaseReinforcementsOnPhaseStart(
                    lobbyCode = payload.lobbyCode,
                    previousTurnState = previousTurnState,
                    context = request.context,
                )
            }
            network.send(request.connectionId, TurnAdvanceResponse(payload.lobbyCode))
            if (!matchEnded) {
                broadcastPhaseBoundaryIfChanged(payload.lobbyCode, previousTurnState)
                broadcastTurnStateIfChanged(payload.lobbyCode, previousTurnState)
                broadcastFullSnapshotOnTurnChangeIfNeeded(payload.lobbyCode, previousTurnState)
                autoAdvanceAttackPhaseIfNoValidAttacks(request, payload.lobbyCode, payload.playerId)
            }
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

    /**
     * Routet eine einmalige Truppenverschiebung in der Fortify-Phase.
     *
     * Der Move und das verbrauchte Fortify-Flag werden als Batch submitted,
     * damit kein anderer Request einen Zwischenzustand nach dem Move, aber vor
     * dem Used-Flag, beobachten oder verändern kann.
     *
     * @param request dekodierter Netzwerkrequest mit Lobby- und Spieler-Kontext
     */
    private suspend fun routeFortifyMoveRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as FortifyMoveRequest

        runCatching {
            val events = buildFortifyMoveEvents(request, payload)
            lobbyManager.submitAll(payload.lobbyCode, events, request.context)
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

    private suspend fun routeClaimCheatReinforcementBonusRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as ClaimCheatReinforcementBonusRequest

        runCatching {
            /*
             * Hier wird aus dem App-Signal ("Lichtsensor-Cheat wurde ausgelöst")
             * ein serverautoritativer Spielzug. Der Server baut zuerst die Domain-
             * Events und spielt sie in die Lobby ein. Erst danach antwortet er dem
             * Client mit Erfolg.
             */
            val events = buildClaimCheatReinforcementBonusEvents(request, payload)
            lobbyManager.submitAll(payload.lobbyCode, events, request.context)
            /*
             * Der Cheat ist fachlich sofort aktiv. Die Platzierung öffnet das
             * normale sichtbare Meldefenster, eine frühe Meldung darf aber nicht
             * als falsch bestraft werden.
             */
            markCheatReportWindowPending(payload.lobbyCode, payload.playerId)
            network.send(
                request.connectionId,
                ClaimCheatReinforcementBonusResponse(payload.lobbyCode),
            )
            logRequestCompleted(
                request = request,
                responseType = "ClaimCheatReinforcementBonusResponse",
                extraFields = arrayOf("eventCount" to events.size),
            )
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = claimCheatReinforcementBonusErrorResponse(request, payload, cause)
            network.send(request.connectionId, error)
            logRequestRejected(
                request = request,
                errorCode = error.code.name,
                reason = error.reason,
                cause = cause,
            )
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

    private suspend fun routeReportCheatRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as ReportCheatRequest

        runCatching {
            /*
             * Die Identitätsprüfung ist hier besonders wichtig: Ein Client darf
             * nicht im Namen eines anderen Spielers melden. Die Connection wurde
             * vorher einem PlayerId-Kontext zugeordnet, und genau dieser Kontext
             * muss zur reporterPlayerId im Request passen.
             */
            val lobby =
                lobbyManager.getLobby(payload.lobbyCode)
                    ?: throw IllegalStateException("GAME_NOT_FOUND")
            val state = lobby.currentState()
            val contextPlayerId = request.context.playerId

            require(!(contextPlayerId == null || contextPlayerId != payload.reporterPlayerId)) {
                "REQUESTER_MISMATCH"
            }
            require(state.gameStarted) { "GAME_NOT_RUNNING" }
            require(
                payload.reporterPlayerId in state.players &&
                    payload.accusedPlayerId in state.players,
            ) {
                "UNKNOWN_PLAYER"
            }
            require(payload.reporterPlayerId != payload.accusedPlayerId) { "SELF_REPORT" }

            /*
             * Ab hier ist die Meldung formal gültig. Ob sie inhaltlich stimmt,
             * entscheidet resolveCheatReport ausschließlich über das serverseitig
             * gespeicherte Meldefenster.
             */
            val result =
                resolveCheatReport(
                    lobbyCode = payload.lobbyCode,
                    reporterPlayerId = payload.reporterPlayerId,
                    accusedPlayerId = payload.accusedPlayerId,
                )

            network.send(
                request.connectionId,
                ReportCheatResponse(
                    lobbyCode = payload.lobbyCode,
                    accusedPlayerId = payload.accusedPlayerId,
                    correct = result.correct,
                    modifierDelta = result.modifierDelta,
                ),
            )
            logRequestCompleted(
                request = request,
                responseType = "ReportCheatResponse",
                extraFields =
                    arrayOf(
                        "accusedPlayerId" to payload.accusedPlayerId.value,
                        "correct" to result.correct,
                        "modifierDelta" to result.modifierDelta,
                    ),
            )
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = reportCheatErrorResponse(request, payload, cause)
            network.send(request.connectionId, error)
            logRequestRejected(
                request = request,
                errorCode = error.code.name,
                reason = error.reason,
                cause = cause,
            )
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
            /*
             * Erst nach einer erfolgreichen Platzierung können andere Spieler den
             * Cheat auf der Karte erkennen. Ab diesem Zeitpunkt läuft die Meldefrist.
             */
            openPendingCheatReportWindowAfterPlacement(payload.lobbyCode, payload.playerId)
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
            val resolvedAttack = events.filterIsInstance<AttackResolvedEvent>().firstOrNull()
            network.send(
                request.connectionId,
                AttackResponse(
                    lobbyCode = payload.lobbyCode,
                    requestId = payload.requestId,
                ),
            )
            logAttackResolved(resolvedAttack, attackResult, updatedState.stateVersion)
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
            sendPrivateStateUpdateBestEffort(
                lobbyCode = payload.lobbyCode,
                payload = PlayerHandUpdatedEvent.fromGameState(updatedState, payload.playerId),
                context = "trade-in private hand update",
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
        grantForGameStart: Boolean = false,
    ) {
        val currentState = lobbyManager.getLobby(lobbyCode)?.currentState() ?: return
        val currentTurnState = currentState.resolvedTurnState ?: return
        if (currentTurnState.turnPhase != TurnPhase.REINFORCEMENTS) {
            return
        }
        /*
         * Eine wartende Lobby zeigt für den konfigurierten Startspieler bereits
         * die Phase REINFORCEMENTS, besitzt aber bis zum tatsächlichen
         * Spielstart noch keinen Verstärkungspool. Nur spätere doppelte
         * Verarbeitung derselben laufenden Phase muss übersprungen werden.
         */
        if (!grantForGameStart &&
            previousTurnState?.turnPhase == TurnPhase.REINFORCEMENTS &&
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

        val adjustment =
            synchronized(cheatReportLock) {
                val activePlayerId = currentTurnState.activePlayerId
                /*
                 * Die gespeicherten Folgen von Cheat-Meldungen werden genau beim
                 * Start der nächsten Reinforcements-Phase verbraucht. Danach
                 * werden sie sofort aus den Maps entfernt, damit ein Bonus oder
                 * Malus nie versehentlich in einer späteren Runde erneut wirkt.
                 */
                val modifiers = nextReinforcementModifierByLobby[lobbyCode]
                val modifier = modifiers?.remove(activePlayerId) ?: 0
                if (modifiers?.isEmpty() == true) {
                    nextReinforcementModifierByLobby.remove(lobbyCode)
                }

                val penalizedPlayers = nextZeroReinforcementPenaltyByLobby[lobbyCode]
                val zeroReinforcements = penalizedPlayers?.remove(activePlayerId) == true
                if (penalizedPlayers?.isEmpty() == true) {
                    nextZeroReinforcementPenaltyByLobby.remove(lobbyCode)
                }

                CheatReinforcementAdjustment(
                    modifier = modifier,
                    zeroReinforcements = zeroReinforcements,
                )
            }

        if (adjustment.zeroReinforcements) {
            /*
             * Die Cheater-Strafe ist stärker als ein möglicher Bonus oder Malus:
             * In dieser Verstärkungsphase soll der Spieler wirklich bei 0 landen.
             * Darum wird zuerst der normale Basispool gesetzt und danach komplett
             * wieder abgezogen. So bleibt der Ablauf für alle Clients derselbe:
             * Sie sehen normale PendingReinforcement-Events und brauchen keine
             * Sonderlogik für "Cheater bekommt 0".
             */
            if (breakdown.total > 0) {
                lobbyManager.submit(
                    PendingReinforcementsChangedEvent(
                        lobbyCode = lobbyCode,
                        playerId = currentTurnState.activePlayerId,
                        delta = -breakdown.total,
                    ),
                    context,
                )
            }
            return
        }

        /*
         * Der Malus darf den Verstärkungspool nicht negativ machen.
         * Falls ein Spieler weniger als 3 Verstärkungen bekommt, wird der Malus begrenzt.
         * Beispiel: Hat jemand nur 2 Basisverstärkungen und bekommt -3, werden
         * effektiv nur -2 angewendet. Der Pending-Pool endet also bei 0 und nicht
         * bei einem fachlich sinnlosen negativen Wert.
         */
        val appliedModifier = adjustment.modifier.coerceAtLeast(-breakdown.total)
        if (appliedModifier != 0) {
            lobbyManager.submit(
                PendingReinforcementsChangedEvent(
                    lobbyCode = lobbyCode,
                    playerId = currentTurnState.activePlayerId,
                    delta = appliedModifier,
                ),
                context,
            )
        }
    }

    /**
     * Entscheidet, ob nach dem Routing noch Basisverstärkungen vergeben werden müssen.
     *
     * Start, Verlassen und Kick können eine laufende Partie direkt in eine neue
     * Verstärkungsphase bringen. Ohne diese Prüfung würde der nächste aktive Spieler
     * zwar die Phase sehen, aber keine offenen Truppen zum Platzieren erhalten.
     *
     * @param lobbyCode Code der Lobby, deren aktueller Zustand nach dem Routing geprüft wird.
     * @param payload Verarbeitete Anfrage, die den möglichen Phasenwechsel ausgelöst hat.
     * @return `true`, wenn der nachgelagerte Verstärkungsgrant ausgeführt werden soll.
     */
    private fun shouldGrantBaseReinforcementsAfterRouting(
        lobbyCode: LobbyCode,
        payload: NetworkMessagePayload,
    ): Boolean =
        when (payload) {
            is StartGameRequest -> true
            is LeaveLobbyRequest,
            is KickPlayerRequest,
            -> {
                val currentState = lobbyManager.getLobby(lobbyCode)?.currentState() ?: return false
                currentState.status == GameStatus.RUNNING && currentState.hasMap()
            }
            else -> false
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
            logRequestCompleted(
                request = request,
                responseType = TurnStateGetResponse::class.simpleName ?: "TurnStateGetResponse",
                extraFields =
                    arrayOf(
                        "turnCount" to response.turnCount,
                        "phase" to response.turnPhase.name,
                    ),
            )
            hooks.onRouted(request.connectionId)
        }.onFailure { cause ->
            val error = turnStateGetErrorResponse(payload, cause)
            network.send(request.connectionId, error)
            logRequestRejected(
                request = request,
                errorCode = error.code.name,
                reason = error.reason,
                cause = cause,
            )
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

    private suspend fun routeCharacterSelectRequest(request: DecodedNetworkRequest) {
        val payload = request.payload as CharacterSelectRequest
        val requesterPlayerId =
            request.context.playerId?.value
                ?: run {
                    val reason = "Connection ist keinem Spieler zugeordnet."
                    logRequestRejected(
                        request = request,
                        errorCode = PLAYER_CONTEXT_MISSING_ERROR_CODE,
                        reason = reason,
                        cause = IllegalArgumentException(reason),
                    )
                    return
                }

        val charMap = charactersByLobby.getOrPut(payload.lobbyCode) { ConcurrentHashMap() }
        val success =
            synchronized(charMap) {
                charMap.entries.removeIf { it.value == requesterPlayerId }
                if (charMap.containsKey(payload.characterId)) {
                    false
                } else {
                    charMap[payload.characterId] = requesterPlayerId
                    true
                }
            }

        if (!success) {
            val reason = "Achtung, dieser Charakter ist schon vergeben"
            network.send(
                request.connectionId,
                CharacterSelectErrorResponse(reason),
            )
            logRequestRejected(
                request = request,
                errorCode = CHARACTER_ALREADY_ASSIGNED_ERROR_CODE,
                reason = reason,
                cause = IllegalArgumentException(reason),
            )
            return
        }

        network.send(
            request.connectionId,
            CharacterSelectResponse(
                lobbyCode = payload.lobbyCode,
                characterId = payload.characterId,
            ),
        )

        val broadcast =
            CharacterSelectedBroadcast(
                lobbyCode = payload.lobbyCode,
                playerId = payload.playerId,
                characterId = payload.characterId,
            )
        lobbyManager.getLobby(payload.lobbyCode)
            ?.currentState()
            ?.players
            .orEmpty()
            .mapNotNull(connectionIdResolver)
            .distinct()
            .forEach { connectionId -> network.send(connectionId, broadcast) }
        logRequestCompleted(
            request = request,
            responseType = CharacterSelectResponse::class.simpleName ?: "CharacterSelectResponse",
            extraFields = arrayOf("characterId" to payload.characterId),
        )
    }

    private suspend fun routeDecodedRequest(request: DecodedNetworkRequest) {
        val lobbyCode = lobbyCodeOf(request.payload)
        val previousTurnState = lobbyCode?.let(::currentTurnState)

        when (val result = router.route(request)) {
            is LobbyRoutingResult.Success -> {
                dispatchNetworkMessages(request)
                responseTypeFor(request.payload)?.let { responseType ->
                    logRequestCompleted(
                        request = request,
                        responseType = responseType,
                        lobbyCode = result.context.lobbyCode ?: lobbyCode,
                        extraFields = arrayOf("eventCount" to result.eventCount),
                    )
                }
                if (lobbyCode != null) {
                    if (shouldGrantBaseReinforcementsAfterRouting(lobbyCode, request.payload)) {
                        grantBaseReinforcementsOnPhaseStart(
                            lobbyCode = lobbyCode,
                            previousTurnState = previousTurnState,
                            context = request.context,
                            grantForGameStart = request.payload is StartGameRequest,
                        )
                    }
                    broadcastTurnStateIfChanged(
                        lobbyCode = lobbyCode,
                        previousTurnState = previousTurnState,
                        force = request.payload is StartGameRequest,
                    )
                    broadcastFullSnapshotAfterMembershipChangeIfNeeded(
                        lobbyCode = lobbyCode,
                        payload = request.payload,
                    )
                }
                hooks.onRouted(request.connectionId)
            }

            is LobbyRoutingResult.Failure -> {
                dispatchErrorResponse(request, result.error.reason)
                logRequestRejected(
                    request = request,
                    errorCode = result.error::class.simpleName ?: ROUTING_ERROR_CODE,
                    reason = result.error.reason,
                    cause = result.error.cause ?: IllegalArgumentException(result.error.reason),
                    lobbyCode = result.error.context.lobbyCode ?: lobbyCode,
                )
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

    private suspend fun handleCreateLobbyRequest(request: DecodedNetworkRequest): LobbyCode {
        val lobbyCode = createLobbyWithUniqueCode()
        lobbyManager.submit(LobbyCreated(lobbyCode), request.context)
        network.send(request.connectionId, CreateLobbyResponse(lobbyCode = lobbyCode))
        return lobbyCode
    }

    private suspend fun dispatchReconnectLobbySnapshot(
        connectionId: ConnectionId,
        payload: ReconnectRequest,
    ) {
        if (resolveSessionToken(connectionId) != payload.sessionToken) {
            logger.warn(
                "Reconnect snapshot skipped connectionId={} reason=session-token-mismatch",
                connectionId.value,
            )
            return
        }

        val reconnectContext =
            sessionContextRegistry?.contextFor(payload.sessionToken)
                ?: run {
                    logger.warn(
                        "Reconnect snapshot skipped connectionId={} reason=context-missing",
                        connectionId.value,
                    )
                    return
                }
        val lobbyCode =
            reconnectContext.lobbyCode
                ?: run {
                    logger.warn(
                        "Reconnect snapshot skipped connectionId={} " +
                            "reason=lobby-missing-in-context",
                        connectionId.value,
                    )
                    return
                }
        val reconnectingPlayerId =
            reconnectContext.playerId
                ?: run {
                    logger.warn(
                        RECONNECT_SNAPSHOT_SKIPPED_WITH_LOBBY_PREFIX +
                            "reason=player-missing-in-context",
                        connectionId.value,
                        lobbyCode.value,
                    )
                    return
                }
        val lobbyState =
            lobbyManager.getLobby(lobbyCode)?.currentState()
                ?: run {
                    logger.warn(
                        RECONNECT_SNAPSHOT_SKIPPED_WITH_LOBBY_PREFIX +
                            "playerId={} reason=lobby-not-found",
                        connectionId.value,
                        lobbyCode.value,
                        reconnectingPlayerId.value,
                    )
                    return
                }

        if (!lobbyState.players.contains(reconnectingPlayerId)) {
            logger.warn(
                RECONNECT_SNAPSHOT_SKIPPED_WITH_LOBBY_PREFIX +
                    "playerId={} reason=player-not-in-lobby",
                connectionId.value,
                lobbyCode.value,
                reconnectingPlayerId.value,
            )
            return
        }

        logger.info(
            "Reconnect lobby snapshot dispatch connectionId={} lobbyCode={} " +
                "playerId={} memberCount={}",
            connectionId.value,
            lobbyCode.value,
            reconnectingPlayerId.value,
            lobbyState.players.size,
        )

        /*
         * Ein echter Reconnect durchläuft keinen JoinRequest mehr. Deshalb muss
         * der reconnectende Client seine Lobby-Spielerliste erneut erhalten,
         * sonst kann die Android-App Owner-IDs aus dem GameState nicht auf Namen
         * und Farben abbilden. Es wird nur an die neue Verbindung gesendet; die
         * übrigen Clients kennen diese Spieler bereits.
         */
        lobbyState.players.forEach { playerId ->
            val playerDisplayName =
                lobbyState.playerDisplayNames[playerId]
                    ?.let(::normalizePlayerDisplayNameOrFallback)
                    ?: return@forEach
            sendBestEffortPayload(
                connectionId = connectionId,
                payload =
                    PlayerJoinedLobbyEvent(
                        lobbyCode = lobbyCode,
                        playerId = playerId,
                        playerDisplayName = playerDisplayName,
                        isHost = lobbyState.lobbyOwner == playerId,
                    ),
                context = "reconnect lobby roster replay",
            )
        }
        replayCharacterSelections(
            connectionId = connectionId,
            lobbyCode = lobbyCode,
            context = "reconnect character roster replay",
        )

        onPlayerConnected(reconnectingPlayerId)
        replayConnectionStatuses(
            connectionId = connectionId,
            lobbyCode = lobbyCode,
            excludedPlayerId = reconnectingPlayerId,
        )
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
        val playerId = request.context.playerId
        network.send(
            request.connectionId,
            JoinLobbyResponse(
                lobbyCode = payload.lobbyCode,
                playerId = playerId,
            ),
        )

        playerId ?: return
        val lobbyState = lobbyManager.getLobby(payload.lobbyCode)?.currentState() ?: return
        val members = lobbyState.players
        val ownDisplayName =
            lobbyState.playerDisplayNames[playerId]
                ?.let(::normalizePlayerDisplayNameOrFallback)
                ?: normalizePlayerDisplayName(payload.playerDisplayName)
        resolveSessionToken(request.connectionId)?.let { sessionToken ->
            sessionContextRegistry?.updateLobbyContext(
                sessionToken = sessionToken,
                lobbyCode = payload.lobbyCode,
                playerDisplayName = ownDisplayName,
            )
        }

        members
            .filter { existingPlayerId -> existingPlayerId != playerId }
            .forEach { existingPlayerId ->
                val existingName =
                    lobbyState.playerDisplayNames[existingPlayerId]
                        ?.let(::normalizePlayerDisplayNameOrFallback)
                        ?: return@forEach
                sendBestEffortPayload(
                    connectionId = request.connectionId,
                    payload =
                        PlayerJoinedLobbyEvent(
                            lobbyCode = payload.lobbyCode,
                            playerId = existingPlayerId,
                            playerDisplayName = existingName,
                            isHost = lobbyState.lobbyOwner == existingPlayerId,
                        ),
                    context = "join existing roster replay",
                )
            }
        replayCharacterSelections(
            connectionId = request.connectionId,
            lobbyCode = payload.lobbyCode,
            context = "join character roster replay",
        )

        val event =
            PlayerJoinedLobbyEvent(
                lobbyCode = payload.lobbyCode,
                playerId = playerId,
                playerDisplayName = ownDisplayName,
                isHost = lobbyState.lobbyOwner == playerId,
            )

        members
            .mapNotNull(connectionIdResolver)
            .distinct()
            .forEach { connectionId ->
                sendBestEffortPayload(
                    connectionId = connectionId,
                    payload = event,
                    context = "join lobby broadcast",
                )
            }
        replayConnectionStatuses(
            connectionId = request.connectionId,
            lobbyCode = payload.lobbyCode,
            excludedPlayerId = playerId,
        )

        // broadcast updated player count to lobby members
        val count = lobbyState.players.size
        members
            .mapNotNull(connectionIdResolver)
            .distinct()
            .forEach { connectionId ->
                network.send(connectionId, PlayerCountUpdateEvent(payload.lobbyCode, count))
            }
    }

    private suspend fun replayCharacterSelections(
        connectionId: ConnectionId,
        lobbyCode: LobbyCode,
        context: String,
    ) {
        val broadcasts =
            charactersByLobby[lobbyCode]
                ?.let { charMap ->
                    synchronized(charMap) {
                        charMap.map { (characterId, playerId) ->
                            CharacterSelectedBroadcast(
                                lobbyCode = lobbyCode,
                                playerId = PlayerId(playerId),
                                characterId = characterId,
                            )
                        }
                    }
                }
                .orEmpty()

        broadcasts.forEach { broadcast ->
            sendBestEffortPayload(
                connectionId = connectionId,
                payload = broadcast,
                context = context,
            )
        }
    }

    private fun releaseCharacterSelection(
        lobbyCode: LobbyCode,
        playerId: PlayerId,
    ) {
        charactersByLobby[lobbyCode]?.let { charMap ->
            synchronized(charMap) {
                charMap.entries.removeIf { it.value == playerId.value }
            }
        }
    }

    private suspend fun dispatchLeaveNetworkMessages(
        request: DecodedNetworkRequest,
        payload: LeaveLobbyRequest,
    ) {
        network.send(request.connectionId, LeaveLobbyResponse(payload.lobbyCode))

        val playerId = request.context.playerId ?: return
        releaseCharacterSelection(payload.lobbyCode, playerId)
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
                sendBestEffortPayload(
                    connectionId = connectionId,
                    payload = event,
                    context = "leave lobby broadcast",
                )
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
        releaseCharacterSelection(payload.lobbyCode, payload.targetPlayerId)
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
                sendBestEffortPayload(
                    connectionId = connectionId,
                    payload = event,
                    context = "kick lobby broadcast",
                )
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
            when {
                cause is PublicStatePayloadTooLargeException -> MapGetErrorCode.PAYLOAD_TOO_LARGE
                cause.message == "GAME_NOT_FOUND" -> MapGetErrorCode.GAME_NOT_FOUND
                cause.message == "NOT_IN_GAME" -> MapGetErrorCode.NOT_IN_GAME
                cause.message == "MAP_NOT_READY" -> MapGetErrorCode.MAP_NOT_READY
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
                MapGetErrorCode.PAYLOAD_TOO_LARGE -> {
                    val payloadTooLarge = cause as? PublicStatePayloadTooLargeException
                    if (payloadTooLarge == null) {
                        "Map-Snapshot fuer Lobby '${payload.lobbyCode.value}' " +
                            PAYLOAD_LIMIT_EXCEEDED_SUFFIX
                    } else {
                        "Map-Snapshot fuer Lobby '${payload.lobbyCode.value}' " +
                            "ist mit ${payloadTooLarge.encodedSizeBytes} Bytes groesser " +
                            PAYLOAD_LIMIT_DETAILS_PREFIX +
                            "${payloadTooLarge.maxAllowedBytes} Bytes."
                    }
                }
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
            when {
                cause is PublicStatePayloadTooLargeException ->
                    GameStateCatchUpErrorCode.PAYLOAD_TOO_LARGE
                cause.message == "GAME_NOT_FOUND" -> GameStateCatchUpErrorCode.GAME_NOT_FOUND
                cause.message == "NOT_IN_GAME" -> GameStateCatchUpErrorCode.NOT_IN_GAME
                cause.message == "SNAPSHOT_NOT_READY" ->
                    GameStateCatchUpErrorCode.SNAPSHOT_NOT_READY
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
                GameStateCatchUpErrorCode.PAYLOAD_TOO_LARGE -> {
                    val payloadTooLarge = cause as? PublicStatePayloadTooLargeException
                    if (payloadTooLarge == null) {
                        "Catch-up-Snapshot fuer Lobby '${payload.lobbyCode.value}' " +
                            PAYLOAD_LIMIT_EXCEEDED_SUFFIX
                    } else {
                        "Catch-up-Snapshot fuer Lobby '${payload.lobbyCode.value}' " +
                            "ist mit ${payloadTooLarge.encodedSizeBytes} Bytes groesser " +
                            PAYLOAD_LIMIT_DETAILS_PREFIX +
                            "${payloadTooLarge.maxAllowedBytes} Bytes."
                    }
                }
            }

        return GameStateCatchUpErrorResponse(code = code, reason = reason)
    }

    private fun gameStatePrivateGetErrorResponse(
        request: DecodedNetworkRequest,
        payload: GameStatePrivateGetRequest,
        cause: Throwable,
    ): GameStatePrivateGetErrorResponse {
        val code =
            when {
                cause is PrivateStatePayloadTooLargeException ->
                    GameStatePrivateGetErrorCode.PAYLOAD_TOO_LARGE
                cause.message == "GAME_NOT_FOUND" -> GameStatePrivateGetErrorCode.GAME_NOT_FOUND
                cause.message == "NOT_IN_GAME" -> GameStatePrivateGetErrorCode.NOT_IN_GAME
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
                GameStatePrivateGetErrorCode.PAYLOAD_TOO_LARGE -> {
                    val payloadTooLarge = cause as? PrivateStatePayloadTooLargeException
                    if (payloadTooLarge == null) {
                        "Privater Snapshot fuer Lobby '${payload.lobbyCode.value}' " +
                            PAYLOAD_LIMIT_EXCEEDED_SUFFIX
                    } else {
                        "Privater Snapshot fuer Lobby '${payload.lobbyCode.value}' " +
                            "ist mit ${payloadTooLarge.encodedSizeBytes} Bytes groesser " +
                            PAYLOAD_LIMIT_DETAILS_PREFIX +
                            "${payloadTooLarge.maxAllowedBytes} Bytes."
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
        val currentTurnState = requireValidTurnAdvanceState(request, payload, state)

        return advanceTurnStateEvent(
            lobbyCode = payload.lobbyCode,
            turnState = currentTurnState,
            turnOrder = state.turnOrder,
        )
    }

    private fun buildTurnAdvancePlan(
        request: DecodedNetworkRequest,
        payload: TurnAdvanceRequest,
    ): TurnAdvancePlan {
        val lobby =
            lobbyManager.getLobby(payload.lobbyCode)
                ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val currentTurnState = requireValidTurnAdvanceState(request, payload, state)
        val firstTurnStateUpdate =
            advanceTurnStateEvent(
                lobbyCode = payload.lobbyCode,
                turnState = currentTurnState,
                turnOrder = state.turnOrder,
            )

        if (currentTurnState.turnPhase != TurnPhase.FORTIFY) {
            val drawPhaseEvent =
                buildDrawPhaseEventIfNeeded(
                    lobbyCode = payload.lobbyCode,
                    playerId = payload.playerId,
                    state = state,
                    currentTurnState = currentTurnState,
                )

            return when (drawPhaseEvent) {
                is MatchEndedEvent -> TurnAdvancePlan(events = listOf(drawPhaseEvent))
                is CardDrawnEvent ->
                    TurnAdvancePlan(
                        events = listOf(drawPhaseEvent, firstTurnStateUpdate),
                        drawnCardEvent = drawPhaseEvent,
                    )
                else -> TurnAdvancePlan(events = listOf(firstTurnStateUpdate))
            }
        }

        val drawCardTurnState = firstTurnStateUpdate.toTurnState()
        if (drawCardTurnState.turnPhase != TurnPhase.DRAW_CARD || drawCardTurnState.isPaused) {
            return TurnAdvancePlan(events = listOf(firstTurnStateUpdate))
        }

        val drawPhaseEvent =
            buildDrawPhaseEventIfNeeded(
                lobbyCode = payload.lobbyCode,
                playerId = payload.playerId,
                state = state,
                currentTurnState = drawCardTurnState,
            )
        if (drawPhaseEvent is MatchEndedEvent) {
            return TurnAdvancePlan(events = listOf(firstTurnStateUpdate, drawPhaseEvent))
        }

        val nextTurnStateUpdate =
            advanceTurnStateEvent(
                lobbyCode = payload.lobbyCode,
                turnState = drawCardTurnState,
                turnOrder = state.turnOrder,
            )
        return TurnAdvancePlan(
            events = listOfNotNull(firstTurnStateUpdate, drawPhaseEvent, nextTurnStateUpdate),
            drawnCardEvent = drawPhaseEvent as? CardDrawnEvent,
        )
    }

    private fun requireValidTurnAdvanceState(
        request: DecodedNetworkRequest,
        payload: TurnAdvanceRequest,
        state: GameState,
    ): TurnState {
        val contextPlayerId = request.context.playerId
        val currentTurnState =
            state.resolvedTurnState
                ?: throw IllegalArgumentException("NOT_ACTIVE_PLAYER")

        check(state.status != GameStatus.FINISHED) { "GAME_FINISHED" }
        require(!(contextPlayerId == null || contextPlayerId != payload.playerId)) {
            "NOT_ACTIVE_PLAYER"
        }
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
        if (currentTurnState.turnPhase == TurnPhase.REINFORCEMENTS) {
            val hand = state.handOf(payload.playerId)
            require(!requiresForcedTradeInOnReinforcementPhase(state, payload.playerId, hand)) {
                "FORCED_TRADE_REQUIRED"
            }
        }

        return currentTurnState
    }

    private fun advanceTurnStateEvent(
        lobbyCode: LobbyCode,
        turnState: TurnState,
        turnOrder: List<PlayerId>,
    ): TurnStateUpdatedEvent {
        val updatedTurnState =
            TurnStateMachine.advance(
                turnState = turnState,
                turnOrder = turnOrder,
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

        return pausedOrAdvancedTurnState.toUpdatedEvent(lobbyCode)
    }

    private fun buildDrawPhaseEventIfNeeded(
        lobbyCode: LobbyCode,
        playerId: PlayerId,
    ): LobbyEvent? {
        val state =
            lobbyManager.getLobby(lobbyCode)?.currentState()
                ?: throw IllegalStateException("GAME_NOT_FOUND")
        val currentTurnState = state.resolvedTurnState ?: return null
        return buildDrawPhaseEventIfNeeded(lobbyCode, playerId, state, currentTurnState)
    }

    private fun buildDrawPhaseEventIfNeeded(
        lobbyCode: LobbyCode,
        playerId: PlayerId,
        state: GameState,
        currentTurnState: TurnState,
    ): LobbyEvent? {
        if (
            currentTurnState.activePlayerId != playerId ||
            currentTurnState.turnPhase != TurnPhase.DRAW_CARD ||
            !state.territoryCapturedThisTurn
        ) {
            return null
        }

        val drawnCard =
            state.deckState.topCard()
                ?: return MatchEndedEvent(
                    lobbyCode = lobbyCode,
                    reason = MatchEndReason.DECK_EMPTY,
                )
        return CardDrawnEvent(
            lobbyCode = lobbyCode,
            playerId = playerId,
            cardId = drawnCard.cardId,
        )
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
    ): List<LobbyEvent> {
        val lobby =
            lobbyManager.getLobby(payload.lobbyCode)
                ?: throw IllegalStateException("GAME_NOT_FOUND")
        val state = lobby.currentState()
        val contextPlayerId = request.context.playerId
        val currentTurnState =
            state.resolvedTurnState
                ?: throw IllegalArgumentException("NOT_ACTIVE_PLAYER")

        /*
         * Auch wenn der Client den Cheatbutton nur in der passenden Situation
         * anzeigen soll, vertraut der Server nie auf die UI. Hier werden deshalb
         * alle fachlichen Voraussetzungen noch einmal geprüft:
         * - Die Connection muss wirklich zum Spieler gehören.
         * - Der Spieler muss aktiv am Match teilnehmen.
         * - Er muss am Zug und in der Reinforcements-Phase sein.
         * - Ein Pflicht-Kartentausch darf den Verstärkungszug nicht blockieren.
         * - Der einmalige Bonus darf noch nicht verbraucht sein.
         */
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

    private fun buildClaimCheatReinforcementBonusEvents(
        request: DecodedNetworkRequest,
        payload: ClaimCheatReinforcementBonusRequest,
    ): List<LobbyEvent> {
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
        require(payload.playerId !in state.usedCheatReinforcementBonusByPlayer) {
            "ALREADY_USED"
        }

        /*
         * Der Bonus besteht bewusst aus zwei getrennten Events:
         * 1. CheatReinforcementBonusUsedEvent merkt dauerhaft, dass der Spieler
         *    seinen einmaligen Bonus verbraucht hat.
         * 2. PendingReinforcementsChangedEvent erhöht den aktuellen Pool um 3.
         *
         * Dadurch bleibt das Eventlog verständlich und replaybar. Beim
         * Nachspielen kann man genau sehen, warum der Spieler später nicht noch
         * einmal cheaten darf und wo die zusätzlichen Truppen herkommen.
         */
        return listOf(
            CheatReinforcementBonusUsedEvent(
                lobbyCode = payload.lobbyCode,
                playerId = payload.playerId,
            ),
            PendingReinforcementsChangedEvent(
                lobbyCode = payload.lobbyCode,
                playerId = payload.playerId,
                delta = 3,
            ),
        )
    }

    private fun markCheatReportWindowPending(
        lobbyCode: LobbyCode,
        accusedPlayerId: PlayerId,
    ) {
        /*
         * Der Spieler hat den Cheat ausgelöst, aber eventuell noch nichts
         * Sichtbares gemacht. Frühe Meldungen bleiben dadurch korrekt; die
         * sichtbare Platzierung setzt später die Ablaufzeit des Meldefensters.
         */
        synchronized(cheatReportLock) {
            pendingVisibleCheatByLobby
                .getOrPut(lobbyCode) { mutableSetOf() }
                .add(accusedPlayerId)
        }
    }

    private fun openPendingCheatReportWindowAfterPlacement(
        lobbyCode: LobbyCode,
        accusedPlayerId: PlayerId,
    ) {
        synchronized(cheatReportLock) {
            val pendingPlayers = pendingVisibleCheatByLobby[lobbyCode]
            if (pendingPlayers?.remove(accusedPlayerId) != true) {
                return@synchronized
            }
            if (pendingPlayers.isEmpty()) {
                pendingVisibleCheatByLobby.remove(lobbyCode)
            }
            /*
             * Ab der ersten Platzierung nach dem Cheat beginnt die Meldefrist,
             * damit das Fenster immer 20 Sekunden nach der sichtbaren
             * Platzierung endet.
             */
            if (cheatReportWindowsByLobby[lobbyCode]?.containsKey(accusedPlayerId) != true) {
                openCheatReportWindowLocked(lobbyCode, accusedPlayerId)
            }
        }
    }

    private fun openCheatReportWindowLocked(
        lobbyCode: LobbyCode,
        accusedPlayerId: PlayerId,
    ): CheatReportWindow {
        /*
         * Statt einen Timer laufen zu lassen, speichere ich nur den Zeitpunkt,
         * bis wann eine Meldung noch gültig ist. Das ist einfacher und robuster.
         */
        val window =
            CheatReportWindow(
                expiresAtMillis = nowEpochMillis() + CHEAT_REPORT_WINDOW_MILLIS,
            )
        cheatReportWindowsByLobby
            .getOrPut(lobbyCode) { mutableMapOf() }[accusedPlayerId] = window
        return window
    }

    private fun resolveCheatReport(
        lobbyCode: LobbyCode,
        reporterPlayerId: PlayerId,
        accusedPlayerId: PlayerId,
    ): CheatReportResult =
        synchronized(cheatReportLock) {
            /*
             * Hier entscheidet ausschließlich der Server, ob eine Meldung korrekt ist.
             * Eine Meldung ist korrekt, wenn für den beschuldigten Spieler noch
             * ein gültiges Meldefenster offen ist oder ein Cheat bereits
             * serverseitig vorgemerkt wurde. Vorgemerkte Cheats haben noch
             * keine Ablaufzeit; diese entsteht erst beim Platzieren.
             */
            val window = cheatReportWindowsByLobby[lobbyCode]?.get(accusedPlayerId)
            val now = nowEpochMillis()
            val pendingCheat =
                pendingVisibleCheatByLobby[lobbyCode]?.contains(accusedPlayerId) == true
            val activeWindow = window?.takeIf { now <= it.expiresAtMillis }
            val correct = activeWindow != null || pendingCheat

            if (window != null && !correct) {
                cheatReportWindowsByLobby[lobbyCode]?.remove(accusedPlayerId)
            }

            if (correct) {
                val key =
                    CheatReportKey(
                        reporterPlayerId = reporterPlayerId,
                        accusedPlayerId = accusedPlayerId,
                    )
                require(cheatReportsByLobby.getOrPut(lobbyCode) { mutableSetOf() }.add(key)) {
                    "ALREADY_REPORTED"
                }
                /*
                 * Der Schummel-Verstärkungsbonus ist pro Spieler einmalig.
                 * Reporter dürfen dieselbe Cheat-Aktion deshalb nur einmal melden,
                 * egal ob vor oder nach der sichtbaren Platzierung.
                 */
            }

            /*
             * Korrekte Meldung: +3 für den Melder und 0 Truppen für den Cheater
             * in dessen nächster Verstärkungsphase.
             * Falsche Meldung: -3 für den meldenden Spieler.
             *
             * Wichtig: Auch falsche Meldungen sind kein Routingfehler. Sie sind
             * spielerisch erlaubt, haben aber als Risiko den Malus. Nur formale
             * Fehler wie Selbstmeldung oder falsche Connection landen in
             * ReportCheatErrorResponse.
             */
            val modifierDelta =
                if (correct) {
                    CHEAT_REPORT_REWARD
                } else {
                    CHEAT_REPORT_PENALTY
                }
            val modifiers = nextReinforcementModifierByLobby.getOrPut(lobbyCode) { mutableMapOf() }
            modifiers[reporterPlayerId] = (modifiers[reporterPlayerId] ?: 0) + modifierDelta
            if (correct) {
                nextZeroReinforcementPenaltyByLobby
                    .getOrPut(lobbyCode) { mutableSetOf() }
                    .add(accusedPlayerId)
            }

            CheatReportResult(
                correct = correct,
                modifierDelta = modifierDelta,
            )
        }

    private fun buildAttackEvents(
        request: DecodedNetworkRequest,
        payload: AttackRequest,
    ): List<LobbyEvent> {
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
        require(payload.attackTroops >= MIN_ATTACK_COMMITTED_TROOPS) { "INVALID_REQUEST" }
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
        require(state.troopCountOf(payload.toTerritoryId) > 0) { "INVALID_REQUEST" }

        val sourceTroops = state.troopCountOf(payload.fromTerritoryId)
        require(payload.attackTroops <= sourceTroops - 1) { "INSUFFICIENT_TROOPS" }

        val attackEvents =
            try {
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
        val winnerPlayerId = territoryDominationWinnerAfterEvents(state, attackEvents)

        return if (winnerPlayerId != null) {
            attackEvents +
                MatchEndedEvent(
                    lobbyCode = payload.lobbyCode,
                    reason = MatchEndReason.TERRITORY_DOMINATION,
                    winnerPlayerId = winnerPlayerId,
                )
        } else {
            attackEvents
        }
    }

    /**
     * Projiziert die Owner-Änderungen einer Attack-Eventliste und prüft die Siegbedingung.
     *
     * @param state autoritativer Zustand vor dem Angriff
     * @param events durch die Angriffsregeln erzeugte Domain-Events
     * @return Gewinner bei vollständiger Gebietskontrolle oder `null`
     */
    private fun territoryDominationWinnerAfterEvents(
        state: GameState,
        events: List<LobbyEvent>,
    ): PlayerId? {
        if (events.none { event -> event is AttackResolvedEvent && event.capture }) {
            return null
        }

        val projectedOwners =
            state
                .allTerritoryStates()
                .associate { territoryState ->
                    territoryState.territoryId to territoryState.ownerId
                }
                .toMutableMap()
        events.filterIsInstance<AttackResolvedEvent>()
            .filter(AttackResolvedEvent::capture)
            .forEach { event ->
                projectedOwners[event.toTerritoryId] = event.attackerPlayerId
            }

        if (
            projectedOwners.isEmpty() ||
            projectedOwners.values.any { ownerId -> ownerId == null }
        ) {
            return null
        }

        return projectedOwners.values.filterNotNull().toSet().singleOrNull()
    }

    private fun buildTradeInCardsEvents(
        request: DecodedNetworkRequest,
        payload: TradeInCardsRequest,
    ): List<LobbyEvent> {
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
                    state.status == GameStatus.RUNNING
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
                "GAME_FINISHED" -> TurnAdvanceErrorCode.GAME_FINISHED
                "PHASE_MISMATCH" -> TurnAdvanceErrorCode.PHASE_MISMATCH
                "FORCED_TRADE_REQUIRED" -> TurnAdvanceErrorCode.FORCED_TRADE_REQUIRED
                else -> TurnAdvanceErrorCode.NOT_ACTIVE_PLAYER
            }

        val reason =
            when (code) {
                TurnAdvanceErrorCode.GAME_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
                TurnAdvanceErrorCode.GAME_PAUSED ->
                    "Lobby '${payload.lobbyCode.value}' ist pausiert; " +
                        "Turn-Wechsel ist aktuell nicht erlaubt."
                TurnAdvanceErrorCode.GAME_FINISHED ->
                    "Spiel für Lobby '${payload.lobbyCode.value}' ist bereits beendet."
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
                TurnAdvanceErrorCode.FORCED_TRADE_REQUIRED ->
                    REINFORCEMENTS_PHASE_END_BLOCKED_PREFIX +
                        "die Pflichtabgabe von Karten erfüllt ist."
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

    private fun claimCheatReinforcementBonusErrorResponse(
        request: DecodedNetworkRequest,
        payload: ClaimCheatReinforcementBonusRequest,
        cause: Throwable,
    ): ClaimCheatReinforcementBonusErrorResponse {
        val code =
            when (cause.message) {
                "GAME_NOT_FOUND" -> ClaimCheatReinforcementBonusErrorCode.GAME_NOT_FOUND
                "REQUESTER_MISMATCH" -> ClaimCheatReinforcementBonusErrorCode.REQUESTER_MISMATCH
                "GAME_PAUSED" -> ClaimCheatReinforcementBonusErrorCode.GAME_PAUSED
                "PHASE_MISMATCH" -> ClaimCheatReinforcementBonusErrorCode.PHASE_MISMATCH
                "ALREADY_USED" -> ClaimCheatReinforcementBonusErrorCode.ALREADY_USED
                "FORCED_TRADE_REQUIRED" ->
                    ClaimCheatReinforcementBonusErrorCode.FORCED_TRADE_REQUIRED
                else -> ClaimCheatReinforcementBonusErrorCode.NOT_ACTIVE_PLAYER
            }

        val reason =
            when (code) {
                ClaimCheatReinforcementBonusErrorCode.GAME_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
                ClaimCheatReinforcementBonusErrorCode.REQUESTER_MISMATCH -> {
                    val contextPlayerId = request.context.playerId
                    if (contextPlayerId == null) {
                        connectionNotAssignedToLobby(payload.lobbyCode)
                    } else {
                        "Requester '${payload.playerId.value}' passt nicht zur aktuellen " +
                            "Connection '${contextPlayerId.value}'."
                    }
                }
                ClaimCheatReinforcementBonusErrorCode.NOT_ACTIVE_PLAYER ->
                    "Nur der aktive Spieler darf den Schummel-Verstärkungsbonus beanspruchen."
                ClaimCheatReinforcementBonusErrorCode.GAME_PAUSED ->
                    "Lobby '${payload.lobbyCode.value}' ist pausiert; " +
                        "der Schummel-Verstärkungsbonus ist aktuell nicht erlaubt."
                ClaimCheatReinforcementBonusErrorCode.PHASE_MISMATCH ->
                    "Der Schummel-Verstärkungsbonus ist nur in der Reinforcements-Phase erlaubt."
                ClaimCheatReinforcementBonusErrorCode.ALREADY_USED ->
                    "Spieler '${payload.playerId.value}' hat den " +
                        "Schummel-Verstärkungsbonus bereits verwendet."
                ClaimCheatReinforcementBonusErrorCode.FORCED_TRADE_REQUIRED ->
                    "Der Schummel-Verstärkungsbonus ist gesperrt: " +
                        "Spieler '${payload.playerId.value}' muss zuerst Karten eintauschen."
            }

        return ClaimCheatReinforcementBonusErrorResponse(code = code, reason = reason)
    }

    private fun reportCheatErrorResponse(
        request: DecodedNetworkRequest,
        payload: ReportCheatRequest,
        cause: Throwable,
    ): ReportCheatErrorResponse {
        val code =
            when (cause.message) {
                "GAME_NOT_FOUND" -> ReportCheatErrorCode.GAME_NOT_FOUND
                "REQUESTER_MISMATCH" -> ReportCheatErrorCode.REQUESTER_MISMATCH
                "GAME_NOT_RUNNING" -> ReportCheatErrorCode.GAME_NOT_RUNNING
                "SELF_REPORT" -> ReportCheatErrorCode.SELF_REPORT
                "ALREADY_REPORTED" -> ReportCheatErrorCode.ALREADY_REPORTED
                else -> ReportCheatErrorCode.UNKNOWN_PLAYER
            }

        val reason =
            when (code) {
                ReportCheatErrorCode.GAME_NOT_FOUND ->
                    "Lobby '${payload.lobbyCode.value}' wurde nicht gefunden."
                ReportCheatErrorCode.REQUESTER_MISMATCH -> {
                    val contextPlayerId = request.context.playerId
                    if (contextPlayerId == null) {
                        connectionNotAssignedToLobby(payload.lobbyCode)
                    } else {
                        "Reporter '${payload.reporterPlayerId.value}' passt nicht zur " +
                            "aktuellen Connection '${contextPlayerId.value}'."
                    }
                }
                ReportCheatErrorCode.GAME_NOT_RUNNING ->
                    "Cheat-Meldungen sind erst in einem laufenden Spiel möglich."
                ReportCheatErrorCode.UNKNOWN_PLAYER ->
                    "Reporter '${payload.reporterPlayerId.value}' oder beschuldigter Spieler " +
                        "'${payload.accusedPlayerId.value}' ist nicht Teil der Lobby."
                ReportCheatErrorCode.SELF_REPORT ->
                    "Spieler können sich nicht selbst als Cheater melden."
                ReportCheatErrorCode.ALREADY_REPORTED ->
                    "Dieser Cheat wurde von Spieler '${payload.reporterPlayerId.value}' " +
                        "bereits gemeldet."
            }

        return ReportCheatErrorResponse(code = code, reason = reason)
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
                    REINFORCEMENTS_PHASE_END_BLOCKED_PREFIX +
                        "keine ausstehenden Verstärkungen mehr vorhanden sind."
                ConfirmReinforcementsDoneErrorCode.FORCED_TRADE_REQUIRED ->
                    REINFORCEMENTS_PHASE_END_BLOCKED_PREFIX +
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
        event: LobbyEvent,
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
            is CharacterSelectRequest -> payload.lobbyCode
            is ClaimCheatReinforcementBonusRequest -> payload.lobbyCode
            is ConfirmAttackDoneRequest -> payload.lobbyCode
            is ConfirmReinforcementsDoneRequest -> payload.lobbyCode
            is FortifyMoveRequest -> payload.lobbyCode
            is GameStateCatchUpRequest -> payload.lobbyCode
            is GameStatePrivateGetRequest -> payload.lobbyCode
            is JoinLobbyRequest -> payload.lobbyCode
            is LeaveLobbyRequest -> payload.lobbyCode
            is LobbyPlayerCountRequest -> payload.lobbyCode
            is KickPlayerRequest -> payload.lobbyCode
            is PlaceReinforcementsRequest -> payload.lobbyCode
            is ReportCheatRequest -> payload.lobbyCode
            is StartGameRequest -> payload.lobbyCode
            is StartPlayerSetRequest -> payload.lobbyCode
            is TradeInCardsRequest -> payload.lobbyCode
            is MapGetRequest -> payload.lobbyCode
            is TurnAdvanceRequest -> payload.lobbyCode
            is TurnStateGetRequest -> payload.lobbyCode
            else -> null
        }

    private fun playerIdOf(payload: NetworkMessagePayload): PlayerId? =
        when (payload) {
            is AttackRequest -> payload.playerId
            is CharacterSelectRequest -> payload.playerId
            is ClaimCheatReinforcementBonusRequest -> payload.playerId
            is ConfirmAttackDoneRequest -> payload.playerId
            is ConfirmReinforcementsDoneRequest -> payload.playerId
            is FortifyMoveRequest -> payload.playerId
            is GameStatePrivateGetRequest -> payload.playerId
            is KickPlayerRequest -> payload.requesterPlayerId
            is PlaceReinforcementsRequest -> payload.playerId
            is ReportCheatRequest -> payload.reporterPlayerId
            is StartPlayerSetRequest -> payload.requesterPlayerId
            is TradeInCardsRequest -> payload.playerId
            is TurnAdvanceRequest -> payload.playerId
            else -> null
        }

    private fun clientRequestIdOf(payload: NetworkMessagePayload): String? =
        when (payload) {
            is AttackRequest -> payload.requestId
            else -> null
        }

    private fun responseTypeFor(payload: NetworkMessagePayload): String? =
        when (payload) {
            is JoinLobbyRequest -> JoinLobbyResponse::class.simpleName
            is LeaveLobbyRequest -> LeaveLobbyResponse::class.simpleName
            is KickPlayerRequest -> KickPlayerResponse::class.simpleName
            is StartGameRequest -> StartGameResponse::class.simpleName
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

        broadcastFullSnapshot(
            lobbyCode = lobbyCode,
            currentState = currentState,
            trigger = RoundSnapshotTrigger.TURN_CHANGE_BROADCAST,
            logContext = "turn change",
        )
    }

    /**
     * Sendet nach Leave/Kick einen vollständigen öffentlichen State.
     *
     * PlayerLeft/PlayerKicked-Events aktualisieren nur die Spielerliste. Der
     * zusätzliche Snapshot stellt sicher, dass alle Clients verlassene
     * Territorien, Host-Wechsel und private/öffentliche Ableitungen auf derselben
     * Version sehen.
     *
     * @param lobbyCode Lobby, deren Spielerliste geändert wurde.
     * @param payload Request, der den möglichen Membership-Wechsel ausgelöst hat.
     */
    private suspend fun broadcastFullSnapshotAfterMembershipChangeIfNeeded(
        lobbyCode: LobbyCode,
        payload: NetworkMessagePayload,
    ) {
        if (payload !is LeaveLobbyRequest && payload !is KickPlayerRequest) {
            return
        }

        val currentState = lobbyManager.getLobby(lobbyCode)?.currentState() ?: return
        if (!currentState.hasMap() || currentState.status != GameStatus.RUNNING) {
            return
        }

        broadcastFullSnapshot(
            lobbyCode = lobbyCode,
            currentState = currentState,
            trigger = RoundSnapshotTrigger.MEMBERSHIP_CHANGE_BROADCAST,
            logContext = "membership change",
        )
    }

    /**
     * Baut, prüft und sendet einen öffentlichen Vollsnapshot.
     *
     * @param lobbyCode Lobby, an deren Mitglieder der Snapshot gesendet wird.
     * @param currentState bereits geladener, aktueller GameState.
     * @param trigger Diagnosemarker für den Snapshot-History-Buffer.
     * @param logContext kurzer Kontext für das technische Server-Log.
     */
    private suspend fun broadcastFullSnapshot(
        lobbyCode: LobbyCode,
        currentState: GameState,
        trigger: RoundSnapshotTrigger,
        logContext: String,
    ) {
        val payload = publicGameStateBuilder.buildSnapshotBroadcast(currentState)
        val encodedPayloadSize =
            runCatching { requirePublicStatePayloadWithinLimit(payload) }
                .getOrElse { cause ->
                    if (cause is PublicStatePayloadTooLargeException) {
                        logger.warn(
                            "Skipping public snapshot broadcast for lobby {} because payload " +
                                "size {} exceeds configured limit {}",
                            lobbyCode.value,
                            cause.encodedSizeBytes,
                            cause.maxAllowedBytes,
                        )
                        return
                    }
                    throw cause
                }
        logger.info(
            "Public snapshot broadcast: context={} lobbyCode={} playerId={} stateVersion={} " +
                "turnCount={} mapHash={} payloadBytes={}",
            logContext,
            lobbyCode.value,
            payload.turnState.activePlayerId.value,
            payload.stateVersion,
            payload.turnState.turnCount,
            payload.determinism.mapHash,
            encodedPayloadSize,
        )
        gameStateDelivery.broadcastPublicState(
            lobbyCode = lobbyCode,
            payload = payload,
        )
        roundHistoryBuffer(lobbyCode).recordSnapshot(
            roundIndex = payload.turnState.turnCount,
            stateVersion = payload.stateVersion,
            trigger = trigger,
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
                sendBestEffortPayload(
                    connectionId = activeConnectionId,
                    payload = event,
                    context = "player connection lost broadcast",
                )
            }
    }

    /**
     * Sendet dem beitretenden oder reconnectenden Client den aktuellen Status
     * der bereits bekannten Lobby-Spieler.
     */
    private suspend fun replayConnectionStatuses(
        connectionId: ConnectionId,
        lobbyCode: LobbyCode,
        excludedPlayerId: PlayerId,
    ) {
        val members = lobbyManager.getLobby(lobbyCode)?.currentState()?.players.orEmpty()
        members
            .filter { playerId -> playerId != excludedPlayerId }
            .map { playerId -> playerId to connectionStatusFor(playerId) }
            .filter { (_, status) -> status == ConnectionStatus.DISCONNECTED }
            .forEach { (playerId, status) ->
                sendBestEffortPayload(
                    connectionId = connectionId,
                    payload =
                        ConnectionStatusUpdateEvent(
                            lobbyCode = lobbyCode,
                            playerId = playerId,
                            status = status,
                        ),
                    context = "connection status replay",
                )
            }
    }

    /**
     * Broadcastet eine Statusänderung an alle aktuell erreichbaren Lobby-Mitglieder.
     */
    private suspend fun broadcastConnectionStatusUpdate(
        lobbyCode: LobbyCode,
        playerId: PlayerId,
        status: ConnectionStatus,
    ) {
        val members = lobbyManager.getLobby(lobbyCode)?.currentState()?.players.orEmpty()
        val event =
            ConnectionStatusUpdateEvent(
                lobbyCode = lobbyCode,
                playerId = playerId,
                status = status,
            )

        logger.info(
            "Broadcasting messageType={} lobbyCode={} playerId={} status={}",
            MessageType.LOBBY_CONNECTION_STATUS_UPDATE_BROADCAST.name,
            lobbyCode.value,
            playerId.value,
            status.name,
        )

        members
            .mapNotNull(connectionIdResolver)
            .distinct()
            .forEach { connectionId ->
                sendBestEffortPayload(
                    connectionId = connectionId,
                    payload = event,
                    context = "connection status update broadcast",
                )
            }
    }

    private fun connectionStatusFor(playerId: PlayerId): ConnectionStatus =
        if (isPlayerConnected(playerId)) {
            ConnectionStatus.CONNECTED
        } else {
            ConnectionStatus.DISCONNECTED
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

    private fun TurnStateUpdatedEvent.toTurnState(): TurnState =
        TurnState(
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

    private fun requirePublicStatePayloadWithinLimit(payload: PublicGameStatePayload): Int {
        val encodedSize = MessageCodec.encode(payload).size
        if (encodedSize > publicStatePayloadMaxBytes) {
            throw PublicStatePayloadTooLargeException(
                payload = payload,
                encodedSizeBytes = encodedSize,
                maxAllowedBytes = publicStatePayloadMaxBytes,
            )
        }
        return encodedSize
    }

    private fun requirePrivateStatePayloadWithinLimit(payload: PrivateGameStatePayload): Int {
        val encodedSize = MessageCodec.encode(payload).size
        if (encodedSize > privateStatePayloadMaxBytes) {
            throw PrivateStatePayloadTooLargeException(
                payload = payload,
                encodedSizeBytes = encodedSize,
                maxAllowedBytes = privateStatePayloadMaxBytes,
            )
        }
        return encodedSize
    }

    private suspend fun sendBestEffortPayload(
        connectionId: ConnectionId,
        payload: NetworkMessagePayload,
        context: String,
    ) {
        runCatching { network.send(connectionId, payload) }
            .onFailure { cause ->
                logger.warn(
                    "Best-effort payload delivery failed during {} on connection {} payload {}",
                    context,
                    connectionId.value,
                    payload::class.simpleName,
                    cause,
                )
            }
    }

    private suspend fun sendPrivateStateUpdateBestEffort(
        lobbyCode: LobbyCode,
        payload: PrivateGameStatePayload,
        context: String,
    ) {
        runCatching { requirePrivateStatePayloadWithinLimit(payload) }
            .onFailure { cause ->
                if (cause is PrivateStatePayloadTooLargeException) {
                    logger.warn(
                        "Skipping private payload during {} for lobby {} recipient {} " +
                            "because payload size {} exceeds configured limit {}",
                        context,
                        lobbyCode.value,
                        payload.recipientPlayerId.value,
                        cause.encodedSizeBytes,
                        cause.maxAllowedBytes,
                    )
                    return
                }
                throw cause
            }
        gameStateDelivery.sendPrivateState(lobbyCode, payload)
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
        val digits = "0123456789"
        return buildString(4) {
            repeat(4) {
                append(digits[Random.nextInt(digits.length)])
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
        hand: List<CardState> = state.handOf(playerId),
    ): Boolean =
        state.tradeRequiredOnNextReinforcementPhaseFor(playerId) ||
            (hand.size >= 5 && CardSetValidator.canMakeAnySet(hand))

    private suspend fun sendUpdatedHandsAfterEliminationIfNeeded(
        lobbyCode: LobbyCode,
        stateBeforeAttack: GameState,
        events: List<LobbyEvent>,
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
                sendPrivateStateUpdateBestEffort(
                    lobbyCode = lobbyCode,
                    payload = PlayerHandUpdatedEvent.fromGameState(updatedState, playerId),
                    context = "elimination private hand update",
                )
            }
    }

    /**
     * Plant den automatischen Wechsel von Attack nach Fortify, wenn kein
     * legaler Angriff mehr möglich ist.
     *
     * Der Wechsel wird nicht sofort ausgeführt, damit alle Clients das
     * Kampfergebnis kurz sehen können. Vor dem tatsächlichen Submit wird der
     * Zustand erneut geprüft; wenn der Spieler die Phase vorher manuell beendet
     * hat, läuft der Job still aus.
     *
     * @param request ursprünglicher Request-Kontext für Audit und EventContext
     * @param lobbyCode betroffene Lobby
     * @param playerId aktiver Spieler der Angriffsphase
     */
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

        CoroutineScope(coroutineContext).launch {
            delay(attackAutoAdvanceDelayMillis)
            runCatching {
                autoAdvanceAttackPhaseIfNoValidAttacksAfterDelay(
                    request = request,
                    lobbyCode = lobbyCode,
                    playerId = playerId,
                )
            }.onFailure { cause ->
                logger.warn(
                    "Delayed attack auto-advance failed: lobbyCode={} playerId={}",
                    lobbyCode.value,
                    playerId.value,
                    cause,
                )
            }
        }
    }

    /**
     * Führt den verzögerten Attack-Auto-Skip nach erneuter Validierung aus.
     *
     * @param request ursprünglicher Request-Kontext für Audit und EventContext
     * @param lobbyCode betroffene Lobby
     * @param playerId aktiver Spieler der Angriffsphase
     */
    private suspend fun autoAdvanceAttackPhaseIfNoValidAttacksAfterDelay(
        request: DecodedNetworkRequest,
        lobbyCode: LobbyCode,
        playerId: PlayerId,
    ) {
        val stateAfterDelay =
            lobbyManager.getLobby(lobbyCode)?.currentState()
                ?: return
        val turnStateAfterDelay = stateAfterDelay.resolvedTurnState ?: return
        if (
            stateAfterDelay.status != GameStatus.RUNNING ||
            turnStateAfterDelay.isPaused ||
            turnStateAfterDelay.turnPhase != TurnPhase.ATTACK ||
            turnStateAfterDelay.activePlayerId != playerId ||
            stateAfterDelay.hasAnyValidAttack(playerId)
        ) {
            return
        }

        val previousTurnState = turnStateAfterDelay
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

    private fun summarizeAttackResult(events: List<LobbyEvent>): String {
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

    private fun logAttackResolved(
        resolvedAttack: AttackResolvedEvent?,
        result: String,
        updatedStateVersion: Long,
    ) {
        if (!logger.isInfoEnabled) {
            return
        }

        logger.info(
            "Attack resolved: {}",
            attackResolvedLogMessage(
                resolvedAttack = resolvedAttack,
                result = result,
                updatedStateVersion = updatedStateVersion,
            ),
        )
    }

    private fun attackResolvedLogMessage(
        resolvedAttack: AttackResolvedEvent?,
        result: String,
        updatedStateVersion: Long,
    ): String {
        if (resolvedAttack == null) {
            return "result=$result updatedStateVersion=$updatedStateVersion event=null"
        }

        return buildString {
            append("result=").append(result)
            append(" lobbyCode=").append(resolvedAttack.lobbyCode.value)
            append(" attackerPlayerId=").append(resolvedAttack.attackerPlayerId.value)
            append(" defenderPlayerId=").append(resolvedAttack.defenderPlayerId.value)
            append(" fromTerritoryId=").append(resolvedAttack.fromTerritoryId.value)
            append(" toTerritoryId=").append(resolvedAttack.toTerritoryId.value)
            append(" attackTroops=").append(resolvedAttack.attackTroops)
            append(" sourceTroopsBefore=").append(resolvedAttack.sourceTroopsBefore)
            append(" targetTroopsBefore=").append(resolvedAttack.targetTroopsBefore)
            append(" requestedAttackDice=").append(resolvedAttack.requestedAttackDice)
            append(" attackDice=").append(resolvedAttack.attackDice)
            append(" defendDice=").append(resolvedAttack.defendDice)
            append(" attackerRolls=").append(resolvedAttack.attackerRolls)
            append(" defenderRolls=").append(resolvedAttack.defenderRolls)
            append(" rngTrace=").append(resolvedAttack.rngTrace)
            append(" rngStateBefore=").append(resolvedAttack.rngStateBefore)
            append(" rngStateAfter=").append(resolvedAttack.rngStateAfter)
            append(" attackerLosses=").append(resolvedAttack.attackerLosses)
            append(" defenderLosses=").append(resolvedAttack.defenderLosses)
            append(" attackerRemaining=").append(resolvedAttack.attackerRemaining)
            append(" defenderRemaining=").append(resolvedAttack.defenderRemaining)
            append(" occupyingTroopCount=").append(resolvedAttack.occupyingTroopCount)
            append(" minOccupyingTroops=").append(resolvedAttack.minOccupyingTroops)
            append(" eventStateVersion=").append(resolvedAttack.stateVersion)
            append(" capture=").append(resolvedAttack.capture)
            append(" updatedStateVersion=").append(updatedStateVersion)
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
