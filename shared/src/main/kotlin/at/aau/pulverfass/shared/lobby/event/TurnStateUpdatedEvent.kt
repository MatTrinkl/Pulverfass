package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Atomare Aktualisierung des kombinierten Turn-Zustands einer Lobby.
 *
 * Dieses Event ist die einzige fachliche Mutationsquelle für den TurnState im
 * GameState. Änderungen an aktivem Spieler, Phase, Rundenzähler oder
 * Pause-Status werden bewusst nicht in einzelne Teil-Events aufgespalten.
 *
 * @property lobbyCode betroffene Lobby
 * @property activePlayerId aktuell aktiver Spieler
 * @property turnPhase aktuell aktive Phase
 * @property turnCount Rundenzähler ab der ersten gestarteten Runde
 * @property startPlayerId Referenzspieler für Rundenerkennung
 * @property isPaused signalisiert einen pausierten Turn-State
 * @property pauseReason optionale Begründung für den Pause-Zustand
 * @property pausedPlayerId optionaler Spieler, auf dessen Verbindung gewartet wird
 */
@Serializable(with = TurnStateUpdatedEventSerializer::class)
data class TurnStateUpdatedEvent(
    override val lobbyCode: LobbyCode,
    val activePlayerId: PlayerId,
    val turnPhase: TurnPhase,
    val turnCount: Int,
    val startPlayerId: PlayerId,
    val isPaused: Boolean = false,
    val pauseReason: String? = null,
    val pausedPlayerId: PlayerId? = null,
) : InternalLobbyEvent, PublicGameEvent {
    init {
        require(turnCount >= 1) {
            "TurnStateUpdatedEvent.turnCount darf nicht kleiner als 1 sein, war aber $turnCount."
        }
        require(!isPaused || !pauseReason.isNullOrBlank()) {
            "TurnStateUpdatedEvent.pauseReason muss gesetzt sein, wenn isPaused=true ist."
        }
        require(isPaused || pauseReason == null) {
            "TurnStateUpdatedEvent.pauseReason darf nur gesetzt sein, wenn isPaused=true ist."
        }
        require(isPaused || pausedPlayerId == null) {
            "TurnStateUpdatedEvent.pausedPlayerId darf nur gesetzt sein, wenn isPaused=true ist."
        }
        require(pausedPlayerId == null || pauseReason == TurnPauseReasons.WAITING_FOR_PLAYER) {
            "TurnStateUpdatedEvent.pausedPlayerId darf nur mit PauseReason '${TurnPauseReasons.WAITING_FOR_PLAYER}' gesetzt sein."
        }
        require(pauseReason != TurnPauseReasons.WAITING_FOR_PLAYER || pausedPlayerId != null) {
            "TurnStateUpdatedEvent.pausedPlayerId muss gesetzt sein, wenn pauseReason='${TurnPauseReasons.WAITING_FOR_PLAYER}' ist."
        }
        require(pausedPlayerId == null || pausedPlayerId == activePlayerId) {
            "TurnStateUpdatedEvent.pausedPlayerId muss dem aktiven Spieler entsprechen."
        }
    }
}

/**
 * Technischer Serializer für [TurnStateUpdatedEvent].
 */
object TurnStateUpdatedEventSerializer : KSerializer<TurnStateUpdatedEvent> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.TurnStateUpdatedEvent") {
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
        value: TurnStateUpdatedEvent,
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

    override fun deserialize(decoder: Decoder): TurnStateUpdatedEvent {
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
        return TurnStateUpdatedEvent(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            activePlayerId =
                activePlayerId ?: throw MissingFieldException("activePlayerId", descriptor.serialName),
            turnPhase = turnPhase ?: throw MissingFieldException("turnPhase", descriptor.serialName),
            turnCount = turnCount ?: throw MissingFieldException("turnCount", descriptor.serialName),
            startPlayerId = startPlayerId ?: throw MissingFieldException("startPlayerId", descriptor.serialName),
            isPaused = isPaused,
            pauseReason = pauseReason,
            pausedPlayerId = pausedPlayerId,
        )
    }
}
