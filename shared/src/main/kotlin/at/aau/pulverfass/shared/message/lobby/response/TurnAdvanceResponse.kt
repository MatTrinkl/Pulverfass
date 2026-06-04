package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Erfolgsantwort auf eine verarbeitete Turn-Advance-Anfrage.
 */
@Serializable
data class TurnAdvanceResponse(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

object TurnAdvanceResponseSerializer :
    KSerializer<TurnAdvanceResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        TurnAdvanceResponse.serializer(),
    )
