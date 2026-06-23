package at.aau.pulverfass.shared.message.codec

import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.message.connection.event.GlobalPlayerCountEvent
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponse
import at.aau.pulverfass.shared.message.lobby.event.AttackResolvedBroadcastEvent
import at.aau.pulverfass.shared.message.lobby.event.CharacterSelectedBroadcast
import at.aau.pulverfass.shared.message.lobby.event.ConnectionStatusUpdateEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.MatchEndedBroadcastEvent
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerCountUpdateEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerCountUpdateEventSerializer
import at.aau.pulverfass.shared.message.lobby.event.PlayerHandUpdatedEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerKickedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEvent
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
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CharacterSelectErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ClaimCheatReinforcementBonusErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmAttackDoneErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.FortifyMoveErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.KickPlayerErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.LobbyPlayerCountErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ReportCheatErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartGameErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorResponse
import at.aau.pulverfass.shared.message.protocol.MessageType
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadClassException
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadTypeException
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

/**
 * Verwaltet die zentrale Zuordnung zwischen MessageTypes und ihren konkreten
 * Payload-Klassen.
 */
internal object NetworkPayloadRegistry {
    private val payloadTypeByClass =
        mapOf<KClass<out NetworkMessagePayload>, MessageType>(
            ConnectionResponse::class to MessageType.CONNECTION_RESPONSE,
            GlobalPlayerCountEvent::class to MessageType.GLOBAL_PLAYER_COUNT_BROADCAST,
            CharacterSelectRequest::class to MessageType.LOBBY_CHARACTER_SELECT_REQUEST,
            CharacterSelectResponse::class to MessageType.LOBBY_CHARACTER_SELECT_RESPONSE,
            CharacterSelectErrorResponse::class to
                MessageType.LOBBY_CHARACTER_SELECT_ERROR_RESPONSE,
            CharacterSelectedBroadcast::class to
                MessageType.LOBBY_CHARACTER_SELECTED_BROADCAST,
            ReconnectRequest::class to MessageType.CONNECTION_RECONNECT_REQUEST,
            ReconnectResponse::class to MessageType.CONNECTION_RECONNECT_RESPONSE,
            AttackRequest::class to MessageType.LOBBY_ATTACK_REQUEST,
            AttackResponse::class to MessageType.LOBBY_ATTACK_RESPONSE,
            AttackErrorResponse::class to MessageType.LOBBY_ATTACK_ERROR_RESPONSE,
            AttackResolvedBroadcastEvent::class to MessageType.LOBBY_ATTACK_RESOLVED_BROADCAST,
            PlayerEliminatedEvent::class to MessageType.LOBBY_PLAYER_ELIMINATED_BROADCAST,
            ConfirmAttackDoneRequest::class to MessageType.LOBBY_CONFIRM_ATTACK_DONE_REQUEST,
            ConfirmAttackDoneResponse::class to MessageType.LOBBY_CONFIRM_ATTACK_DONE_RESPONSE,
            ConfirmAttackDoneErrorResponse::class to
                MessageType.LOBBY_CONFIRM_ATTACK_DONE_ERROR_RESPONSE,
            ConfirmReinforcementsDoneRequest::class to
                MessageType.LOBBY_CONFIRM_REINFORCEMENTS_DONE_REQUEST,
            ConfirmReinforcementsDoneResponse::class to
                MessageType.LOBBY_CONFIRM_REINFORCEMENTS_DONE_RESPONSE,
            ConfirmReinforcementsDoneErrorResponse::class to
                MessageType.LOBBY_CONFIRM_REINFORCEMENTS_DONE_ERROR_RESPONSE,
            CreateLobbyRequest::class to MessageType.LOBBY_CREATE_REQUEST,
            CreateLobbyErrorResponse::class to MessageType.LOBBY_CREATE_ERROR_RESPONSE,
            CreateLobbyResponse::class to MessageType.LOBBY_CREATE_RESPONSE,
            JoinLobbyRequest::class to MessageType.LOBBY_JOIN_REQUEST,
            JoinLobbyErrorResponse::class to MessageType.LOBBY_JOIN_ERROR_RESPONSE,
            JoinLobbyResponse::class to MessageType.LOBBY_JOIN_RESPONSE,
            PlayerJoinedLobbyEvent::class to MessageType.LOBBY_PLAYER_JOINED_BROADCAST,
            PlayerConnectionLostEvent::class to
                MessageType.LOBBY_PLAYER_CONNECTION_LOST_BROADCAST,
            ConnectionStatusUpdateEvent::class to
                MessageType.LOBBY_CONNECTION_STATUS_UPDATE_BROADCAST,
            LobbyPlayerCountRequest::class to MessageType.LOBBY_PLAYER_COUNT_REQUEST,
            LobbyPlayerCountResponse::class to MessageType.LOBBY_PLAYER_COUNT_RESPONSE,
            PlayerCountUpdateEvent::class to MessageType.LOBBY_PLAYER_COUNT_UPDATE_BROADCAST,
            LobbyPlayerCountErrorResponse::class to
                MessageType.LOBBY_PLAYER_COUNT_ERROR_RESPONSE,
            FortifyMoveRequest::class to MessageType.LOBBY_FORTIFY_MOVE_REQUEST,
            FortifyMoveResponse::class to MessageType.LOBBY_FORTIFY_MOVE_RESPONSE,
            FortifyMoveErrorResponse::class to
                MessageType.LOBBY_FORTIFY_MOVE_ERROR_RESPONSE,
            ClaimCheatReinforcementBonusRequest::class to
                MessageType.LOBBY_CHEAT_REINFORCEMENT_BONUS_REQUEST,
            ClaimCheatReinforcementBonusResponse::class to
                MessageType.LOBBY_CHEAT_REINFORCEMENT_BONUS_RESPONSE,
            ClaimCheatReinforcementBonusErrorResponse::class to
                MessageType.LOBBY_CHEAT_REINFORCEMENT_BONUS_ERROR_RESPONSE,
            ReportCheatRequest::class to MessageType.LOBBY_REPORT_CHEAT_REQUEST,
            ReportCheatResponse::class to MessageType.LOBBY_REPORT_CHEAT_RESPONSE,
            ReportCheatErrorResponse::class to MessageType.LOBBY_REPORT_CHEAT_ERROR_RESPONSE,
            PendingReinforcementsChangedEvent::class to
                MessageType.LOBBY_PENDING_REINFORCEMENTS_CHANGED_BROADCAST,
            ReinforcementsGrantedEvent::class to
                MessageType.LOBBY_REINFORCEMENTS_GRANTED_BROADCAST,
            LeaveLobbyRequest::class to MessageType.LOBBY_LEAVE_REQUEST,
            LeaveLobbyResponse::class to MessageType.LOBBY_LEAVE_RESPONSE,
            PlayerLeftLobbyEvent::class to MessageType.LOBBY_PLAYER_LEFT_BROADCAST,
            KickPlayerRequest::class to MessageType.LOBBY_KICK_REQUEST,
            KickPlayerResponse::class to MessageType.LOBBY_KICK_RESPONSE,
            KickPlayerErrorResponse::class to MessageType.LOBBY_KICK_ERROR_RESPONSE,
            PlayerKickedLobbyEvent::class to MessageType.LOBBY_PLAYER_KICKED_BROADCAST,
            StartGameRequest::class to MessageType.LOBBY_START_REQUEST,
            StartGameResponse::class to MessageType.LOBBY_START_RESPONSE,
            StartGameErrorResponse::class to MessageType.LOBBY_START_ERROR_RESPONSE,
            GameStartedEvent::class to MessageType.LOBBY_GAME_STARTED_BROADCAST,
            MatchEndedBroadcastEvent::class to MessageType.LOBBY_ENDED_BROADCAST,
            PlayerHandUpdatedEvent::class to MessageType.LOBBY_PLAYER_HAND_UPDATED_EVENT,
            GameStateDeltaEvent::class to MessageType.LOBBY_GAME_STATE_DELTA_BROADCAST,
            PhaseBoundaryEvent::class to MessageType.LOBBY_PHASE_BOUNDARY_BROADCAST,
            GameStateSnapshotBroadcast::class to
                MessageType.LOBBY_GAME_STATE_SNAPSHOT_BROADCAST,
            GameStateCatchUpRequest::class to
                MessageType.LOBBY_GAME_STATE_CATCH_UP_REQUEST,
            GameStateCatchUpResponse::class to
                MessageType.LOBBY_GAME_STATE_CATCH_UP_RESPONSE,
            GameStateCatchUpErrorResponse::class to
                MessageType.LOBBY_GAME_STATE_CATCH_UP_ERROR_RESPONSE,
            GameStatePrivateGetRequest::class to
                MessageType.LOBBY_GAME_STATE_PRIVATE_GET_REQUEST,
            GameStatePrivateGetResponse::class to
                MessageType.LOBBY_GAME_STATE_PRIVATE_GET_RESPONSE,
            GameStatePrivateGetErrorResponse::class to
                MessageType.LOBBY_GAME_STATE_PRIVATE_GET_ERROR_RESPONSE,
            MapGetRequest::class to MessageType.LOBBY_MAP_GET_REQUEST,
            MapGetResponse::class to MessageType.LOBBY_MAP_GET_RESPONSE,
            MapGetErrorResponse::class to MessageType.LOBBY_MAP_GET_ERROR_RESPONSE,
            PlaceReinforcementsRequest::class to
                MessageType.LOBBY_PLACE_REINFORCEMENTS_REQUEST,
            PlaceReinforcementsResponse::class to
                MessageType.LOBBY_PLACE_REINFORCEMENTS_RESPONSE,
            PlaceReinforcementsErrorResponse::class to
                MessageType.LOBBY_PLACE_REINFORCEMENTS_ERROR_RESPONSE,
            TradeInCardsRequest::class to MessageType.LOBBY_TRADE_IN_CARDS_REQUEST,
            TradeInCardsResponse::class to MessageType.LOBBY_TRADE_IN_CARDS_RESPONSE,
            TradeInCardsErrorResponse::class to
                MessageType.LOBBY_TRADE_IN_CARDS_ERROR_RESPONSE,
            StartPlayerSetRequest::class to
                MessageType.LOBBY_START_PLAYER_SET_REQUEST,
            StartPlayerSetResponse::class to
                MessageType.LOBBY_START_PLAYER_SET_RESPONSE,
            StartPlayerSetErrorResponse::class to
                MessageType.LOBBY_START_PLAYER_SET_ERROR_RESPONSE,
            TerritoryOwnerChangedEvent::class to
                MessageType.LOBBY_TERRITORY_OWNER_CHANGED_BROADCAST,
            TerritoryTroopsChangedEvent::class to
                MessageType.LOBBY_TERRITORY_TROOPS_CHANGED_BROADCAST,
            TurnAdvanceRequest::class to MessageType.LOBBY_TURN_ADVANCE_REQUEST,
            TurnAdvanceResponse::class to MessageType.LOBBY_TURN_ADVANCE_RESPONSE,
            TurnAdvanceErrorResponse::class to
                MessageType.LOBBY_TURN_ADVANCE_ERROR_RESPONSE,
            TurnStateUpdatedEvent::class to
                MessageType.LOBBY_TURN_STATE_UPDATED_BROADCAST,
            TurnStateGetRequest::class to MessageType.LOBBY_TURN_STATE_GET_REQUEST,
            TurnStateGetResponse::class to MessageType.LOBBY_TURN_STATE_GET_RESPONSE,
            TurnStateGetErrorResponse::class to
                MessageType.LOBBY_TURN_STATE_GET_ERROR_RESPONSE,
        )

