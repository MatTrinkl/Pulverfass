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
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
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
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorResponse
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
import kotlinx.serialization.json.Json

/**
 * Verwaltet die zentrale Zuordnung zwischen MessageTypes und ihren konkreten
 * Payload-Klassen.
 */
internal object NetworkPayloadRegistry {
    private val payloadTypeByClass =
        mapOf<Class<out NetworkMessagePayload>, MessageType>(
            ConnectionResponse::class.java to MessageType.CONNECTION_RESPONSE,
            GlobalPlayerCountEvent::class.java to MessageType.GLOBAL_PLAYER_COUNT_BROADCAST,
            ReconnectRequest::class.java to MessageType.CONNECTION_RECONNECT_REQUEST,
            ReconnectResponse::class.java to MessageType.CONNECTION_RECONNECT_RESPONSE,
            AttackRequest::class.java to MessageType.LOBBY_ATTACK_REQUEST,
            AttackResponse::class.java to MessageType.LOBBY_ATTACK_RESPONSE,
            AttackErrorResponse::class.java to MessageType.LOBBY_ATTACK_ERROR_RESPONSE,
            AttackResolvedBroadcastEvent::class.java to MessageType.LOBBY_ATTACK_RESOLVED_BROADCAST,
            PlayerEliminatedEvent::class.java to MessageType.LOBBY_PLAYER_ELIMINATED_BROADCAST,
            ConfirmAttackDoneRequest::class.java to MessageType.LOBBY_CONFIRM_ATTACK_DONE_REQUEST,
            ConfirmAttackDoneResponse::class.java to MessageType.LOBBY_CONFIRM_ATTACK_DONE_RESPONSE,
            ConfirmAttackDoneErrorResponse::class.java to
                MessageType.LOBBY_CONFIRM_ATTACK_DONE_ERROR_RESPONSE,
            ConfirmReinforcementsDoneRequest::class.java to
                MessageType.LOBBY_CONFIRM_REINFORCEMENTS_DONE_REQUEST,
            ConfirmReinforcementsDoneResponse::class.java to
                MessageType.LOBBY_CONFIRM_REINFORCEMENTS_DONE_RESPONSE,
            ConfirmReinforcementsDoneErrorResponse::class.java to
                MessageType.LOBBY_CONFIRM_REINFORCEMENTS_DONE_ERROR_RESPONSE,
            CreateLobbyRequest::class.java to MessageType.LOBBY_CREATE_REQUEST,
            CreateLobbyErrorResponse::class.java to MessageType.LOBBY_CREATE_ERROR_RESPONSE,
            CreateLobbyResponse::class.java to MessageType.LOBBY_CREATE_RESPONSE,
            JoinLobbyRequest::class.java to MessageType.LOBBY_JOIN_REQUEST,
            JoinLobbyErrorResponse::class.java to MessageType.LOBBY_JOIN_ERROR_RESPONSE,
            JoinLobbyResponse::class.java to MessageType.LOBBY_JOIN_RESPONSE,
            PlayerJoinedLobbyEvent::class.java to MessageType.LOBBY_PLAYER_JOINED_BROADCAST,
            PlayerConnectionLostEvent::class.java to
                MessageType.LOBBY_PLAYER_CONNECTION_LOST_BROADCAST,
            LobbyPlayerCountRequest::class.java to MessageType.LOBBY_PLAYER_COUNT_REQUEST,
            LobbyPlayerCountResponse::class.java to MessageType.LOBBY_PLAYER_COUNT_RESPONSE,
            PlayerCountUpdateEvent::class.java to MessageType.LOBBY_PLAYER_COUNT_UPDATE_BROADCAST,
            LobbyPlayerCountErrorResponse::class.java to
                MessageType.LOBBY_PLAYER_COUNT_ERROR_RESPONSE,
            FortifyMoveRequest::class.java to MessageType.LOBBY_FORTIFY_MOVE_REQUEST,
            FortifyMoveResponse::class.java to MessageType.LOBBY_FORTIFY_MOVE_RESPONSE,
            FortifyMoveErrorResponse::class.java to
                MessageType.LOBBY_FORTIFY_MOVE_ERROR_RESPONSE,
            PendingReinforcementsChangedEvent::class.java to
                MessageType.LOBBY_PENDING_REINFORCEMENTS_CHANGED_BROADCAST,
            ReinforcementsGrantedEvent::class.java to
                MessageType.LOBBY_REINFORCEMENTS_GRANTED_BROADCAST,
            LeaveLobbyRequest::class.java to MessageType.LOBBY_LEAVE_REQUEST,
            LeaveLobbyResponse::class.java to MessageType.LOBBY_LEAVE_RESPONSE,
            PlayerLeftLobbyEvent::class.java to MessageType.LOBBY_PLAYER_LEFT_BROADCAST,
            KickPlayerRequest::class.java to MessageType.LOBBY_KICK_REQUEST,
            KickPlayerResponse::class.java to MessageType.LOBBY_KICK_RESPONSE,
            KickPlayerErrorResponse::class.java to MessageType.LOBBY_KICK_ERROR_RESPONSE,
            PlayerKickedLobbyEvent::class.java to MessageType.LOBBY_PLAYER_KICKED_BROADCAST,
            StartGameRequest::class.java to MessageType.LOBBY_START_REQUEST,
            StartGameResponse::class.java to MessageType.LOBBY_START_RESPONSE,
            StartGameErrorResponse::class.java to MessageType.LOBBY_START_ERROR_RESPONSE,
            GameStartedEvent::class.java to MessageType.LOBBY_GAME_STARTED_BROADCAST,
            PlayerHandUpdatedEvent::class.java to MessageType.LOBBY_PLAYER_HAND_UPDATED_EVENT,
            GameStateDeltaEvent::class.java to MessageType.LOBBY_GAME_STATE_DELTA_BROADCAST,
            PhaseBoundaryEvent::class.java to MessageType.LOBBY_PHASE_BOUNDARY_BROADCAST,
            GameStateSnapshotBroadcast::class.java to
                MessageType.LOBBY_GAME_STATE_SNAPSHOT_BROADCAST,
            GameStateCatchUpRequest::class.java to
                MessageType.LOBBY_GAME_STATE_CATCH_UP_REQUEST,
            GameStateCatchUpResponse::class.java to
                MessageType.LOBBY_GAME_STATE_CATCH_UP_RESPONSE,
            GameStateCatchUpErrorResponse::class.java to
                MessageType.LOBBY_GAME_STATE_CATCH_UP_ERROR_RESPONSE,
            GameStatePrivateGetRequest::class.java to
                MessageType.LOBBY_GAME_STATE_PRIVATE_GET_REQUEST,
            GameStatePrivateGetResponse::class.java to
                MessageType.LOBBY_GAME_STATE_PRIVATE_GET_RESPONSE,
            GameStatePrivateGetErrorResponse::class.java to
                MessageType.LOBBY_GAME_STATE_PRIVATE_GET_ERROR_RESPONSE,
            MapGetRequest::class.java to MessageType.LOBBY_MAP_GET_REQUEST,
            MapGetResponse::class.java to MessageType.LOBBY_MAP_GET_RESPONSE,
            MapGetErrorResponse::class.java to MessageType.LOBBY_MAP_GET_ERROR_RESPONSE,
            PlaceReinforcementsRequest::class.java to
                MessageType.LOBBY_PLACE_REINFORCEMENTS_REQUEST,
            PlaceReinforcementsResponse::class.java to
                MessageType.LOBBY_PLACE_REINFORCEMENTS_RESPONSE,
            PlaceReinforcementsErrorResponse::class.java to
                MessageType.LOBBY_PLACE_REINFORCEMENTS_ERROR_RESPONSE,
            TradeInCardsRequest::class.java to MessageType.LOBBY_TRADE_IN_CARDS_REQUEST,
            TradeInCardsResponse::class.java to MessageType.LOBBY_TRADE_IN_CARDS_RESPONSE,
            TradeInCardsErrorResponse::class.java to
                MessageType.LOBBY_TRADE_IN_CARDS_ERROR_RESPONSE,
            StartPlayerSetRequest::class.java to
                MessageType.LOBBY_START_PLAYER_SET_REQUEST,
            StartPlayerSetResponse::class.java to
                MessageType.LOBBY_START_PLAYER_SET_RESPONSE,
            StartPlayerSetErrorResponse::class.java to
                MessageType.LOBBY_START_PLAYER_SET_ERROR_RESPONSE,
            TerritoryOwnerChangedEvent::class.java to
                MessageType.LOBBY_TERRITORY_OWNER_CHANGED_BROADCAST,
            TerritoryTroopsChangedEvent::class.java to
                MessageType.LOBBY_TERRITORY_TROOPS_CHANGED_BROADCAST,
            TurnAdvanceRequest::class.java to MessageType.LOBBY_TURN_ADVANCE_REQUEST,
            TurnAdvanceResponse::class.java to MessageType.LOBBY_TURN_ADVANCE_RESPONSE,
            TurnAdvanceErrorResponse::class.java to
                MessageType.LOBBY_TURN_ADVANCE_ERROR_RESPONSE,
            TurnStateUpdatedEvent::class.java to
                MessageType.LOBBY_TURN_STATE_UPDATED_BROADCAST,
            TurnStateGetRequest::class.java to MessageType.LOBBY_TURN_STATE_GET_REQUEST,
            TurnStateGetResponse::class.java to MessageType.LOBBY_TURN_STATE_GET_RESPONSE,
            TurnStateGetErrorResponse::class.java to
                MessageType.LOBBY_TURN_STATE_GET_ERROR_RESPONSE,
        )

