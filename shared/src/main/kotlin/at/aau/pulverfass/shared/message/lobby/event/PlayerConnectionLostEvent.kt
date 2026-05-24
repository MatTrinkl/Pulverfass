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
 * Stabile Gründe für einen im Lobby-Scope beobachteten Verbindungsverlust.
 */
@Serializable
enum class PlayerConnectionLostReason {
    SOCKET_CLOSED,
    HEARTBEAT_TIMEOUT,
}

/**
 * Lobby-Scoped Broadcast des Servers, wenn ein Spieler die Verbindung verliert.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId Spieler mit verlorener Verbindung
 * @property reason stabiler technischer Grund für den Verbindungsverlust
 */
@Serializable(with = PlayerConnectionLostEventSerializer::class)
data class PlayerConnectionLostEvent(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val reason: PlayerConnectionLostReason,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [PlayerConnectionLostEvent].
 */
@OptIn(ExperimentalSerializationApi::class)
object PlayerConnectionLostEventSerializer : KSerializer<PlayerConnectionLostEvent> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostEvent",
        ) {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("playerId", PlayerId.serializer().descriptor)
            element("reason", PlayerConnectionLostReason.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: PlayerConnectionLostEvent,
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
        composite.encodeSerializableElement(
            descriptor = descriptor,
            index = 2,
            serializer = PlayerConnectionLostReason.serializer(),
            value = value.reason,
        )
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): PlayerConnectionLostEvent {
        val composite = decoder.beginStructure(descriptor)
        val serialName = descriptor.serialName
        var lobbyCode: LobbyCode? = null
        var playerId: PlayerId? = null
        var reason: PlayerConnectionLostReason? = null

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
                2 ->
                    reason =
                        composite.decodeSerializableElement(
                            descriptor,
                            2,
                            PlayerConnectionLostReason.serializer(),
                        )
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return PlayerConnectionLostEvent(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", serialName),
            playerId = playerId ?: throw MissingFieldException("playerId", serialName),
            reason = reason ?: throw MissingFieldException("reason", serialName),
        )
    }
}
