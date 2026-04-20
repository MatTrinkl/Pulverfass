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
 * Anfrage eines Clients, den aktuellen autoritativen Turn-State einer Lobby
 * als Snapshot vom Server abzurufen.
 *
 * @property lobbyCode betroffene Lobby
 */
@Serializable(with = TurnStateGetRequestSerializer::class)
data class TurnStateGetRequest(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [TurnStateGetRequest].
 */
object TurnStateGetRequestSerializer : KSerializer<TurnStateGetRequest> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.TurnStateGetRequest") {
            element("lobbyCode", LobbyCode.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: TurnStateGetRequest,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): TurnStateGetRequest {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return TurnStateGetRequest(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
        )
    }
}
