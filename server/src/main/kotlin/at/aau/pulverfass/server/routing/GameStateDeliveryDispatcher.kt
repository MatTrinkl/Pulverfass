package at.aau.pulverfass.server.routing

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.PrivateGameStatePayload
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import at.aau.pulverfass.shared.message.lobby.event.PublicGameStatePayload
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload

/**
 * Zentraler Delivery-Layer für autoritative GameState-Payloads.
 *
 * Der Dispatcher trennt bewusst zwischen öffentlichen und privaten GameState-
 * Nachrichten. Öffentliche Payloads dürfen lobbyweit verteilt werden, private
 * Payloads ausschließlich an ihren expliziten Empfänger.
 */
class GameStateDeliveryDispatcher(
    private val sendPayload: suspend (ConnectionId, NetworkMessagePayload) -> Unit,
    private val lobbyMembers: (LobbyCode) -> List<PlayerId>,
    private val connectionIdResolver: (PlayerId) -> ConnectionId?,
) {
    suspend fun sendPublicState(
        connectionId: ConnectionId,
        payload: PublicGameStatePayload,
    ) {
        sendPayload(connectionId, payload)
    }

    suspend fun broadcastPublicState(
        lobbyCode: LobbyCode,
        payload: PublicGameStatePayload,
    ) {
        lobbyMemberConnections(lobbyCode).forEach { connectionId ->
            sendPayload(connectionId, payload)
        }
    }

    suspend fun broadcastPublicDelta(
        lobbyCode: LobbyCode,
        fromVersion: Long,
        toVersion: Long,
        events: List<PublicGameEvent>,
    ) {
        broadcastPublicState(
            lobbyCode = lobbyCode,
            payload =
                GameStateDeltaEvent(
                    lobbyCode = lobbyCode,
                    fromVersion = fromVersion,
                    toVersion = toVersion,
                    events = events,
                ),
        )
    }

    suspend fun sendPrivateState(
        connectionId: ConnectionId,
        payload: PrivateGameStatePayload,
    ) {
        sendPayload(connectionId, payload)
    }

    suspend fun sendPrivateState(
        lobbyCode: LobbyCode,
        payload: PrivateGameStatePayload,
    ) {
        require(lobbyMembers(lobbyCode).contains(payload.recipientPlayerId)) {
            "Spieler '${payload.recipientPlayerId.value}' ist nicht Teil der Lobby '${lobbyCode.value}'."
        }

        val connectionId = connectionIdResolver(payload.recipientPlayerId) ?: return
        sendPayload(connectionId, payload)
    }

    private fun lobbyMemberConnections(lobbyCode: LobbyCode): List<ConnectionId> =
        lobbyMembers(lobbyCode)
            .mapNotNull(connectionIdResolver)
            .distinct()
}
