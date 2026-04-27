package at.aau.pulverfass.server.send

import at.aau.pulverfass.server.connection.ConnectionManager
import at.aau.pulverfass.shared.ids.ConnectionId

/**
 * Serverseitiger Raw-Byte-Sender für ausgehende Netzwerkpakete.
 *
 * Diese Klasse ist ein rein technischer Helfer zwischen High-Level-API und
 * WebSocket-Transport. Fachliche Payload-Serialisierung findet nicht hier statt.
 */
class PacketSender(
    private val connectionManager: ConnectionManager,
) {
    /**
     * Sendet bereits fertig kodierte Wire-Bytes an eine aktive Verbindung.
     *
     * @param connectionId Zielverbindung
     * @param bytes bereits serialisierte und geframte Nutzdaten
     */
    suspend fun send(
        connectionId: ConnectionId,
        bytes: ByteArray,
    ) {
        connectionManager.send(connectionId, bytes)
    }
}
