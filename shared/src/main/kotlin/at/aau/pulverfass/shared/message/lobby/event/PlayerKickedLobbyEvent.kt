package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
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
 * Lobby-Scoped Broadcast des Servers nach dem erfolgreichen Kick eines Spielers.
 *
 * @property lobbyCode betroffene Lobby
 * @property targetPlayerId Spieler, der geworfen wurde
 * @property requesterPlayerId Spieler, der gekickt hat (Owner)
 */
@Serializable(with = PlayerKickedLobbyEventSerializer::class)
data class PlayerKickedLobbyEvent(
    val lobbyCode: LobbyCode,
    val targetPlayerId: PlayerId,
    val requesterPlayerId: PlayerId,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [PlayerKickedLobbyEvent].
 */
object PlayerKickedLobbyEventSerializer : KSerializer<PlayerKickedLobbyEvent> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.PlayerKickedLobbyEvent",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("targetPlayerId", PlayerId.serializer().descriptor)
            element("requesterPlayerId", PlayerId.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: PlayerKickedLobbyEvent,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor = descriptor,
            index = 0,
            serializer = LobbyCode.serializer(),
            value = value.lobbyCode,
        )
        composite.encodeSerializableElement(
            descriptor = descriptor,
            index = 1,
            serializer = PlayerId.serializer(),
            value = value.targetPlayerId,
        )
        composite.encodeSerializableElement(
            descriptor = descriptor,
            index = 2,
            serializer = PlayerId.serializer(),
            value = value.requesterPlayerId,
        )
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): PlayerKickedLobbyEvent {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var targetPlayerId: PlayerId? = null
        var requesterPlayerId: PlayerId? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = decodeLobbyCode(composite)
                1 -> targetPlayerId = decodeTargetPlayerId(composite)
                2 -> requesterPlayerId = decodeRequesterPlayerId(composite)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return PlayerKickedLobbyEvent(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            targetPlayerId =
                targetPlayerId
                    ?: throw MissingFieldException("targetPlayerId", descriptor.serialName),
            requesterPlayerId =
                requesterPlayerId
                    ?: throw MissingFieldException("requesterPlayerId", descriptor.serialName),
        )
    }

    private fun decodeLobbyCode(composite: CompositeDecoder): LobbyCode =
        composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())

    private fun decodeTargetPlayerId(composite: CompositeDecoder): PlayerId =
        composite.decodeSerializableElement(descriptor, 1, PlayerId.serializer())

    private fun decodeRequesterPlayerId(composite: CompositeDecoder): PlayerId =
        composite.decodeSerializableElement(descriptor, 2, PlayerId.serializer())
}
