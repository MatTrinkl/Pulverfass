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
    val expiresAtEpochMillis: Long,
    val revokedAtEpochMillis: Long? = null,
) {
    val isConnected: Boolean
        get() = connectionId != null

    val isRevoked: Boolean
        get() = revokedAtEpochMillis != null

    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis >= expiresAtEpochMillis
}
