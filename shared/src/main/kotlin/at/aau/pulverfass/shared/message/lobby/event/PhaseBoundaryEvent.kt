package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Expliziter Marker für den Abschluss einer Turn-Phase.
 *
 * Das Event signalisiert Clients, dass die Verarbeitung der vorherigen Phase
 * abgeschlossen ist und die nächste Phase ab `stateVersion` gilt.
 *
 * @property lobbyCode betroffene Lobby
 * @property stateVersion autoritative Version nach dem Phasenwechsel
 * @property previousPhase abgeschlossene Phase
 * @property nextPhase neue aktive Phase
 * @property activePlayerId aktiver Spieler in der neuen Phase
 * @property turnCount Rundenzähler der neuen Phase
 */
@Serializable(with = PhaseBoundaryEventSerializer::class)
data class PhaseBoundaryEvent(
    val lobbyCode: LobbyCode,
    val stateVersion: Long,
    val previousPhase: TurnPhase,
    val nextPhase: TurnPhase,
    val activePlayerId: PlayerId,
    val turnCount: Int,
) : PublicGameStatePayload {
    init {
        require(stateVersion >= 1) {
            "PhaseBoundaryEvent.stateVersion darf nicht kleiner als 1 sein, war aber $stateVersion."
        }
        require(turnCount >= 1) {
            "PhaseBoundaryEvent.turnCount darf nicht kleiner als 1 sein, war aber $turnCount."
        }
        require(previousPhase != nextPhase) {
            "PhaseBoundaryEvent.previousPhase und nextPhase muessen verschieden sein."
        }
    }
}

/**
 * Technischer Serializer für [PhaseBoundaryEvent].
 */
object PhaseBoundaryEventSerializer : KSerializer<PhaseBoundaryEvent> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.PhaseBoundaryEvent") {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element<Long>("stateVersion")
            element("previousPhase", TurnPhase.serializer().descriptor)
            element("nextPhase", TurnPhase.serializer().descriptor)
            element("activePlayerId", PlayerId.serializer().descriptor)
            element<Int>("turnCount")
        }

    override fun serialize(
        encoder: Encoder,
        value: PhaseBoundaryEvent,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeLongElement(descriptor, 1, value.stateVersion)
        composite.encodeSerializableElement(
            descriptor,
            2,
            TurnPhase.serializer(),
            value.previousPhase,
        )
        composite.encodeSerializableElement(descriptor, 3, TurnPhase.serializer(), value.nextPhase)
        composite.encodeSerializableElement(
            descriptor,
            4,
            PlayerId.serializer(),
            value.activePlayerId,
        )
        composite.encodeIntElement(descriptor, 5, value.turnCount)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): PhaseBoundaryEvent {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var stateVersion: Long? = null
        var previousPhase: TurnPhase? = null
        var nextPhase: TurnPhase? = null
        var activePlayerId: PlayerId? = null
        var turnCount: Int? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 ->
                    lobbyCode =
                        composite.decodeSerializableElement(
                            descriptor,
                            0,
                            LobbyCode.serializer(),
                        )
                1 -> stateVersion = composite.decodeLongElement(descriptor, 1)
                2 ->
                    previousPhase =
                        composite.decodeSerializableElement(
                            descriptor,
                            2,
                            TurnPhase.serializer(),
                        )
                3 ->
                    nextPhase =
                        composite.decodeSerializableElement(
                            descriptor,
                            3,
                            TurnPhase.serializer(),
                        )
                4 ->
                    activePlayerId =
                        composite.decodeSerializableElement(
                            descriptor,
                            4,
                            PlayerId.serializer(),
                        )
                5 -> turnCount = composite.decodeIntElement(descriptor, 5)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return PhaseBoundaryEvent(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            stateVersion =
                stateVersion
                    ?: throw MissingFieldException("stateVersion", descriptor.serialName),
            previousPhase =
                previousPhase
                    ?: throw MissingFieldException("previousPhase", descriptor.serialName),
            nextPhase =
                nextPhase
                    ?: throw MissingFieldException("nextPhase", descriptor.serialName),
            activePlayerId =
                activePlayerId
                    ?: throw MissingFieldException("activePlayerId", descriptor.serialName),
            turnCount =
                turnCount
                    ?: throw MissingFieldException("turnCount", descriptor.serialName),
        )
    }
}
