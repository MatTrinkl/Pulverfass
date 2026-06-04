package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Lobby-Scoped Broadcast des Servers nach dem erfolgreichen Spielstart.
 *
 * @property lobbyCode betroffene Lobby
 */
@Serializable
data class GameStartedEvent(
    val lobbyCode: LobbyCode,
) : PublicGameEvent

/**
 * Technischer Serializer für [GameStartedEvent].
 */
object GameStartedEventSerializer :
    KSerializer<GameStartedEvent> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(GameStartedEvent.serializer())
