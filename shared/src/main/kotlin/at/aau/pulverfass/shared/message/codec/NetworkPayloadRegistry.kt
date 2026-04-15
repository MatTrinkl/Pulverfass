package at.aau.pulverfass.shared.message.codec

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
        )

    fun messageTypeFor(payload: NetworkMessagePayload): MessageType =
        payloadTypeByClass[payload.javaClass]
            ?: throw UnsupportedPayloadClassException(payload.javaClass.name)

    fun serializePayload(payload: NetworkMessagePayload): String {
        val serializer = payloadSerializerByClass[payload.javaClass]
        if (serializer == null) {
            throw UnsupportedPayloadClassException(payload.javaClass.name)
        }

        return serializer(payload)
    }

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
