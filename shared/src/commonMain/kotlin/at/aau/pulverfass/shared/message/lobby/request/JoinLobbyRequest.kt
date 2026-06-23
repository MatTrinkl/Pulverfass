package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.lobby.requireValidPlayerDisplayName
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Anfrage eines Clients, einer bestehenden Lobby beizutreten.
 *
 * **Konvention:** Siehe docs/NETWORK_MESSAGES.md für CustomSerializer-Pattern
 *
 * @property lobbyCode Ziel-Lobby der Join-Anfrage
 * @property playerDisplayName Anzeigename des Players fuer die Lobby-UI;
 * darf nicht leer sein und ist auf acht ASCII-Buchstaben begrenzt

 */
@Serializable
data class JoinLobbyRequest(
    val lobbyCode: LobbyCode,
    val playerDisplayName: String,
) : NetworkMessagePayload {
    init {
        requireValidPlayerDisplayName(playerDisplayName)
    }
}

/**
 * Technischer Serializer für [JoinLobbyRequest].
 */
object JoinLobbyRequestSerializer :
    KSerializer<JoinLobbyRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(JoinLobbyRequest.serializer())
