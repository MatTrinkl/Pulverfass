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
 * Erfolgsantwort des Servers mit der aktuellen Anzahl an Spielern in einer Lobby.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerCount aktuell autoritativ ermittelte Spieleranzahl
 */
@Serializable(with = LobbyPlayerCountResponseSerializer::class)
data class LobbyPlayerCountResponse(
    val lobbyCode: LobbyCode,
    val playerCount: Int,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [LobbyPlayerCountResponse].
 */
object LobbyPlayerCountResponseSerializer : KSerializer<LobbyPlayerCountResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.LobbyPlayerCountResponse",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element<Int>("playerCount")
        }

    override fun serialize(
        encoder: Encoder,
        value: LobbyPlayerCountResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeIntElement(descriptor, 1, value.playerCount)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): LobbyPlayerCountResponse {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var playerCount: Int? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    lobbyCode =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            LobbyCode.serializer(),
                        )
                1 -> playerCount = composite.decodeIntElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return LobbyPlayerCountResponse(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            playerCount =
                playerCount
                    ?: throw MissingFieldException("playerCount", descriptor.serialName),
        )
    }
}
