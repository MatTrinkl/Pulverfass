package at.aau.pulverfass.shared.message.connection.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Antwort des Servers auf einen Reconnect-Versuch.
 *
 * @property success gibt an, ob die Session erfolgreich wiederhergestellt wurde
 * @property errorCode standardisierter Fehlercode bei fehlgeschlagenem Reconnect
 * @property playerId wiederhergestellter Player-Kontext der Session
 * @property lobbyCode wiederhergestellter Lobby-Kontext der Session
 * @property playerDisplayName Anzeigename des Spielers im aktuellen Lobby-Kontext
 */
@Serializable(with = ReconnectResponseSerializer::class)
data class ReconnectResponse(
    val success: Boolean,
    val errorCode: ReconnectErrorCode? = null,
    val playerId: PlayerId? = null,
    val lobbyCode: LobbyCode? = null,
    val playerDisplayName: String? = null,
) : NetworkMessagePayload {
    init {
        require(!(success && errorCode != null)) {
            "ReconnectResponse darf bei Erfolg keinen errorCode enthalten."
        }
        require(!(!success && errorCode == null)) {
            "ReconnectResponse benötigt bei Fehlschlag einen errorCode."
        }
        require(
            !(!success && (playerId != null || lobbyCode != null || playerDisplayName != null)),
        ) {
            "ReconnectResponse darf bei Fehlschlag keinen Session-Kontext enthalten."
        }
        require(!(playerDisplayName != null && lobbyCode == null)) {
            "ReconnectResponse darf playerDisplayName nur mit lobbyCode übertragen."
        }
    }
}

/**
 * Technischer Serializer für [ReconnectResponse].
 */
@OptIn(ExperimentalSerializationApi::class)
object ReconnectResponseSerializer : KSerializer<ReconnectResponse> {
    override val descriptor =
        buildClassSerialDescriptor(
            "at.aau.pulverfass.shared.network.message.ReconnectResponse",
        ) {
            element<Boolean>("success")
            element("errorCode", ReconnectErrorCode.serializer().descriptor, isOptional = true)
            element("playerId", PlayerId.serializer().descriptor, isOptional = true)
            element("lobbyCode", LobbyCode.serializer().descriptor, isOptional = true)
            element<String>("playerDisplayName", isOptional = true)
        }

    override fun serialize(
        encoder: Encoder,
        value: ReconnectResponse,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeBooleanElement(descriptor, 0, value.success)
        if (value.errorCode != null) {
            composite.encodeNullableSerializableElement(
                descriptor = descriptor,
                index = 1,
                serializer = ReconnectErrorCode.serializer(),
                value = value.errorCode,
            )
        }
        if (value.playerId != null) {
            composite.encodeNullableSerializableElement(
                descriptor = descriptor,
                index = 2,
                serializer = PlayerId.serializer(),
                value = value.playerId,
            )
        }
        if (value.lobbyCode != null) {
            composite.encodeNullableSerializableElement(
                descriptor = descriptor,
                index = 3,
                serializer = LobbyCode.serializer(),
                value = value.lobbyCode,
            )
        }
        if (value.playerDisplayName != null) {
            composite.encodeStringElement(descriptor, 4, value.playerDisplayName)
        }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ReconnectResponse {
        val composite = decoder.beginStructure(descriptor)
        var success: Boolean? = null
        var errorCode: ReconnectErrorCode? = null
        var playerId: PlayerId? = null
        var lobbyCode: LobbyCode? = null
        var playerDisplayName: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> success = composite.decodeBooleanElement(descriptor, 0)
                1 ->
                    errorCode =
                        composite.decodeNullableSerializableElement(
                            descriptor,
                            1,
                            ReconnectErrorCode.serializer(),
                        )
                2 ->
                    playerId =
                        composite.decodeNullableSerializableElement(
                            descriptor,
                            2,
                            PlayerId.serializer(),
                        )
                3 ->
                    lobbyCode =
                        composite.decodeNullableSerializableElement(
                            descriptor,
                            3,
                            LobbyCode.serializer(),
                        )
                4 -> playerDisplayName = composite.decodeStringElement(descriptor, 4)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return ReconnectResponse(
            success = success ?: throw MissingFieldException("success", descriptor.serialName),
            errorCode = errorCode,
            playerId = playerId,
            lobbyCode = lobbyCode,
            playerDisplayName = playerDisplayName,
        )
    }
}
