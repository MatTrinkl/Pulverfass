package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Erfolgsantwort auf einen akzeptierten Kartentausch.
 *
 * @property lobbyCode betroffene Lobby
 */
@Serializable
data class TradeInCardsResponse(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

/**
 * Legacy-Serializer für [TradeInCardsResponse].
 */
object TradeInCardsResponseSerializer :
    KSerializer<TradeInCardsResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        TradeInCardsResponse.serializer(),
    )
