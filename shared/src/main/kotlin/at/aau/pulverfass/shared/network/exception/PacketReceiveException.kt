package at.aau.pulverfass.shared.network.exception

import at.aau.pulverfass.shared.ids.ConnectionId

/**
 * Wird geworfen, wenn empfangene Bytes nicht bis zum technischen Nachrichtenkopf
 * dekodiert werden können.
 *
 * @property connectionId Verbindung, auf der die fehlerhaften Daten empfangen wurden
 */
class PacketReceiveException(
    val connectionId: ConnectionId,
    message: String,
    cause: Throwable,
) : NetworkException(message, cause)
