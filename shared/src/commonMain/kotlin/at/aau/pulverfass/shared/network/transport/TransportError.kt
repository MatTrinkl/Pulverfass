package at.aau.pulverfass.shared.network.transport

import at.aau.pulverfass.shared.ids.ConnectionId

/**
 * Optionales technisches Fehlerereignis auf der Transportebene.
 *
 * @property connectionId optionale Verbindung, falls der Fehler bereits
 * zugeordnet werden konnte
 * @property cause eigentliche Fehlerursache
 */
data class TransportError(
    override val connectionId: ConnectionId?,
    val cause: Throwable,
) : TransportEvent
