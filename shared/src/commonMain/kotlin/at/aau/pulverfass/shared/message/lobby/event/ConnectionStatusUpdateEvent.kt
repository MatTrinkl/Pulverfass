package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.connection.ConnectionStatus
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Lobbyweiter Broadcast des aktuellen Verbindungsstatus eines Spielers.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId Spieler, dessen Status sich geändert hat
 * @property status aktueller, nicht persistierter Verbindungsstatus
 */
@Serializable
data class ConnectionStatusUpdateEvent(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val status: ConnectionStatus,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [ConnectionStatusUpdateEvent].
 */
@OptIn(ExperimentalSerializationApi::class)
object ConnectionStatusUpdateEventSerializer :
    KSerializer<ConnectionStatusUpdateEvent> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        ConnectionStatusUpdateEvent.serializer(),
    )
