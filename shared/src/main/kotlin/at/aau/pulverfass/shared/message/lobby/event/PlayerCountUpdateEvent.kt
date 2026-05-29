package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = PlayerCountUpdateEventSerializer::class)
data class PlayerCountUpdateEvent(
    val lobbyCode: LobbyCode,
    val playerCount: Int,
) : NetworkMessagePayload

object PlayerCountUpdateEventSerializer : KSerializer<PlayerCountUpdateEvent> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PlayerCountUpdateEvent", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: PlayerCountUpdateEvent,
    ) {
        // reuse simple string encoding: "lobbyCode|count"
        encoder.encodeString(
            "${value.lobbyCode.value}|${value.playerCount}",
        )
    }

    override fun deserialize(decoder: Decoder): PlayerCountUpdateEvent {
        val raw = decoder.decodeString()
        val parts = raw.split("|")
        val lobby = LobbyCode(parts[0])
        val count = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return PlayerCountUpdateEvent(
            lobbyCode = lobby,
            playerCount = count,
        )
    }
}
