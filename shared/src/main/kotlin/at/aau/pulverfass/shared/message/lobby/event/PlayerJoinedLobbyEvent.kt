package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.requireValidPlayerDisplayName
import at.aau.pulverfass.shared.message.codec.ManualSerializerSupport
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
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
 * @property playerDisplayName Anzeigename des Players fuer die Lobby-UI;
 * darf nicht leer sein und ist auf 32 Zeichen begrenzt
 */
@Serializable(with = PlayerJoinedLobbyEventSerializer::class)
data class PlayerJoinedLobbyEvent(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val playerDisplayName: String,
    val isHost: Boolean = false,
) : NetworkMessagePayload {
    init {
        requireValidPlayerDisplayName(playerDisplayName)
    }
}

/**
 * Technischer Serializer für [PlayerJoinedLobbyEvent].
 */
@OptIn(ExperimentalSerializationApi::class)
object PlayerJoinedLobbyEventSerializer : KSerializer<PlayerJoinedLobbyEvent> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.PlayerJoinedLobbyEvent",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("playerId", PlayerId.serializer().descriptor)
            element<String>("playerDisplayName")
            element<Boolean>("isHost", isOptional = true)
        }

    override fun serialize(
        encoder: Encoder,
        value: PlayerJoinedLobbyEvent,
    ) {
        ManualSerializerSupport.encodeStructure(encoder, descriptor) { composite ->
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
            if (value.isHost) {
                composite.encodeBooleanElement(descriptor, 3, value.isHost)
            }
        }
    }

    override fun deserialize(decoder: Decoder): PlayerJoinedLobbyEvent =
        ManualSerializerSupport.decodeStructure(decoder, descriptor) { composite ->
            var lobbyCode: LobbyCode? = null
            var playerId: PlayerId? = null
            var playerDisplayName: String? = null
            var isHost = false

            loop@ while (true) {
                when (val index = composite.decodeElementIndex(descriptor)) {
                    0 -> lobbyCode = decodeLobbyCode(composite)
                    1 -> playerId = decodePlayerId(composite)
                    2 -> playerDisplayName = composite.decodeStringElement(descriptor, 2)
                    3 -> isHost = composite.decodeBooleanElement(descriptor, 3)
                    CompositeDecoder.DECODE_DONE -> break@loop
                    else -> ManualSerializerSupport.unexpectedIndex(index)
                }
            }

            PlayerJoinedLobbyEvent(
                lobbyCode =
                    lobbyCode
                        ?: ManualSerializerSupport.missingField("lobbyCode", descriptor),
                playerId =
                    playerId
                        ?: ManualSerializerSupport.missingField("playerId", descriptor),
                playerDisplayName =
                    playerDisplayName
                        ?: ManualSerializerSupport.missingField("playerDisplayName", descriptor),
                isHost = isHost,
            )
        }

    private fun decodeLobbyCode(composite: CompositeDecoder): LobbyCode =
        composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())

    private fun decodePlayerId(composite: CompositeDecoder): PlayerId =
        composite.decodeSerializableElement(descriptor, 1, PlayerId.serializer())
}
