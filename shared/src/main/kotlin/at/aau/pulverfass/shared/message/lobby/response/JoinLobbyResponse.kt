package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Erfolgsantwort des Servers auf eine Join-Anfrage.
 *
 * @property lobbyCode Lobby, der der Client beigetreten ist
 */
@Serializable(with = JoinLobbyResponseSerializer::class)
data class JoinLobbyResponse(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

object JoinLobbyResponseSerializer : KSerializer<JoinLobbyResponse> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.JoinLobbyResponse") {
            element("lobbyCode", LobbyCode.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: JoinLobbyResponse,
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

    override fun deserialize(decoder: Decoder): JoinLobbyResponse {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = decodeLobbyCode(composite)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return JoinLobbyResponse(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
        )
    }

    private fun decodeLobbyCode(composite: CompositeDecoder): LobbyCode =
        composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
}
