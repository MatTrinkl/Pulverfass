package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.lobby.requireValidPlayerDisplayName
import at.aau.pulverfass.shared.message.codec.ManualSerializerSupport
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
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
 * @property playerDisplayName Anzeigename des Players fuer die Lobby-UI;
 * darf nicht leer sein und ist auf 32 Zeichen begrenzt
 */
@Serializable(with = JoinLobbyRequestSerializer::class)
data class JoinLobbyRequest(
    val lobbyCode: LobbyCode,
    val playerDisplayName: String,
) : NetworkMessagePayload {
    init {
        requireValidPlayerDisplayName(playerDisplayName)
    }
}

/**
 * Technischer Serializer für [JoinLobbyRequest].
 */
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
        ManualSerializerSupport.encodeStructure(encoder, descriptor) { composite ->
            composite.encodeSerializableElement(
                descriptor = descriptor,
                index = 0,
                serializer = LobbyCode.serializer(),
                value = value.lobbyCode,
            )
            composite.encodeStringElement(descriptor, 1, value.playerDisplayName)
        }
    }

    override fun deserialize(decoder: Decoder): JoinLobbyRequest =
        ManualSerializerSupport.decodeStructure(decoder, descriptor) { composite ->
            var lobbyCode: LobbyCode? = null
            var playerDisplayName: String? = null

            loop@ while (true) {
                when (val index = composite.decodeElementIndex(descriptor)) {
                    0 -> lobbyCode = decodeLobbyCode(composite)
                    1 -> playerDisplayName = composite.decodeStringElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break@loop
                    else -> ManualSerializerSupport.unexpectedIndex(index)
                }
            }

            JoinLobbyRequest(
                lobbyCode =
                    lobbyCode
                        ?: ManualSerializerSupport.missingField("lobbyCode", descriptor),
                playerDisplayName =
                    playerDisplayName
                        ?: ManualSerializerSupport.missingField("playerDisplayName", descriptor),
            )
        }

    private fun decodeLobbyCode(composite: CompositeDecoder): LobbyCode =
        composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
}
