package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = AttackRequestSerializer::class)
data class AttackRequest(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val fromTerritoryId: TerritoryId,
    val toTerritoryId: TerritoryId,
    val attackTroops: Int,
    val moveAfterCapture: Int,
    val requestId: String? = null,
) : NetworkMessagePayload {
    init {
        require(attackTroops >= 2) {
            "AttackRequest.attackTroops muss mindestens 2 sein, war aber $attackTroops."
        }
        require(moveAfterCapture > 0) {
            "AttackRequest.moveAfterCapture muss positiv sein, war aber $moveAfterCapture."
        }
        require(requestId == null || requestId.isNotBlank()) {
            "AttackRequest.requestId darf nicht leer sein."
        }
    }
}

object AttackRequestSerializer : KSerializer<AttackRequest> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.AttackRequest") {
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("playerId", PlayerId.serializer().descriptor)
            element("fromTerritoryId", TerritoryId.serializer().descriptor)
            element("toTerritoryId", TerritoryId.serializer().descriptor)
            element<Int>("attackTroops")
            element<Int>("moveAfterCapture")
            element<String>("requestId", isOptional = true)
        }

    override fun serialize(
        encoder: Encoder,
        value: AttackRequest,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, PlayerId.serializer(), value.playerId)
        composite.encodeSerializableElement(
            descriptor,
            2,
            TerritoryId.serializer(),
            value.fromTerritoryId,
        )
        composite.encodeSerializableElement(
            descriptor,
            3,
            TerritoryId.serializer(),
            value.toTerritoryId,
        )
        composite.encodeIntElement(descriptor, 4, value.attackTroops)
        composite.encodeIntElement(descriptor, 5, value.moveAfterCapture)
        if (value.requestId != null) {
            composite.encodeStringElement(descriptor, 6, value.requestId)
        }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): AttackRequest {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var playerId: PlayerId? = null
        var fromTerritoryId: TerritoryId? = null
        var toTerritoryId: TerritoryId? = null
        var attackTroops: Int? = null
        var moveAfterCapture: Int? = null
        var requestId: String? = null

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
                    fromTerritoryId =
                        composite.decodeSerializableElement(
                            descriptor,
                            2,
                            TerritoryId.serializer(),
                        )
                3 ->
                    toTerritoryId =
                        composite.decodeSerializableElement(
                            descriptor,
                            3,
                            TerritoryId.serializer(),
                        )
                4 -> attackTroops = composite.decodeIntElement(descriptor, 4)
                5 -> moveAfterCapture = composite.decodeIntElement(descriptor, 5)
                6 -> requestId = composite.decodeStringElement(descriptor, 6)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return AttackRequest(
            lobbyCode =
                lobbyCode
                    ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            playerId = playerId ?: throw MissingFieldException("playerId", descriptor.serialName),
            fromTerritoryId =
                fromTerritoryId
                    ?: throw MissingFieldException("fromTerritoryId", descriptor.serialName),
            toTerritoryId =
                toTerritoryId
                    ?: throw MissingFieldException("toTerritoryId", descriptor.serialName),
            attackTroops =
                attackTroops ?: throw MissingFieldException("attackTroops", descriptor.serialName),
            moveAfterCapture =
                moveAfterCapture
                    ?: throw MissingFieldException("moveAfterCapture", descriptor.serialName),
            requestId = requestId,
        )
    }
}
