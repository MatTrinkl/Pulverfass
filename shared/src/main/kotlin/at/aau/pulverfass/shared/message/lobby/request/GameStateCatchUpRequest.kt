package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
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
 * Diagnosegrund fuer eine Catch-up-Anfrage nach einem Full Snapshot.
 */
@Serializable
enum class GameStateCatchUpReason {
    MISSING_DELTA,
    OUT_OF_ORDER,
    AFTER_RECONNECT,
}

/**
 * Anfrage eines Clients nach einem vollständigen öffentlichen GameState-Snapshot,
 * wenn lokale Deltas nicht mehr konsistent angewendet werden konnten.
 *
 * @property lobbyCode betroffene Lobby
 * @property clientStateVersion letzte lokal bekannte State-Version des Clients
 * @property reason optionale Diagnoseursache fuer Logging und Analyse
 */
@Serializable(with = GameStateCatchUpRequestSerializer::class)
data class GameStateCatchUpRequest(
    val lobbyCode: LobbyCode,
    val clientStateVersion: Long,
    val reason: GameStateCatchUpReason? = null,
) : NetworkMessagePayload

/**
 * Technischer Serializer fuer [GameStateCatchUpRequest].
 */
object GameStateCatchUpRequestSerializer : KSerializer<GameStateCatchUpRequest> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.GameStateCatchUpRequest",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element<Long>("clientStateVersion")
            element("reason", GameStateCatchUpReason.serializer().descriptor, isOptional = true)
        }

    override fun serialize(
        encoder: Encoder,
        value: GameStateCatchUpRequest,
    ) {
        ManualSerializerSupport.encodeStructure(encoder, descriptor) { composite ->
            composite.encodeSerializableElement(
                descriptor,
                0,
                LobbyCode.serializer(),
                value.lobbyCode,
            )
            composite.encodeLongElement(descriptor, 1, value.clientStateVersion)
            if (value.reason != null) {
                composite.encodeSerializableElement(
                    descriptor,
                    2,
                    GameStateCatchUpReason.serializer(),
                    value.reason,
                )
            }
        }
    }

    override fun deserialize(decoder: Decoder): GameStateCatchUpRequest =
        ManualSerializerSupport.decodeStructure(decoder, descriptor) { composite ->
            var lobbyCode: LobbyCode? = null
            var clientStateVersion: Long? = null
            var reason: GameStateCatchUpReason? = null

            loop@ while (true) {
                when (val index = composite.decodeElementIndex(descriptor)) {
                    0 ->
                        lobbyCode =
                            composite.decodeSerializableElement(
                                descriptor,
                                0,
                                LobbyCode.serializer(),
                            )
                    1 -> clientStateVersion = composite.decodeLongElement(descriptor, 1)
                    2 ->
                        reason =
                            composite.decodeSerializableElement(
                                descriptor,
                                2,
                                GameStateCatchUpReason.serializer(),
                            )
                    CompositeDecoder.DECODE_DONE -> break@loop
                    else -> ManualSerializerSupport.unexpectedIndex(index)
                }
            }

            GameStateCatchUpRequest(
                lobbyCode =
                    lobbyCode
                        ?: ManualSerializerSupport.missingField("lobbyCode", descriptor),
                clientStateVersion =
                    clientStateVersion
                        ?: ManualSerializerSupport.missingField("clientStateVersion", descriptor),
                reason = reason,
            )
        }
}