    private val payloadSerializerByClass =
        mapOf<KClass<out NetworkMessagePayload>, (NetworkMessagePayload) -> String>(
            ConnectionResponse::class to encodeWith(ConnectionResponse.serializer()),
            GlobalPlayerCountEvent::class to encodeWith(GlobalPlayerCountEvent.serializer()),
            CharacterSelectRequest::class to encodeWith(CharacterSelectRequest.serializer()),
            CharacterSelectResponse::class to encodeWith(CharacterSelectResponse.serializer()),
            CharacterSelectErrorResponse::class to
                encodeWith(CharacterSelectErrorResponse.serializer()),
            CharacterSelectedBroadcast::class to
                encodeWith(CharacterSelectedBroadcast.serializer()),
            ReconnectRequest::class to encodeWith(ReconnectRequest.serializer()),
            ReconnectResponse::class to encodeWith(ReconnectResponse.serializer()),
            AttackRequest::class to encodeWith(AttackRequest.serializer()),
            AttackResponse::class to encodeWith(AttackResponse.serializer()),
            AttackErrorResponse::class to encodeWith(AttackErrorResponse.serializer()),
            AttackResolvedBroadcastEvent::class to
                encodeWith(AttackResolvedBroadcastEvent.serializer()),
            PlayerEliminatedEvent::class to encodeWith(PlayerEliminatedEvent.serializer()),
            ConfirmAttackDoneRequest::class to
                encodeWith(ConfirmAttackDoneRequest.serializer()),
            ConfirmAttackDoneResponse::class to
                encodeWith(ConfirmAttackDoneResponse.serializer()),
            ConfirmAttackDoneErrorResponse::class to
                encodeWith(ConfirmAttackDoneErrorResponse.serializer()),
            ConfirmReinforcementsDoneRequest::class to
                encodeWith(ConfirmReinforcementsDoneRequest.serializer()),
            ConfirmReinforcementsDoneResponse::class to
                encodeWith(ConfirmReinforcementsDoneResponse.serializer()),
            ConfirmReinforcementsDoneErrorResponse::class to
                encodeWith(ConfirmReinforcementsDoneErrorResponse.serializer()),
            CreateLobbyRequest::class to encodeWith(CreateLobbyRequest.serializer()),
            CreateLobbyErrorResponse::class to
                encodeWith(CreateLobbyErrorResponse.serializer()),
            CreateLobbyResponse::class to encodeWith(CreateLobbyResponse.serializer()),
            JoinLobbyRequest::class to encodeWith(JoinLobbyRequest.serializer()),
            JoinLobbyErrorResponse::class to
                encodeWith(JoinLobbyErrorResponse.serializer()),
            JoinLobbyResponse::class to encodeWith(JoinLobbyResponse.serializer()),
            PlayerJoinedLobbyEvent::class to encodeWith(PlayerJoinedLobbyEvent.serializer()),
            PlayerConnectionLostEvent::class to
                encodeWith(PlayerConnectionLostEvent.serializer()),
            ConnectionStatusUpdateEvent::class to
                encodeWith(ConnectionStatusUpdateEvent.serializer()),
            LobbyPlayerCountRequest::class to
                encodeWith(LobbyPlayerCountRequest.serializer()),
            LobbyPlayerCountResponse::class to
                encodeWith(LobbyPlayerCountResponse.serializer()),
            PlayerCountUpdateEvent::class to
                encodeWith<PlayerCountUpdateEvent>(PlayerCountUpdateEventSerializer),
            LobbyPlayerCountErrorResponse::class to
                encodeWith(LobbyPlayerCountErrorResponse.serializer()),
            FortifyMoveRequest::class to encodeWith(FortifyMoveRequest.serializer()),
            FortifyMoveResponse::class to encodeWith(FortifyMoveResponse.serializer()),
            FortifyMoveErrorResponse::class to
                encodeWith(FortifyMoveErrorResponse.serializer()),
            ClaimCheatReinforcementBonusRequest::class to
                encodeWith(ClaimCheatReinforcementBonusRequest.serializer()),
            ClaimCheatReinforcementBonusResponse::class to
                encodeWith(ClaimCheatReinforcementBonusResponse.serializer()),
            ClaimCheatReinforcementBonusErrorResponse::class to
                encodeWith(ClaimCheatReinforcementBonusErrorResponse.serializer()),
            ReportCheatRequest::class to encodeWith(ReportCheatRequest.serializer()),
            ReportCheatResponse::class to encodeWith(ReportCheatResponse.serializer()),
            ReportCheatErrorResponse::class to
                encodeWith(ReportCheatErrorResponse.serializer()),
            PendingReinforcementsChangedEvent::class to
                encodeWith(PendingReinforcementsChangedEvent.serializer()),
            ReinforcementsGrantedEvent::class to
                encodeWith(ReinforcementsGrantedEvent.serializer()),
            LeaveLobbyRequest::class to encodeWith(LeaveLobbyRequest.serializer()),
            LeaveLobbyResponse::class to encodeWith(LeaveLobbyResponse.serializer()),
            PlayerLeftLobbyEvent::class to encodeWith(PlayerLeftLobbyEvent.serializer()),
            KickPlayerRequest::class to encodeWith(KickPlayerRequest.serializer()),
            KickPlayerResponse::class to encodeWith(KickPlayerResponse.serializer()),
            KickPlayerErrorResponse::class to
                encodeWith(KickPlayerErrorResponse.serializer()),
            PlayerKickedLobbyEvent::class to encodeWith(PlayerKickedLobbyEvent.serializer()),
            StartGameRequest::class to encodeWith(StartGameRequest.serializer()),
            StartGameResponse::class to encodeWith(StartGameResponse.serializer()),
            StartGameErrorResponse::class to encodeWith(StartGameErrorResponse.serializer()),
            GameStartedEvent::class to encodeWith(GameStartedEvent.serializer()),
            MatchEndedBroadcastEvent::class to
                encodeWith(MatchEndedBroadcastEvent.serializer()),
            PlayerHandUpdatedEvent::class to encodeWith(PlayerHandUpdatedEvent.serializer()),
            GameStateDeltaEvent::class to encodeWith(GameStateDeltaEvent.serializer()),
            PhaseBoundaryEvent::class to encodeWith(PhaseBoundaryEvent.serializer()),
            GameStateSnapshotBroadcast::class to
                encodeWith(GameStateSnapshotBroadcast.serializer()),
            GameStateCatchUpRequest::class to encodeWith(GameStateCatchUpRequest.serializer()),
            GameStateCatchUpResponse::class to
                encodeWith(GameStateCatchUpResponse.serializer()),
            GameStateCatchUpErrorResponse::class to
                encodeWith(GameStateCatchUpErrorResponse.serializer()),
            GameStatePrivateGetRequest::class to
                encodeWith(GameStatePrivateGetRequest.serializer()),
            GameStatePrivateGetResponse::class to
                encodeWith(GameStatePrivateGetResponse.serializer()),
            GameStatePrivateGetErrorResponse::class to
                encodeWith(GameStatePrivateGetErrorResponse.serializer()),
            MapGetRequest::class to encodeWith(MapGetRequest.serializer()),
            MapGetResponse::class to encodeWith(MapGetResponse.serializer()),
            MapGetErrorResponse::class to encodeWith(MapGetErrorResponse.serializer()),
            PlaceReinforcementsRequest::class to
                encodeWith(PlaceReinforcementsRequest.serializer()),
            PlaceReinforcementsResponse::class to
                encodeWith(PlaceReinforcementsResponse.serializer()),
            PlaceReinforcementsErrorResponse::class to
                encodeWith(PlaceReinforcementsErrorResponse.serializer()),
            TradeInCardsRequest::class to encodeWith(TradeInCardsRequest.serializer()),
            TradeInCardsResponse::class to encodeWith(TradeInCardsResponse.serializer()),
            TradeInCardsErrorResponse::class to
                encodeWith(TradeInCardsErrorResponse.serializer()),
            StartPlayerSetRequest::class to encodeWith(StartPlayerSetRequest.serializer()),
            StartPlayerSetResponse::class to encodeWith(StartPlayerSetResponse.serializer()),
            StartPlayerSetErrorResponse::class to
                encodeWith(StartPlayerSetErrorResponse.serializer()),
            TerritoryOwnerChangedEvent::class to
                encodeWith(TerritoryOwnerChangedEvent.serializer()),
            TerritoryTroopsChangedEvent::class to
                encodeWith(TerritoryTroopsChangedEvent.serializer()),
            TurnAdvanceRequest::class to encodeWith(TurnAdvanceRequest.serializer()),
            TurnAdvanceResponse::class to encodeWith(TurnAdvanceResponse.serializer()),
            TurnAdvanceErrorResponse::class to
                encodeWith(TurnAdvanceErrorResponse.serializer()),
            TurnStateUpdatedEvent::class to encodeWith(TurnStateUpdatedEvent.serializer()),
            TurnStateGetRequest::class to encodeWith(TurnStateGetRequest.serializer()),
            TurnStateGetResponse::class to encodeWith(TurnStateGetResponse.serializer()),
            TurnStateGetErrorResponse::class to
                encodeWith(TurnStateGetErrorResponse.serializer()),
        )

