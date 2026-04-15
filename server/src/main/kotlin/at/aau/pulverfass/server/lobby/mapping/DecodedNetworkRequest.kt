package at.aau.pulverfass.server.lobby.mapping

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.message.protocol.MessageHeader
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.receive.ReceivedPacket

/**
 * Technisch bereits dekodierter Netzwerkrequest als Input für Domain-Mapping.
 *
 * Die Struktur bleibt absichtlich transportneutral und enthält keine Ktor- oder
 * Frame-Typen.
 */
data class DecodedNetworkRequest(
    /** Bereits bis zum Header entpackte technische Paketdaten. */
    val receivedPacket: ReceivedPacket,
    /** Fachlich dekodierte Payload. */
    val payload: NetworkMessagePayload,
    /** Technischer Kontext für Tracing, Routing und spätere Responses. */
    val context: EventContext,
) {
    /** Bequemer Direktzugriff auf die Quellverbindung. */
    val connectionId: ConnectionId
        get() = receivedPacket.connectionId

    /** Bequemer Direktzugriff auf den Protokolltyp der Nachricht. */
    val header: MessageHeader
        get() = receivedPacket.header

    init {
        val contextConnectionId = context.connectionId
        require(!(contextConnectionId != null && contextConnectionId != connectionId)) {
            "EventContext.connectionId muss null oder gleich request.connectionId sein."
        }
    }
}
