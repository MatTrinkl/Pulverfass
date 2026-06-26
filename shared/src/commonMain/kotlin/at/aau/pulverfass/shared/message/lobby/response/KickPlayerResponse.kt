package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Erfolgreiche Antwort des Servers auf eine Kick-Anfrage.
 *
 * Bestätigt, dass der Spieler erfolgreich aus der Lobby entfernt wurde.
 */
@Serializable(with = KickPlayerResponseSerializer::class)
data class KickPlayerResponse(
    val success: Boolean = true,
) : NetworkMessagePayload

@Serializable
@SerialName("at.aau.pulverfass.shared.network.message.KickPlayerResponse")
private object KickPlayerResponseWire

/**
 * Technischer Serializer für [KickPlayerResponse].
 */
object KickPlayerResponseSerializer :
    KSerializer<KickPlayerResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyTransformingSerializer(
        KickPlayerResponseWire.serializer(),
        toWire = { KickPlayerResponseWire },
        fromWire = { KickPlayerResponse() },
    )