    private val payloadSerializerByClass =
        mapOf<Class<out NetworkMessagePayload>, (NetworkMessagePayload) -> String>(
            ConnectionResponse::class.java to encodeWith(ConnectionResponse.serializer()),
            GlobalPlayerCountEvent::class.java to encodeWith(GlobalPlayerCountEvent.serializer()),
            ReconnectRequest::class.java to encodeWith(ReconnectRequest.serializer()),
            ReconnectResponse::class.java to encodeWith(ReconnectResponse.serializer()),
            AttackRequest::class.java to encodeWith(AttackRequest.serializer()),
            AttackResponse::class.java to encodeWith(AttackResponse.serializer()),
            AttackErrorResponse::class.java to encodeWith(AttackErrorResponse.serializer()),
            AttackResolvedBroadcastEvent::class.java to
                encodeWith(AttackResolvedBroadcastEvent.serializer()),
            PlayerEliminatedEvent::class.java to encodeWith(PlayerEliminatedEvent.serializer()),
            ConfirmAttackDoneRequest::class.java to
                encodeWith(ConfirmAttackDoneRequest.serializer()),
            ConfirmAttackDoneResponse::class.java to
                encodeWith(ConfirmAttackDoneResponse.serializer()),
            ConfirmAttackDoneErrorResponse::class.java to
                encodeWith(ConfirmAttackDoneErrorResponse.serializer()),
            ConfirmReinforcementsDoneRequest::class.java to
                encodeWith(ConfirmReinforcementsDoneRequest.serializer()),
            ConfirmReinforcementsDoneResponse::class.java to
                encodeWith(ConfirmReinforcementsDoneResponse.serializer()),
            ConfirmReinforcementsDoneErrorResponse::class.java to
                encodeWith(ConfirmReinforcementsDoneErrorResponse.serializer()),
            CreateLobbyRequest::class.java to encodeWith(CreateLobbyRequest.serializer()),
            CreateLobbyErrorResponse::class.java to
                encodeWith(CreateLobbyErrorResponse.serializer()),
            CreateLobbyResponse::class.java to encodeWith(CreateLobbyResponse.serializer()),
            JoinLobbyRequest::class.java to encodeWith(JoinLobbyRequest.serializer()),
            JoinLobbyErrorResponse::class.java to
                encodeWith(JoinLobbyErrorResponse.serializer()),
            JoinLobbyResponse::class.java to encodeWith(JoinLobbyResponse.serializer()),
            PlayerJoinedLobbyEvent::class.java to encodeWith(PlayerJoinedLobbyEvent.serializer()),
            PlayerConnectionLostEvent::class.java to
                encodeWith(PlayerConnectionLostEvent.serializer()),
            LobbyPlayerCountRequest::class.java to
                encodeWith(LobbyPlayerCountRequest.serializer()),
            LobbyPlayerCountResponse::class.java to
                encodeWith(LobbyPlayerCountResponse.serializer()),
            PlayerCountUpdateEvent::class.java to
                encodeWith<PlayerCountUpdateEvent>(PlayerCountUpdateEventSerializer),
            LobbyPlayerCountErrorResponse::class.java to
                encodeWith(LobbyPlayerCountErrorResponse.serializer()),
            FortifyMoveRequest::class.java to encodeWith(FortifyMoveRequest.serializer()),
            FortifyMoveResponse::class.java to encodeWith(FortifyMoveResponse.serializer()),
            FortifyMoveErrorResponse::class.java to
                encodeWith(FortifyMoveErrorResponse.serializer()),
            PendingReinforcementsChangedEvent::class.java to
                encodeWith(PendingReinforcementsChangedEvent.serializer()),
            ReinforcementsGrantedEvent::class.java to
                encodeWith(ReinforcementsGrantedEvent.serializer()),
            LeaveLobbyRequest::class.java to encodeWith(LeaveLobbyRequest.serializer()),
            LeaveLobbyResponse::class.java to encodeWith(LeaveLobbyResponse.serializer()),
            PlayerLeftLobbyEvent::class.java to encodeWith(PlayerLeftLobbyEvent.serializer()),
            KickPlayerRequest::class.java to encodeWith(KickPlayerRequest.serializer()),
            KickPlayerResponse::class.java to encodeWith(KickPlayerResponse.serializer()),
            KickPlayerErrorResponse::class.java to
                encodeWith(KickPlayerErrorResponse.serializer()),
            PlayerKickedLobbyEvent::class.java to encodeWith(PlayerKickedLobbyEvent.serializer()),
            StartGameRequest::class.java to encodeWith(StartGameRequest.serializer()),
            StartGameResponse::class.java to encodeWith(StartGameResponse.serializer()),
            StartGameErrorResponse::class.java to encodeWith(StartGameErrorResponse.serializer()),
            GameStartedEvent::class.java to encodeWith(GameStartedEvent.serializer()),
            PlayerHandUpdatedEvent::class.java to encodeWith(PlayerHandUpdatedEvent.serializer()),
            GameStateDeltaEvent::class.java to encodeWith(GameStateDeltaEvent.serializer()),
            PhaseBoundaryEvent::class.java to encodeWith(PhaseBoundaryEvent.serializer()),
            GameStateSnapshotBroadcast::class.java to
                encodeWith(GameStateSnapshotBroadcast.serializer()),
            GameStateCatchUpRequest::class.java to encodeWith(GameStateCatchUpRequest.serializer()),
            GameStateCatchUpResponse::class.java to
                encodeWith(GameStateCatchUpResponse.serializer()),
            GameStateCatchUpErrorResponse::class.java to
                encodeWith(GameStateCatchUpErrorResponse.serializer()),
            GameStatePrivateGetRequest::class.java to
                encodeWith(GameStatePrivateGetRequest.serializer()),
            GameStatePrivateGetResponse::class.java to
                encodeWith(GameStatePrivateGetResponse.serializer()),
            GameStatePrivateGetErrorResponse::class.java to
                encodeWith(GameStatePrivateGetErrorResponse.serializer()),
            MapGetRequest::class.java to encodeWith(MapGetRequest.serializer()),
            MapGetResponse::class.java to encodeWith(MapGetResponse.serializer()),
            MapGetErrorResponse::class.java to encodeWith(MapGetErrorResponse.serializer()),
            PlaceReinforcementsRequest::class.java to
                encodeWith(PlaceReinforcementsRequest.serializer()),
            PlaceReinforcementsResponse::class.java to
                encodeWith(PlaceReinforcementsResponse.serializer()),
            PlaceReinforcementsErrorResponse::class.java to
                encodeWith(PlaceReinforcementsErrorResponse.serializer()),
            TradeInCardsRequest::class.java to encodeWith(TradeInCardsRequest.serializer()),
            TradeInCardsResponse::class.java to encodeWith(TradeInCardsResponse.serializer()),
            TradeInCardsErrorResponse::class.java to
                encodeWith(TradeInCardsErrorResponse.serializer()),
            StartPlayerSetRequest::class.java to encodeWith(StartPlayerSetRequest.serializer()),
            StartPlayerSetResponse::class.java to encodeWith(StartPlayerSetResponse.serializer()),
            StartPlayerSetErrorResponse::class.java to
                encodeWith(StartPlayerSetErrorResponse.serializer()),
            TerritoryOwnerChangedEvent::class.java to
                encodeWith(TerritoryOwnerChangedEvent.serializer()),
            TerritoryTroopsChangedEvent::class.java to
                encodeWith(TerritoryTroopsChangedEvent.serializer()),
            TurnAdvanceRequest::class.java to encodeWith(TurnAdvanceRequest.serializer()),
            TurnAdvanceResponse::class.java to encodeWith(TurnAdvanceResponse.serializer()),
            TurnAdvanceErrorResponse::class.java to
                encodeWith(TurnAdvanceErrorResponse.serializer()),
            TurnStateUpdatedEvent::class.java to encodeWith(TurnStateUpdatedEvent.serializer()),
            TurnStateGetRequest::class.java to encodeWith(TurnStateGetRequest.serializer()),
            TurnStateGetResponse::class.java to encodeWith(TurnStateGetResponse.serializer()),
            TurnStateGetErrorResponse::class.java to
                encodeWith(TurnStateGetErrorResponse.serializer()),
        )

