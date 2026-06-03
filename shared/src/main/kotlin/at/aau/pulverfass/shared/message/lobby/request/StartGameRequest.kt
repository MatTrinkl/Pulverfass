package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Anfrage eines Clients (als Lobby Owner), das Spiel zu starten.
 *
 * **Konvention:** Siehe docs/NETWORK_MESSAGES.md für CustomSerializer-Pattern
 *
 * @property lobbyCode betroffene Lobby
 */
@Serializable
data class StartGameRequest(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [StartGameRequest].
 */
object StartGameRequestSerializer :
    KSerializer<StartGameRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(StartGameRequest.serializer())
