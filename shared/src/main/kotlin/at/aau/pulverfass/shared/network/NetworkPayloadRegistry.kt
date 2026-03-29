package at.aau.pulverfass.shared.network

import at.aau.pulverfass.shared.networkmessage.LoginRequest
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

    private val payloadDeserializerByType =
        mapOf<MessageType, (String) -> NetworkMessagePayload>(
            MessageType.LOGIN_REQUEST to { json ->
                Json.decodeFromString(LoginRequest.serializer(), json)
            },
        )

    fun messageTypeFor(payload: NetworkMessagePayload): MessageType =
        payloadTypeByClass[payload.javaClass]
            ?: throw UnsupportedPayloadClassException(payload.javaClass.name)

    fun deserializePayload(
        type: MessageType,
        json: String,
    ): NetworkMessagePayload =
        payloadDeserializerByType[type]?.invoke(json)
            ?: throw UnsupportedPayloadTypeException(type)
}
