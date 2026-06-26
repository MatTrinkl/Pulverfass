package at.aau.pulverfass.shared.network.transport

import at.aau.pulverfass.shared.ids.ConnectionId

/**
 * Signalisiert, dass eine bestehende Verbindung beendet wurde.
 *
 * @property connectionId beendete Verbindung
 * @property reason optionaler technischer Close-Reason
 */
data class Disconnected(
    override val connectionId: ConnectionId,
    val reason: String? = null,
) : ConnectionBoundTransportEvent
