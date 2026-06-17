package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.Serializable

/**
 * Anfrage eines Spielers, einen vermuteten Cheat eines anderen Spielers zu melden.
 *
 * Die App sendet hier bewusst keine Begründung und keinen Sensorwert mit. Der
 * Server entscheidet ausschließlich anhand seines eigenen Meldefensters, ob die
 * Meldung stimmt. Dadurch kann ein manipulierter Client nicht einfach behaupten,
 * dass ein anderer Spieler gecheatet hat.
 *
 * @property lobbyCode Lobby, in der die Meldung passiert.
 * @property reporterPlayerId Spieler, der die Meldung abschickt.
 * @property accusedPlayerId Spieler, der verdächtigt wird.
 */
@Serializable
data class ReportCheatRequest(
    val lobbyCode: LobbyCode,
    val reporterPlayerId: PlayerId,
    val accusedPlayerId: PlayerId,
) : NetworkMessagePayload
