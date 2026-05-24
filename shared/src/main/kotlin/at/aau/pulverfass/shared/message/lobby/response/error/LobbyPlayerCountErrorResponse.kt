package at.aau.pulverfass.shared.message.lobby.response.error

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Typisierte Fehlercodes für fehlgeschlagene PlayerCount-Anfragen.
 */
@Serializable
enum class LobbyPlayerCountErrorCode {
    LOBBY_NOT_FOUND,
}

/**
 * Fehlantwort des Servers auf eine nicht erfolgreiche PlayerCount-Anfrage.
 *
 * @property lobbyCode betroffene Lobby
 * @property code fachlicher Fehlercode
 * @property reason lesbare Fehlerbeschreibung
 */
@Serializable(with = LobbyPlayerCountErrorResponseSerializer::class)
data class LobbyPlayerCountErrorResponse(
    val lobbyCode: LobbyCode,
    val code: LobbyPlayerCountErrorCode,
    val reason: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [LobbyPlayerCountErrorResponse].
 */
object LobbyPlayerCountErrorResponseSerializer : KSerializer<LobbyPlayerCountErrorResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.LobbyPlayerCountErrorResponse",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("code", LobbyPlayerCountErrorCode.serializer().descriptor)
            element<String>("reason")
        }

    override fun serialize(
        encoder: Encoder,
        value: LobbyPlayerCountErrorResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(
            descriptor,
            1,
            LobbyPlayerCountErrorCode.serializer(),
            value.code,
        )
        composite.encodeStringElement(descriptor, 2, value.reason)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): LobbyPlayerCountErrorResponse {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var code: LobbyPlayerCountErrorCode? = null
        var reason: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    lobbyCode =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            LobbyCode.serializer(),
                        )
                1 ->
                    code =
                        composite.decodeSerializableElement(
                            descriptor,
                            1,
                            LobbyPlayerCountErrorCode.serializer(),
                        )
                2 -> reason = composite.decodeStringElement(descriptor, 2)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return LobbyPlayerCountErrorResponse(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            code = code ?: throw MissingFieldException("code", descriptor.serialName),
            reason = reason ?: throw MissingFieldException("reason", descriptor.serialName),
        )
    }
}
