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
 * Lobby-Scoped Broadcast des Servers nach einem erfolgreichen Join.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId Spieler, der der Lobby beigetreten ist
 * @property playerDisplayName Anzeigename des Players für die Lobby-UI
 */
@Serializable(with = PlayerJoinedLobbyEventSerializer::class)
data class PlayerJoinedLobbyEvent(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val playerDisplayName: String,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [PlayerJoinedLobbyEvent].
 */
object PlayerJoinedLobbyEventSerializer : KSerializer<PlayerJoinedLobbyEvent> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.PlayerJoinedLobbyEvent",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("playerId", PlayerId.serializer().descriptor)
            element<String>("playerDisplayName")
        }

    override fun serialize(
        encoder: Encoder,
        value: PlayerJoinedLobbyEvent,
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
            value = value.playerId,
        )
        composite.encodeStringElement(descriptor, 2, value.playerDisplayName)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): PlayerJoinedLobbyEvent {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var playerId: PlayerId? = null
        var playerDisplayName: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = decodeLobbyCode(composite)
                1 -> playerId = decodePlayerId(composite)
                2 -> playerDisplayName = composite.decodeStringElement(descriptor, 2)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return PlayerJoinedLobbyEvent(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            playerId =
                playerId
                    ?: throw MissingFieldException("playerId", descriptor.serialName),
            playerDisplayName =
                playerDisplayName
                    ?: throw MissingFieldException("playerDisplayName", descriptor.serialName),
        )
    }

    private fun decodeLobbyCode(composite: CompositeDecoder): LobbyCode =
        composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())

    private fun decodePlayerId(composite: CompositeDecoder): PlayerId =
        composite.decodeSerializableElement(descriptor, 1, PlayerId.serializer())
}
