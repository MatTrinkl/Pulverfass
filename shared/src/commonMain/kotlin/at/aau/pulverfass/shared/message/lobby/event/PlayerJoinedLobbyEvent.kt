package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.requireValidPlayerDisplayName
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Lobby-Scoped Broadcast des Servers nach einem erfolgreichen Join.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId Spieler, der der Lobby beigetreten ist
 * @property playerDisplayName Anzeigename des Players fuer die Lobby-UI;
 * darf nicht leer sein und ist auf acht ASCII-Buchstaben begrenzt

 */
@Serializable
data class PlayerJoinedLobbyEvent(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val playerDisplayName: String,
    val isHost: Boolean = false,
) : NetworkMessagePayload {
    init {
        requireValidPlayerDisplayName(playerDisplayName)
    }
}

/**
 * Technischer Serializer für [PlayerJoinedLobbyEvent].
 */
@OptIn(ExperimentalSerializationApi::class)
object PlayerJoinedLobbyEventSerializer :
    KSerializer<PlayerJoinedLobbyEvent> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        PlayerJoinedLobbyEvent.serializer(),
    )
