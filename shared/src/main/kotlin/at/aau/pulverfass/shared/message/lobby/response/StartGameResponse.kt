package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Erfolgreiche Antwort des Servers auf eine StartGame-Anfrage.
 *
 * Bestätigt, dass das Spiel erfolgreich gestartet wurde.
 */
@Serializable(with = StartGameResponseSerializer::class)
data class StartGameResponse(
    val success: Boolean = true,
) : NetworkMessagePayload

@Serializable
@SerialName("at.aau.pulverfass.shared.network.message.StartGameResponse")
private object StartGameResponseWire

/**
 * Technischer Serializer für [StartGameResponse].
 */
object StartGameResponseSerializer :
    KSerializer<StartGameResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyTransformingSerializer(
        StartGameResponseWire.serializer(),
        toWire = { StartGameResponseWire },
        fromWire = { StartGameResponse() },
    )
