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
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.KickPlayerErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartGameErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorResponse
import at.aau.pulverfass.shared.message.protocol.MessageType
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadClassException
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadTypeException
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
            GameStateSnapshotBroadcast::class.java to MessageType.LOBBY_GAME_STATE_SNAPSHOT_BROADCAST,
            GameStateCatchUpRequest::class.java to MessageType.LOBBY_GAME_STATE_CATCH_UP_REQUEST,
            GameStateCatchUpResponse::class.java to MessageType.LOBBY_GAME_STATE_CATCH_UP_RESPONSE,
            GameStateCatchUpErrorResponse::class.java to MessageType.LOBBY_GAME_STATE_CATCH_UP_ERROR_RESPONSE,
            GameStatePrivateGetRequest::class.java to MessageType.LOBBY_GAME_STATE_PRIVATE_GET_REQUEST,
            GameStatePrivateGetResponse::class.java to MessageType.LOBBY_GAME_STATE_PRIVATE_GET_RESPONSE,
            GameStatePrivateGetErrorResponse::class.java to MessageType.LOBBY_GAME_STATE_PRIVATE_GET_ERROR_RESPONSE,
            MapGetRequest::class.java to MessageType.LOBBY_MAP_GET_REQUEST,
            MapGetResponse::class.java to MessageType.LOBBY_MAP_GET_RESPONSE,
            MapGetErrorResponse::class.java to MessageType.LOBBY_MAP_GET_ERROR_RESPONSE,
            StartPlayerSetRequest::class.java to MessageType.LOBBY_START_PLAYER_SET_REQUEST,
            StartPlayerSetResponse::class.java to MessageType.LOBBY_START_PLAYER_SET_RESPONSE,
            StartPlayerSetErrorResponse::class.java to MessageType.LOBBY_START_PLAYER_SET_ERROR_RESPONSE,
            TerritoryOwnerChangedEvent::class.java to MessageType.LOBBY_TERRITORY_OWNER_CHANGED_BROADCAST,
            TerritoryTroopsChangedEvent::class.java to MessageType.LOBBY_TERRITORY_TROOPS_CHANGED_BROADCAST,
            TurnAdvanceRequest::class.java to MessageType.LOBBY_TURN_ADVANCE_REQUEST,
            TurnAdvanceResponse::class.java to MessageType.LOBBY_TURN_ADVANCE_RESPONSE,
            TurnAdvanceErrorResponse::class.java to MessageType.LOBBY_TURN_ADVANCE_ERROR_RESPONSE,
            TurnStateUpdatedEvent::class.java to MessageType.LOBBY_TURN_STATE_UPDATED_BROADCAST,
            TurnStateGetRequest::class.java to MessageType.LOBBY_TURN_STATE_GET_REQUEST,
            TurnStateGetResponse::class.java to MessageType.LOBBY_TURN_STATE_GET_RESPONSE,
            TurnStateGetErrorResponse::class.java to MessageType.LOBBY_TURN_STATE_GET_ERROR_RESPONSE,
        )

    private val payloadSerializerByClass =
        mapOf<Class<out NetworkMessagePayload>, (NetworkMessagePayload) -> String>(
            CreateLobbyRequest::class.java to { payload ->
                Json.encodeToString(CreateLobbyRequest.serializer(), payload as CreateLobbyRequest)
            },
            CreateLobbyErrorResponse::class.java to { payload ->
                Json.encodeToString(
                    CreateLobbyErrorResponse.serializer(),
                    payload as CreateLobbyErrorResponse,
                )
            },
            CreateLobbyResponse::class.java to { payload ->
                Json.encodeToString(
                    CreateLobbyResponse.serializer(),
                    payload as CreateLobbyResponse,
                )
            },
            JoinLobbyRequest::class.java to { payload ->
                Json.encodeToString(JoinLobbyRequest.serializer(), payload as JoinLobbyRequest)
            },
            JoinLobbyErrorResponse::class.java to { payload ->
                Json.encodeToString(
                    JoinLobbyErrorResponse.serializer(),
                    payload as JoinLobbyErrorResponse,
                )
            },
            JoinLobbyResponse::class.java to { payload ->
                Json.encodeToString(JoinLobbyResponse.serializer(), payload as JoinLobbyResponse)
            },
            PlayerJoinedLobbyEvent::class.java to { payload ->
                Json.encodeToString(
                    PlayerJoinedLobbyEvent.serializer(),
                    payload as PlayerJoinedLobbyEvent,
                )
            },
            LeaveLobbyRequest::class.java to { payload ->
                Json.encodeToString(LeaveLobbyRequest.serializer(), payload as LeaveLobbyRequest)
            },
            LeaveLobbyResponse::class.java to { payload ->
                Json.encodeToString(LeaveLobbyResponse.serializer(), payload as LeaveLobbyResponse)
            },
            PlayerLeftLobbyEvent::class.java to { payload ->
                Json.encodeToString(
                    PlayerLeftLobbyEvent.serializer(),
                    payload as PlayerLeftLobbyEvent,
                )
            },
            KickPlayerRequest::class.java to { payload ->
                Json.encodeToString(KickPlayerRequest.serializer(), payload as KickPlayerRequest)
            },
            KickPlayerResponse::class.java to { payload ->
                Json.encodeToString(KickPlayerResponse.serializer(), payload as KickPlayerResponse)
            },
            KickPlayerErrorResponse::class.java to { payload ->
                Json.encodeToString(
                    KickPlayerErrorResponse.serializer(),
                    payload as KickPlayerErrorResponse,
                )
            },
            PlayerKickedLobbyEvent::class.java to { payload ->
                Json.encodeToString(
                    PlayerKickedLobbyEvent.serializer(),
                    payload as PlayerKickedLobbyEvent,
                )
            },
            StartGameRequest::class.java to { payload ->
                Json.encodeToString(StartGameRequest.serializer(), payload as StartGameRequest)
            },
            StartGameResponse::class.java to { payload ->
                Json.encodeToString(StartGameResponse.serializer(), payload as StartGameResponse)
            },
            StartGameErrorResponse::class.java to { payload ->
                Json.encodeToString(
                    StartGameErrorResponse.serializer(),
                    payload as StartGameErrorResponse,
                )
            },
            GameStartedEvent::class.java to { payload ->
                Json.encodeToString(GameStartedEvent.serializer(), payload as GameStartedEvent)
            },
            GameStateDeltaEvent::class.java to { payload ->
                Json.encodeToString(GameStateDeltaEvent.serializer(), payload as GameStateDeltaEvent)
            },
            PhaseBoundaryEvent::class.java to { payload ->
                Json.encodeToString(PhaseBoundaryEvent.serializer(), payload as PhaseBoundaryEvent)
            },
            GameStateSnapshotBroadcast::class.java to { payload ->
                Json.encodeToString(
                    GameStateSnapshotBroadcast.serializer(),
                    payload as GameStateSnapshotBroadcast,
                )
            },
            GameStateCatchUpRequest::class.java to { payload ->
                Json.encodeToString(
                    GameStateCatchUpRequest.serializer(),
                    payload as GameStateCatchUpRequest,
                )
            },
            GameStateCatchUpResponse::class.java to { payload ->
                Json.encodeToString(
                    GameStateCatchUpResponse.serializer(),
                    payload as GameStateCatchUpResponse,
                )
            },
            GameStateCatchUpErrorResponse::class.java to { payload ->
                Json.encodeToString(
                    GameStateCatchUpErrorResponse.serializer(),
                    payload as GameStateCatchUpErrorResponse,
                )
            },
            GameStatePrivateGetRequest::class.java to { payload ->
                Json.encodeToString(
                    GameStatePrivateGetRequest.serializer(),
                    payload as GameStatePrivateGetRequest,
                )
            },
            GameStatePrivateGetResponse::class.java to { payload ->
                Json.encodeToString(
                    GameStatePrivateGetResponse.serializer(),
                    payload as GameStatePrivateGetResponse,
                )
            },
            GameStatePrivateGetErrorResponse::class.java to { payload ->
                Json.encodeToString(
                    GameStatePrivateGetErrorResponse.serializer(),
                    payload as GameStatePrivateGetErrorResponse,
                )
            },
            MapGetRequest::class.java to { payload ->
                Json.encodeToString(MapGetRequest.serializer(), payload as MapGetRequest)
            },
            MapGetResponse::class.java to { payload ->
                Json.encodeToString(MapGetResponse.serializer(), payload as MapGetResponse)
            },
            MapGetErrorResponse::class.java to { payload ->
                Json.encodeToString(MapGetErrorResponse.serializer(), payload as MapGetErrorResponse)
            },
            StartPlayerSetRequest::class.java to { payload ->
                Json.encodeToString(StartPlayerSetRequest.serializer(), payload as StartPlayerSetRequest)
            },
            StartPlayerSetResponse::class.java to { payload ->
                Json.encodeToString(StartPlayerSetResponse.serializer(), payload as StartPlayerSetResponse)
            },
            StartPlayerSetErrorResponse::class.java to { payload ->
                Json.encodeToString(
                    StartPlayerSetErrorResponse.serializer(),
                    payload as StartPlayerSetErrorResponse,
                )
            },
            TerritoryOwnerChangedEvent::class.java to { payload ->
                Json.encodeToString(
                    TerritoryOwnerChangedEvent.serializer(),
                    payload as TerritoryOwnerChangedEvent,
                )
            },
            TerritoryTroopsChangedEvent::class.java to { payload ->
                Json.encodeToString(
                    TerritoryTroopsChangedEvent.serializer(),
                    payload as TerritoryTroopsChangedEvent,
                )
            },
            TurnAdvanceRequest::class.java to { payload ->
                Json.encodeToString(TurnAdvanceRequest.serializer(), payload as TurnAdvanceRequest)
            },
            TurnAdvanceResponse::class.java to { payload ->
                Json.encodeToString(TurnAdvanceResponse.serializer(), payload as TurnAdvanceResponse)
            },
            TurnAdvanceErrorResponse::class.java to { payload ->
                Json.encodeToString(
                    TurnAdvanceErrorResponse.serializer(),
                    payload as TurnAdvanceErrorResponse,
                )
            },
            TurnStateUpdatedEvent::class.java to { payload ->
                Json.encodeToString(
                    TurnStateUpdatedEvent.serializer(),
                    payload as TurnStateUpdatedEvent,
                )
            },
            TurnStateGetRequest::class.java to { payload ->
                Json.encodeToString(TurnStateGetRequest.serializer(), payload as TurnStateGetRequest)
            },
            TurnStateGetResponse::class.java to { payload ->
                Json.encodeToString(TurnStateGetResponse.serializer(), payload as TurnStateGetResponse)
            },
            TurnStateGetErrorResponse::class.java to { payload ->
                Json.encodeToString(
                    TurnStateGetErrorResponse.serializer(),
                    payload as TurnStateGetErrorResponse,
                )
            },
        )

    private val payloadDeserializerByType =
        mapOf<MessageType, (String) -> NetworkMessagePayload>(
            MessageType.LOBBY_CREATE_REQUEST to { json ->
                Json.decodeFromString(CreateLobbyRequest.serializer(), json)
            },
            MessageType.LOBBY_CREATE_ERROR_RESPONSE to { json ->
                Json.decodeFromString(CreateLobbyErrorResponse.serializer(), json)
            },
            MessageType.LOBBY_CREATE_RESPONSE to { json ->
                Json.decodeFromString(CreateLobbyResponse.serializer(), json)
            },
            MessageType.LOBBY_JOIN_REQUEST to { json ->
                Json.decodeFromString(JoinLobbyRequest.serializer(), json)
            },
            MessageType.LOBBY_JOIN_ERROR_RESPONSE to { json ->
                Json.decodeFromString(JoinLobbyErrorResponse.serializer(), json)
            },
            MessageType.LOBBY_JOIN_RESPONSE to { json ->
                Json.decodeFromString(JoinLobbyResponse.serializer(), json)
            },
            MessageType.LOBBY_PLAYER_JOINED_BROADCAST to { json ->
                Json.decodeFromString(PlayerJoinedLobbyEvent.serializer(), json)
            },
            MessageType.LOBBY_LEAVE_REQUEST to { json ->
                Json.decodeFromString(LeaveLobbyRequest.serializer(), json)
            },
            MessageType.LOBBY_LEAVE_RESPONSE to { json ->
                Json.decodeFromString(LeaveLobbyResponse.serializer(), json)
            },
            MessageType.LOBBY_PLAYER_LEFT_BROADCAST to { json ->
                Json.decodeFromString(PlayerLeftLobbyEvent.serializer(), json)
            },
            MessageType.LOBBY_KICK_REQUEST to { json ->
                Json.decodeFromString(KickPlayerRequest.serializer(), json)
            },
            MessageType.LOBBY_KICK_RESPONSE to { json ->
                Json.decodeFromString(KickPlayerResponse.serializer(), json)
            },
            MessageType.LOBBY_KICK_ERROR_RESPONSE to { json ->
                Json.decodeFromString(KickPlayerErrorResponse.serializer(), json)
            },
            MessageType.LOBBY_PLAYER_KICKED_BROADCAST to { json ->
                Json.decodeFromString(PlayerKickedLobbyEvent.serializer(), json)
            },
            MessageType.LOBBY_START_REQUEST to { json ->
                Json.decodeFromString(StartGameRequest.serializer(), json)
            },
            MessageType.LOBBY_START_RESPONSE to { json ->
                Json.decodeFromString(StartGameResponse.serializer(), json)
            },
            MessageType.LOBBY_START_ERROR_RESPONSE to { json ->
                Json.decodeFromString(StartGameErrorResponse.serializer(), json)
            },
            MessageType.LOBBY_GAME_STARTED_BROADCAST to { json ->
                Json.decodeFromString(GameStartedEvent.serializer(), json)
            },
            MessageType.LOBBY_GAME_STATE_DELTA_BROADCAST to { json ->
                Json.decodeFromString(GameStateDeltaEvent.serializer(), json)
            },
            MessageType.LOBBY_PHASE_BOUNDARY_BROADCAST to { json ->
                Json.decodeFromString(PhaseBoundaryEvent.serializer(), json)
            },
            MessageType.LOBBY_GAME_STATE_SNAPSHOT_BROADCAST to { json ->
                Json.decodeFromString(GameStateSnapshotBroadcast.serializer(), json)
            },
            MessageType.LOBBY_GAME_STATE_CATCH_UP_REQUEST to { json ->
                Json.decodeFromString(GameStateCatchUpRequest.serializer(), json)
            },
            MessageType.LOBBY_GAME_STATE_CATCH_UP_RESPONSE to { json ->
                Json.decodeFromString(GameStateCatchUpResponse.serializer(), json)
            },
            MessageType.LOBBY_GAME_STATE_CATCH_UP_ERROR_RESPONSE to { json ->
                Json.decodeFromString(GameStateCatchUpErrorResponse.serializer(), json)
            },
            MessageType.LOBBY_GAME_STATE_PRIVATE_GET_REQUEST to { json ->
                Json.decodeFromString(GameStatePrivateGetRequest.serializer(), json)
            },
            MessageType.LOBBY_GAME_STATE_PRIVATE_GET_RESPONSE to { json ->
                Json.decodeFromString(GameStatePrivateGetResponse.serializer(), json)
            },
            MessageType.LOBBY_GAME_STATE_PRIVATE_GET_ERROR_RESPONSE to { json ->
                Json.decodeFromString(GameStatePrivateGetErrorResponse.serializer(), json)
            },
            MessageType.LOBBY_MAP_GET_REQUEST to { json ->
                Json.decodeFromString(MapGetRequest.serializer(), json)
            },
            MessageType.LOBBY_MAP_GET_RESPONSE to { json ->
                Json.decodeFromString(MapGetResponse.serializer(), json)
            },
            MessageType.LOBBY_MAP_GET_ERROR_RESPONSE to { json ->
                Json.decodeFromString(MapGetErrorResponse.serializer(), json)
            },
            MessageType.LOBBY_START_PLAYER_SET_REQUEST to { json ->
                Json.decodeFromString(StartPlayerSetRequest.serializer(), json)
            },
            MessageType.LOBBY_START_PLAYER_SET_RESPONSE to { json ->
                Json.decodeFromString(StartPlayerSetResponse.serializer(), json)
            },
            MessageType.LOBBY_START_PLAYER_SET_ERROR_RESPONSE to { json ->
                Json.decodeFromString(StartPlayerSetErrorResponse.serializer(), json)
            },
            MessageType.LOBBY_TERRITORY_OWNER_CHANGED_BROADCAST to { json ->
                Json.decodeFromString(TerritoryOwnerChangedEvent.serializer(), json)
            },
            MessageType.LOBBY_TERRITORY_TROOPS_CHANGED_BROADCAST to { json ->
                Json.decodeFromString(TerritoryTroopsChangedEvent.serializer(), json)
            },
            MessageType.LOBBY_TURN_ADVANCE_REQUEST to { json ->
                Json.decodeFromString(TurnAdvanceRequest.serializer(), json)
            },
            MessageType.LOBBY_TURN_ADVANCE_RESPONSE to { json ->
                Json.decodeFromString(TurnAdvanceResponse.serializer(), json)
            },
            MessageType.LOBBY_TURN_ADVANCE_ERROR_RESPONSE to { json ->
                Json.decodeFromString(TurnAdvanceErrorResponse.serializer(), json)
            },
            MessageType.LOBBY_TURN_STATE_UPDATED_BROADCAST to { json ->
                Json.decodeFromString(TurnStateUpdatedEvent.serializer(), json)
            },
            MessageType.LOBBY_TURN_STATE_GET_REQUEST to { json ->
                Json.decodeFromString(TurnStateGetRequest.serializer(), json)
            },
            MessageType.LOBBY_TURN_STATE_GET_RESPONSE to { json ->
                Json.decodeFromString(TurnStateGetResponse.serializer(), json)
            },
            MessageType.LOBBY_TURN_STATE_GET_ERROR_RESPONSE to { json ->
                Json.decodeFromString(TurnStateGetErrorResponse.serializer(), json)
            },
        )

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
