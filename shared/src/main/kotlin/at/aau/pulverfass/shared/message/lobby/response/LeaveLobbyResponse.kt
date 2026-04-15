package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Erfolgsantwort des Servers auf eine Leave-Anfrage.
 *
 * @property lobbyCode Lobby, die der Client verlassen hat
 */
@Serializable(with = LeaveLobbyResponseSerializer::class)
data class LeaveLobbyResponse(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

@OptIn(ExperimentalSerializationApi::class)
object LeaveLobbyResponseSerializer : KSerializer<LeaveLobbyResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.message.lobby.response.LeaveLobbyResponse",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: LeaveLobbyResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor = descriptor,
            index = 0,
            serializer = LobbyCode.serializer(),
            value = value.lobbyCode,
        )
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): LeaveLobbyResponse {
        val composite = decoder.beginStructure(descriptor)
        val serialName = descriptor.serialName
        var lobbyCode: LobbyCode? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    lobbyCode =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            LobbyCode.serializer(),
                        )
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return LeaveLobbyResponse(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", serialName),
        )
    }
}
