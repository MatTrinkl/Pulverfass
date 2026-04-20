package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.TurnPhase
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
 * Erfolgsantwort des Servers mit dem aktuellen autoritativen Turn-State.
 *
 * @property lobbyCode betroffene Lobby
 * @property activePlayerId aktuell aktiver Spieler
 * @property turnPhase aktuell aktive Phase
 * @property turnCount Rundenzähler ab Startspieler
 * @property startPlayerId Referenzspieler für den Rundenwechsel
 * @property isPaused signalisiert pausierten Turn-State
 * @property pauseReason optionale Begründung für Pause
 * @property pausedPlayerId optionaler Spieler, auf dessen Verbindung gewartet wird
 */
@Serializable(with = TurnStateGetResponseSerializer::class)
data class TurnStateGetResponse(
    val lobbyCode: LobbyCode,
    val activePlayerId: PlayerId,
    val turnPhase: TurnPhase,
    val turnCount: Int,
    val startPlayerId: PlayerId,
    val isPaused: Boolean = false,
    val pauseReason: String? = null,
    val pausedPlayerId: PlayerId? = null,
) : NetworkMessagePayload {
    companion object {
        fun fromGameState(gameState: GameState): TurnStateGetResponse = gameState.toTurnStateGetResponse()
    }
}

/**
 * Technischer Serializer für [TurnStateGetResponse].
 */
object TurnStateGetResponseSerializer : KSerializer<TurnStateGetResponse> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.TurnStateGetResponse") {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("activePlayerId", PlayerId.serializer().descriptor)
            element("turnPhase", TurnPhase.serializer().descriptor)
            element<Int>("turnCount")
            element("startPlayerId", PlayerId.serializer().descriptor)
            element<Boolean>("isPaused")
            element<String>("pauseReason", isOptional = true)
            element("pausedPlayerId", PlayerId.serializer().descriptor, isOptional = true)
        }

    override fun serialize(
        encoder: Encoder,
        value: TurnStateGetResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, PlayerId.serializer(), value.activePlayerId)
        composite.encodeSerializableElement(descriptor, 2, TurnPhase.serializer(), value.turnPhase)
        composite.encodeIntElement(descriptor, 3, value.turnCount)
        composite.encodeSerializableElement(descriptor, 4, PlayerId.serializer(), value.startPlayerId)
        composite.encodeBooleanElement(descriptor, 5, value.isPaused)
        if (value.pauseReason != null) {
            composite.encodeStringElement(descriptor, 6, value.pauseReason)
        }
        if (value.pausedPlayerId != null) {
            composite.encodeSerializableElement(descriptor, 7, PlayerId.serializer(), value.pausedPlayerId)
        }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): TurnStateGetResponse {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var activePlayerId: PlayerId? = null
        var turnPhase: TurnPhase? = null
        var turnCount: Int? = null
        var startPlayerId: PlayerId? = null
        var isPaused = false
        var pauseReason: String? = null
        var pausedPlayerId: PlayerId? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
                1 -> activePlayerId = composite.decodeSerializableElement(descriptor, 1, PlayerId.serializer())
                2 -> turnPhase = composite.decodeSerializableElement(descriptor, 2, TurnPhase.serializer())
                3 -> turnCount = composite.decodeIntElement(descriptor, 3)
                4 -> startPlayerId = composite.decodeSerializableElement(descriptor, 4, PlayerId.serializer())
                5 -> isPaused = composite.decodeBooleanElement(descriptor, 5)
                6 -> pauseReason = composite.decodeStringElement(descriptor, 6)
                7 -> pausedPlayerId = composite.decodeSerializableElement(descriptor, 7, PlayerId.serializer())
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return TurnStateGetResponse(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            activePlayerId = activePlayerId ?: throw MissingFieldException("activePlayerId", descriptor.serialName),
            turnPhase = turnPhase ?: throw MissingFieldException("turnPhase", descriptor.serialName),
            turnCount = turnCount ?: throw MissingFieldException("turnCount", descriptor.serialName),
            startPlayerId = startPlayerId ?: throw MissingFieldException("startPlayerId", descriptor.serialName),
            isPaused = isPaused,
            pauseReason = pauseReason,
            pausedPlayerId = pausedPlayerId,
        )
    }
}
