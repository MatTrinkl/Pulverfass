package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Stabile Gründe für einen im Lobby-Scope beobachteten Verbindungsverlust.
 */
@Serializable
enum class PlayerConnectionLostReason {
    SOCKET_CLOSED,
    HEARTBEAT_TIMEOUT,
}

/**
 * Lobby-Scoped Broadcast des Servers, wenn ein Spieler die Verbindung verliert.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId Spieler mit verlorener Verbindung
 * @property reason stabiler technischer Grund für den Verbindungsverlust
 */
@Serializable
data class PlayerConnectionLostEvent(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val reason: PlayerConnectionLostReason,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [PlayerConnectionLostEvent].
 */
@OptIn(ExperimentalSerializationApi::class)
object PlayerConnectionLostEventSerializer :
    KSerializer<PlayerConnectionLostEvent> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        PlayerConnectionLostEvent.serializer(),
    )
