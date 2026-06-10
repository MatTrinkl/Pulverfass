package at.aau.pulverfass.shared.message.connection

import kotlinx.serialization.Serializable

/**
 * Aktueller, nicht persistierter Verbindungsstatus eines Lobby-Spielers.
 */
@Serializable
enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
}
