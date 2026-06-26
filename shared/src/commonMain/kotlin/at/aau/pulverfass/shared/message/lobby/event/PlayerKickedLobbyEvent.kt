package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Lobby-Scoped Broadcast des Servers nach dem erfolgreichen Kick eines Spielers.
 *
 * @property lobbyCode betroffene Lobby
 * @property targetPlayerId Spieler, der geworfen wurde
 * @property requesterPlayerId Spieler, der gekickt hat (Owner)
 */
@Serializable
data class PlayerKickedLobbyEvent(
    val lobbyCode: LobbyCode,
    val targetPlayerId: PlayerId,
    val requesterPlayerId: PlayerId,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [PlayerKickedLobbyEvent].
 */
object PlayerKickedLobbyEventSerializer :
    KSerializer<PlayerKickedLobbyEvent> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        PlayerKickedLobbyEvent.serializer(),
    )
