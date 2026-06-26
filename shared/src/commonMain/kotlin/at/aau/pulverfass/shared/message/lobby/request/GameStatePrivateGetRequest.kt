package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Anfrage eines Clients nach seinem privaten, nicht broadcastbaren GameState-Snapshot.
 *
 * Der [playerId]-Wert wird serverseitig gegen die technische Connection validiert,
 * damit ein Client keine privaten Daten anderer Spieler abrufen kann.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId angefragter Spieler, muss zur auslösenden Connection passen
 */
@Serializable
data class GameStatePrivateGetRequest(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [GameStatePrivateGetRequest].
 */
object GameStatePrivateGetRequestSerializer :
    KSerializer<GameStatePrivateGetRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        GameStatePrivateGetRequest.serializer(),
    )