    private val payloadDeserializerByType =
        mapOf<MessageType, (String) -> NetworkMessagePayload>(
            MessageType.CONNECTION_RESPONSE to decodeWith(ConnectionResponse.serializer()),
            MessageType.GLOBAL_PLAYER_COUNT_BROADCAST to
                decodeWith(GlobalPlayerCountEvent.serializer()),
            MessageType.LOBBY_CHARACTER_SELECT_REQUEST to
                decodeWith(CharacterSelectRequest.serializer()),
            MessageType.LOBBY_CHARACTER_SELECT_RESPONSE to
                decodeWith(CharacterSelectResponse.serializer()),
            MessageType.LOBBY_CHARACTER_SELECT_ERROR_RESPONSE to
                decodeWith(CharacterSelectErrorResponse.serializer()),
            MessageType.LOBBY_CHARACTER_SELECTED_BROADCAST to
                decodeWith(CharacterSelectedBroadcast.serializer()),
            MessageType.CONNECTION_RECONNECT_REQUEST to
                decodeWith(ReconnectRequest.serializer()),
            MessageType.CONNECTION_RECONNECT_RESPONSE to
                decodeWith(ReconnectResponse.serializer()),
            MessageType.LOBBY_ATTACK_REQUEST to decodeWith(AttackRequest.serializer()),
            MessageType.LOBBY_ATTACK_RESPONSE to decodeWith(AttackResponse.serializer()),
            MessageType.LOBBY_ATTACK_ERROR_RESPONSE to
                decodeWith(AttackErrorResponse.serializer()),
            MessageType.LOBBY_ATTACK_RESOLVED_BROADCAST to
                decodeWith(AttackResolvedBroadcastEvent.serializer()),
            MessageType.LOBBY_PLAYER_ELIMINATED_BROADCAST to
                decodeWith(PlayerEliminatedEvent.serializer()),
            MessageType.LOBBY_CONFIRM_ATTACK_DONE_REQUEST to
                decodeWith(ConfirmAttackDoneRequest.serializer()),
            MessageType.LOBBY_CONFIRM_ATTACK_DONE_RESPONSE to
                decodeWith(ConfirmAttackDoneResponse.serializer()),
            MessageType.LOBBY_CONFIRM_ATTACK_DONE_ERROR_RESPONSE to
                decodeWith(ConfirmAttackDoneErrorResponse.serializer()),
            MessageType.LOBBY_CONFIRM_REINFORCEMENTS_DONE_REQUEST to
                decodeWith(ConfirmReinforcementsDoneRequest.serializer()),
            MessageType.LOBBY_CONFIRM_REINFORCEMENTS_DONE_RESPONSE to
                decodeWith(ConfirmReinforcementsDoneResponse.serializer()),
            MessageType.LOBBY_CONFIRM_REINFORCEMENTS_DONE_ERROR_RESPONSE to
                decodeWith(ConfirmReinforcementsDoneErrorResponse.serializer()),
            MessageType.LOBBY_CREATE_REQUEST to decodeWith(CreateLobbyRequest.serializer()),
            MessageType.LOBBY_CREATE_ERROR_RESPONSE to
                decodeWith(CreateLobbyErrorResponse.serializer()),
            MessageType.LOBBY_CREATE_RESPONSE to decodeWith(CreateLobbyResponse.serializer()),
            MessageType.LOBBY_JOIN_REQUEST to decodeWith(JoinLobbyRequest.serializer()),
            MessageType.LOBBY_JOIN_ERROR_RESPONSE to
                decodeWith(JoinLobbyErrorResponse.serializer()),
            MessageType.LOBBY_JOIN_RESPONSE to decodeWith(JoinLobbyResponse.serializer()),
            MessageType.LOBBY_PLAYER_JOINED_BROADCAST to
                decodeWith(PlayerJoinedLobbyEvent.serializer()),
            MessageType.LOBBY_PLAYER_CONNECTION_LOST_BROADCAST to
                decodeWith(PlayerConnectionLostEvent.serializer()),
            MessageType.LOBBY_CONNECTION_STATUS_UPDATE_BROADCAST to
                decodeWith(ConnectionStatusUpdateEvent.serializer()),
            MessageType.LOBBY_PLAYER_COUNT_REQUEST to
                decodeWith(LobbyPlayerCountRequest.serializer()),
            MessageType.LOBBY_PLAYER_COUNT_RESPONSE to
                decodeWith(LobbyPlayerCountResponse.serializer()),
            MessageType.LOBBY_PLAYER_COUNT_UPDATE_BROADCAST to
                decodeWith<PlayerCountUpdateEvent>(PlayerCountUpdateEventSerializer),
            MessageType.LOBBY_PLAYER_COUNT_ERROR_RESPONSE to
                decodeWith(LobbyPlayerCountErrorResponse.serializer()),
            MessageType.LOBBY_FORTIFY_MOVE_REQUEST to
                decodeWith(FortifyMoveRequest.serializer()),
            MessageType.LOBBY_FORTIFY_MOVE_RESPONSE to
                decodeWith(FortifyMoveResponse.serializer()),
            MessageType.LOBBY_FORTIFY_MOVE_ERROR_RESPONSE to
                decodeWith(FortifyMoveErrorResponse.serializer()),
            MessageType.LOBBY_CHEAT_REINFORCEMENT_BONUS_REQUEST to
                decodeWith(ClaimCheatReinforcementBonusRequest.serializer()),
            MessageType.LOBBY_CHEAT_REINFORCEMENT_BONUS_RESPONSE to
                decodeWith(ClaimCheatReinforcementBonusResponse.serializer()),
            MessageType.LOBBY_CHEAT_REINFORCEMENT_BONUS_ERROR_RESPONSE to
                decodeWith(ClaimCheatReinforcementBonusErrorResponse.serializer()),
            MessageType.LOBBY_REPORT_CHEAT_REQUEST to
                decodeWith(ReportCheatRequest.serializer()),
            MessageType.LOBBY_REPORT_CHEAT_RESPONSE to
                decodeWith(ReportCheatResponse.serializer()),
            MessageType.LOBBY_REPORT_CHEAT_ERROR_RESPONSE to
                decodeWith(ReportCheatErrorResponse.serializer()),
            MessageType.LOBBY_PENDING_REINFORCEMENTS_CHANGED_BROADCAST to
                decodeWith(PendingReinforcementsChangedEvent.serializer()),
            MessageType.LOBBY_REINFORCEMENTS_GRANTED_BROADCAST to
                decodeWith(ReinforcementsGrantedEvent.serializer()),
            MessageType.LOBBY_LEAVE_REQUEST to decodeWith(LeaveLobbyRequest.serializer()),
            MessageType.LOBBY_LEAVE_RESPONSE to decodeWith(LeaveLobbyResponse.serializer()),
            MessageType.LOBBY_PLAYER_LEFT_BROADCAST to
                decodeWith(PlayerLeftLobbyEvent.serializer()),
            MessageType.LOBBY_KICK_REQUEST to decodeWith(KickPlayerRequest.serializer()),
            MessageType.LOBBY_KICK_RESPONSE to decodeWith(KickPlayerResponse.serializer()),
            MessageType.LOBBY_KICK_ERROR_RESPONSE to
                decodeWith(KickPlayerErrorResponse.serializer()),
            MessageType.LOBBY_PLAYER_KICKED_BROADCAST to
                decodeWith(PlayerKickedLobbyEvent.serializer()),
            MessageType.LOBBY_START_REQUEST to decodeWith(StartGameRequest.serializer()),
            MessageType.LOBBY_START_RESPONSE to decodeWith(StartGameResponse.serializer()),
            MessageType.LOBBY_START_ERROR_RESPONSE to
                decodeWith(StartGameErrorResponse.serializer()),
            MessageType.LOBBY_GAME_STARTED_BROADCAST to decodeWith(GameStartedEvent.serializer()),
            MessageType.LOBBY_ENDED_BROADCAST to
                decodeWith(MatchEndedBroadcastEvent.serializer()),
            MessageType.LOBBY_PLAYER_HAND_UPDATED_EVENT to
                decodeWith(PlayerHandUpdatedEvent.serializer()),
            MessageType.LOBBY_GAME_STATE_DELTA_BROADCAST to
                decodeWith(GameStateDeltaEvent.serializer()),
            MessageType.LOBBY_PHASE_BOUNDARY_BROADCAST to
                decodeWith(PhaseBoundaryEvent.serializer()),
            MessageType.LOBBY_GAME_STATE_SNAPSHOT_BROADCAST to
                decodeWith(GameStateSnapshotBroadcast.serializer()),
            MessageType.LOBBY_GAME_STATE_CATCH_UP_REQUEST to
                decodeWith(GameStateCatchUpRequest.serializer()),
            MessageType.LOBBY_GAME_STATE_CATCH_UP_RESPONSE to
                decodeWith(GameStateCatchUpResponse.serializer()),
            MessageType.LOBBY_GAME_STATE_CATCH_UP_ERROR_RESPONSE to
                decodeWith(GameStateCatchUpErrorResponse.serializer()),
            MessageType.LOBBY_GAME_STATE_PRIVATE_GET_REQUEST to
                decodeWith(GameStatePrivateGetRequest.serializer()),
            MessageType.LOBBY_GAME_STATE_PRIVATE_GET_RESPONSE to
                decodeWith(GameStatePrivateGetResponse.serializer()),
            MessageType.LOBBY_GAME_STATE_PRIVATE_GET_ERROR_RESPONSE to
                decodeWith(GameStatePrivateGetErrorResponse.serializer()),
            MessageType.LOBBY_MAP_GET_REQUEST to decodeWith(MapGetRequest.serializer()),
            MessageType.LOBBY_MAP_GET_RESPONSE to decodeWith(MapGetResponse.serializer()),
            MessageType.LOBBY_MAP_GET_ERROR_RESPONSE to
                decodeWith(MapGetErrorResponse.serializer()),
            MessageType.LOBBY_PLACE_REINFORCEMENTS_REQUEST to
                decodeWith(PlaceReinforcementsRequest.serializer()),
            MessageType.LOBBY_PLACE_REINFORCEMENTS_RESPONSE to
                decodeWith(PlaceReinforcementsResponse.serializer()),
            MessageType.LOBBY_PLACE_REINFORCEMENTS_ERROR_RESPONSE to
                decodeWith(PlaceReinforcementsErrorResponse.serializer()),
            MessageType.LOBBY_TRADE_IN_CARDS_REQUEST to
                decodeWith(TradeInCardsRequest.serializer()),
            MessageType.LOBBY_TRADE_IN_CARDS_RESPONSE to
                decodeWith(TradeInCardsResponse.serializer()),
            MessageType.LOBBY_TRADE_IN_CARDS_ERROR_RESPONSE to
                decodeWith(TradeInCardsErrorResponse.serializer()),
            MessageType.LOBBY_START_PLAYER_SET_REQUEST to
                decodeWith(StartPlayerSetRequest.serializer()),
            MessageType.LOBBY_START_PLAYER_SET_RESPONSE to
                decodeWith(StartPlayerSetResponse.serializer()),
            MessageType.LOBBY_START_PLAYER_SET_ERROR_RESPONSE to
                decodeWith(StartPlayerSetErrorResponse.serializer()),
            MessageType.LOBBY_TERRITORY_OWNER_CHANGED_BROADCAST to
                decodeWith(TerritoryOwnerChangedEvent.serializer()),
            MessageType.LOBBY_TERRITORY_TROOPS_CHANGED_BROADCAST to
                decodeWith(TerritoryTroopsChangedEvent.serializer()),
            MessageType.LOBBY_TURN_ADVANCE_REQUEST to
                decodeWith(TurnAdvanceRequest.serializer()),
            MessageType.LOBBY_TURN_ADVANCE_RESPONSE to
                decodeWith(TurnAdvanceResponse.serializer()),
            MessageType.LOBBY_TURN_ADVANCE_ERROR_RESPONSE to
                decodeWith(TurnAdvanceErrorResponse.serializer()),
            MessageType.LOBBY_TURN_STATE_UPDATED_BROADCAST to
                decodeWith(TurnStateUpdatedEvent.serializer()),
            MessageType.LOBBY_TURN_STATE_GET_REQUEST to
                decodeWith(TurnStateGetRequest.serializer()),
            MessageType.LOBBY_TURN_STATE_GET_RESPONSE to
                decodeWith(TurnStateGetResponse.serializer()),
            MessageType.LOBBY_TURN_STATE_GET_ERROR_RESPONSE to
                decodeWith(TurnStateGetErrorResponse.serializer()),
        )

