package at.aau.pulverfass.shared.message.codec

import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerKickedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.KickPlayerRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequest
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.request.StartPlayerSetRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStateCatchUpResponse
import at.aau.pulverfass.shared.message.lobby.response.GameStatePrivateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.KickPlayerResponse
import at.aau.pulverfass.shared.message.lobby.response.LeaveLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import at.aau.pulverfass.shared.message.lobby.response.StartGameResponse
import at.aau.pulverfass.shared.message.lobby.response.StartPlayerSetResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.KickPlayerErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartGameErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorResponse
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
            CreateLobbyRequest::class.java to MessageType.LOBBY_CREATE_REQUEST,
            CreateLobbyErrorResponse::class.java to MessageType.LOBBY_CREATE_ERROR_RESPONSE,
            CreateLobbyResponse::class.java to MessageType.LOBBY_CREATE_RESPONSE,
            JoinLobbyRequest::class.java to MessageType.LOBBY_JOIN_REQUEST,
            JoinLobbyErrorResponse::class.java to MessageType.LOBBY_JOIN_ERROR_RESPONSE,
            JoinLobbyResponse::class.java to MessageType.LOBBY_JOIN_RESPONSE,
            PlayerJoinedLobbyEvent::class.java to MessageType.LOBBY_PLAYER_JOINED_BROADCAST,
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
            CreateLobbyRequest::class.java to encodeWith(CreateLobbyRequest.serializer()),
            CreateLobbyErrorResponse::class.java to
                encodeWith(CreateLobbyErrorResponse.serializer()),
            CreateLobbyResponse::class.java to encodeWith(CreateLobbyResponse.serializer()),
            JoinLobbyRequest::class.java to encodeWith(JoinLobbyRequest.serializer()),
            JoinLobbyErrorResponse::class.java to
                encodeWith(JoinLobbyErrorResponse.serializer()),
            JoinLobbyResponse::class.java to encodeWith(JoinLobbyResponse.serializer()),
            PlayerJoinedLobbyEvent::class.java to encodeWith(PlayerJoinedLobbyEvent.serializer()),
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