    private val payloadDeserializerByType =
        mapOf<MessageType, (String) -> NetworkMessagePayload>(
            MessageType.CONNECTION_RESPONSE to decodeWith(ConnectionResponse.serializer()),
            MessageType.GLOBAL_PLAYER_COUNT_BROADCAST to
                decodeWith(GlobalPlayerCountEvent.serializer()),
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
            Json.encodeToString(serializer, payload as T)
        }
    }

    private fun <T : NetworkMessagePayload> decodeWith(
        serializer: KSerializer<T>,
    ): (String) -> NetworkMessagePayload {
        return { json ->
            Json.decodeFromString(serializer, json)
        }
    }

    /**
     * Liefert den zu [payload] gehörigen [MessageType].
     */
    fun messageTypeFor(payload: NetworkMessagePayload): MessageType =
        payloadTypeByClass[payload.javaClass]
            ?: throw UnsupportedPayloadClassException(payload.javaClass.name)

    /**
     * Serialisiert [payload] als JSON-String gemäß der registrierten Payload-Klasse.
     */
    fun serializePayload(payload: NetworkMessagePayload): String {
        val serializer = payloadSerializerByClass[payload.javaClass]
        if (serializer == null) {
            throw UnsupportedPayloadClassException(payload.javaClass.name)
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
