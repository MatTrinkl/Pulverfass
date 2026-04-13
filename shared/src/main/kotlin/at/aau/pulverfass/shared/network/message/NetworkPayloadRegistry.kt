package at.aau.pulverfass.shared.network.message

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
            LoginRequest::class.java to MessageType.LOGIN_REQUEST,
        )

    private val payloadSerializerByClass =
        mapOf<Class<out NetworkMessagePayload>, (NetworkMessagePayload) -> String>(
            LoginRequest::class.java to { payload ->
                Json.encodeToString(LoginRequest.serializer(), payload as LoginRequest)
            },
        )

    private val payloadDeserializerByType =
        mapOf<MessageType, (String) -> NetworkMessagePayload>(
            MessageType.LOGIN_REQUEST to { json ->
                Json.decodeFromString(LoginRequest.serializer(), json)
            },
        )

    fun messageTypeFor(payload: NetworkMessagePayload): MessageType =
        payloadTypeByClass[payload.javaClass]
            ?: throw UnsupportedPayloadClassException(payload.javaClass.name)

    fun serializePayload(payload: NetworkMessagePayload): String =
        payloadSerializerByClass[payload.javaClass]?.invoke(payload)
            ?: throw UnsupportedPayloadClassException(payload.javaClass.name)

    fun deserializePayload(
        type: MessageType,
        json: String,
    ): NetworkMessagePayload =
        payloadDeserializerByType[type]?.invoke(json)
            ?: throw UnsupportedPayloadTypeException(type)
}