    @Suppress("UNCHECKED_CAST")
    private fun <T : NetworkMessagePayload> encodeWith(
        serializer: KSerializer<T>,
    ): (NetworkMessagePayload) -> String {
        return { payload ->
            NetworkJson.encodeToString(serializer, payload as T)
        }
    }

    private fun <T : NetworkMessagePayload> decodeWith(
        serializer: KSerializer<T>,
    ): (String) -> NetworkMessagePayload {
        return { json ->
            NetworkJson.decodeFromString(serializer, json)
        }
    }

    /**
     * Liefert den zu [payload] gehörigen [MessageType].
     */
    fun messageTypeFor(payload: NetworkMessagePayload): MessageType =
        payloadTypeByClass[payload::class]
            ?: throw UnsupportedPayloadClassException(payload.payloadClassName)

    /**
     * Serialisiert [payload] als JSON-String gemäß der registrierten Payload-Klasse.
     */
    fun serializePayload(payload: NetworkMessagePayload): String {
        val serializer = payloadSerializerByClass[payload::class]
        if (serializer == null) {
            throw UnsupportedPayloadClassException(payload.payloadClassName)
        }

        return serializer(payload)
    }

    /**
     * Deserialisiert [json] anhand des angegebenen [type] in ein konkretes Payload-Objekt.
     */
    fun deserializePayload(
        type: MessageType,
        json: String,
    ): NetworkMessagePayload {
        val deserializer = payloadDeserializerByType[type]
        if (deserializer == null) {
            throw UnsupportedPayloadTypeException(type)
        }

        return deserializer(json)
    }
}

/**
 * Vollqualifizierter Name der Payload-Klasse für Fehlermeldungen.
 */
internal val NetworkMessagePayload.payloadClassName: String
    get() = this::class.qualifiedName ?: this::class.toString()
