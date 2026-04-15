package at.aau.pulverfass.shared.message.lobby.request

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
 * Anfrage eines Clients, einer bestehenden Lobby beizutreten.
 *
 * **Konvention:** Siehe docs/NETWORK_MESSAGES.md für CustomSerializer-Pattern
 *
 * @property lobbyCode Ziel-Lobby der Join-Anfrage
 * @property playerDisplayName Anzeigename des Players für die Lobby-UI
 */
@Serializable(with = JoinLobbyRequestSerializer::class)
data class JoinLobbyRequest(
    val lobbyCode: LobbyCode,
    val playerDisplayName: String,
) : NetworkMessagePayload

object JoinLobbyRequestSerializer : KSerializer<JoinLobbyRequest> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.JoinLobbyRequest") {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element<String>("playerDisplayName")
        }

    override fun serialize(
        encoder: Encoder,
        value: JoinLobbyRequest,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor = descriptor,
            index = 0,
            serializer = LobbyCode.serializer(),
            value = value.lobbyCode,
        )
        composite.encodeStringElement(descriptor, 1, value.playerDisplayName)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): JoinLobbyRequest {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var playerDisplayName: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = decodeLobbyCode(composite)
                1 -> playerDisplayName = composite.decodeStringElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return JoinLobbyRequest(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            playerDisplayName =
                playerDisplayName
                    ?: throw MissingFieldException("playerDisplayName", descriptor.serialName),
        )
    }

    private fun decodeLobbyCode(composite: CompositeDecoder): LobbyCode =
        composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
}
