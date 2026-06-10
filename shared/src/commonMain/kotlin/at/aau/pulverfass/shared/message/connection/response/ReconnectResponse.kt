package at.aau.pulverfass.shared.message.connection.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.requireValidPlayerDisplayName
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Antwort des Servers auf einen Reconnect-Versuch.
 *
 * @property success gibt an, ob die Session erfolgreich wiederhergestellt wurde
 * @property errorCode standardisierter Fehlercode bei fehlgeschlagenem Reconnect
 * @property playerId wiederhergestellter Player-Kontext der Session
 * @property lobbyCode wiederhergestellter Lobby-Kontext der Session
 * @property playerDisplayName Anzeigename des Spielers im aktuellen Lobby-Kontext
 */
@Serializable
@SerialName("at.aau.pulverfass.shared.network.message.ReconnectResponse")
data class ReconnectResponse(
    val success: Boolean,
    val errorCode: ReconnectErrorCode? = null,
    val playerId: PlayerId? = null,
    val lobbyCode: LobbyCode? = null,
    val playerDisplayName: String? = null,
) : NetworkMessagePayload {
    init {
        require(!(success && errorCode != null)) {
            "ReconnectResponse darf bei Erfolg keinen errorCode enthalten."
        }
        require(!(!success && errorCode == null)) {
            "ReconnectResponse benötigt bei Fehlschlag einen errorCode."
        }
        require(
            !(!success && (playerId != null || lobbyCode != null || playerDisplayName != null)),
        ) {
            "ReconnectResponse darf bei Fehlschlag keinen Session-Kontext enthalten."
        }
        require(!(playerDisplayName != null && lobbyCode == null)) {
            "ReconnectResponse darf playerDisplayName nur mit lobbyCode übertragen."
        }
        playerDisplayName?.let { requireValidPlayerDisplayName(it) }
    }
}

/**
 * Technischer Serializer für [ReconnectResponse].
 */
@OptIn(ExperimentalSerializationApi::class)
object ReconnectResponseSerializer :
    KSerializer<ReconnectResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(ReconnectResponse.serializer())
