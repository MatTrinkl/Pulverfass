package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Anfrage eines Clients (als Lobby Owner), einen Spieler aus der Lobby zu werfen.
 *
 * **Konvention:** Siehe docs/NETWORK_MESSAGES.md für CustomSerializer-Pattern
 *
 * @property lobbyCode betroffene Lobby
 * @property targetPlayerId Spieler, der geworfen werden soll
 * @property requesterPlayerId Spieler, der die Anfrage stellt (muss Owner sein)
 */
@Serializable
data class KickPlayerRequest(
    val lobbyCode: LobbyCode,
    val targetPlayerId: PlayerId,
    val requesterPlayerId: PlayerId,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [KickPlayerRequest].
 */
object KickPlayerRequestSerializer :
    KSerializer<KickPlayerRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(KickPlayerRequest.serializer())
