package at.aau.pulverfass.shared.network.transport

import at.aau.pulverfass.shared.ids.ConnectionId

/**
 * Signalisiert, dass eine technische Verbindung erfolgreich aufgebaut wurde.
 *
 * @property connectionId Kennung der aufgebauten Verbindung
 */
data class Connected(
    override val connectionId: ConnectionId,
) : ConnectionBoundTransportEvent
