package at.aau.pulverfass.shared.message.lobby.response

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
 * Antwort des Servers auf eine erfolgreiche Lobby-Erstellung.
 *
 * @property lobbyCode serverseitig vergebener Join-Code der neuen Lobby
 */
@Serializable(with = CreateLobbyResponseSerializer::class)
data class CreateLobbyResponse(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [CreateLobbyResponse].
 */
object CreateLobbyResponseSerializer : KSerializer<CreateLobbyResponse> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.CreateLobbyResponse") {
            element("lobbyCode", LobbyCode.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: CreateLobbyResponse,
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

    override fun deserialize(decoder: Decoder): CreateLobbyResponse {
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
        return CreateLobbyResponse(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
        )
    }

    private fun decodeLobbyCode(composite: CompositeDecoder): LobbyCode =
        composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
}
