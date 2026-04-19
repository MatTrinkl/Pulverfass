package at.aau.pulverfass.server.session

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.SessionToken

/**
 * Technische Server-Session, die unabhängig von einer konkreten WebSocket-
 * Verbindung bestehen bleiben kann.
 */
data class Session(
    val sessionToken: SessionToken,
    val connectionId: ConnectionId?,
) {
    val isConnected: Boolean
        get() = connectionId != null
}
