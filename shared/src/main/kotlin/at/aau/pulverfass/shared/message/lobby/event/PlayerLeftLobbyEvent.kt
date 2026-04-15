package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
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
 * Lobby-Scoped Broadcast des Servers nach einem erfolgreichen Leave.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId Spieler, der die Lobby verlassen hat
 */
@Serializable(with = PlayerLeftLobbyEventSerializer::class)
data class PlayerLeftLobbyEvent(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [PlayerLeftLobbyEvent].
 */
@OptIn(ExperimentalSerializationApi::class)
object PlayerLeftLobbyEventSerializer : KSerializer<PlayerLeftLobbyEvent> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("playerId", PlayerId.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: PlayerLeftLobbyEvent,
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
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): PlayerLeftLobbyEvent {
        val composite = decoder.beginStructure(descriptor)
        val serialName = descriptor.serialName
        var lobbyCode: LobbyCode? = null
        var playerId: PlayerId? = null

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
                    playerId =
                        composite.decodeSerializableElement(
                            descriptor,
                            1,
                            PlayerId.serializer(),
                        )
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return PlayerLeftLobbyEvent(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", serialName),
            playerId = playerId ?: throw MissingFieldException("playerId", serialName),
        )
    }
}
