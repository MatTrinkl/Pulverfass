package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Lobby-Scoped Broadcast des Servers nach einem erfolgreichen Leave.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId Spieler, der die Lobby verlassen hat
 */
@Serializable
data class PlayerLeftLobbyEvent(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val newHost: PlayerId? = null,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [PlayerLeftLobbyEvent].
 */
@OptIn(ExperimentalSerializationApi::class)
object PlayerLeftLobbyEventSerializer :
    KSerializer<PlayerLeftLobbyEvent> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        PlayerLeftLobbyEvent.serializer(),
    )
