package at.aau.pulverfass.shared.network.receive

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.codec.SerializedPacket
import at.aau.pulverfass.shared.network.message.MessageHeader

/**
 * Technisches Zwischenergebnis nach dem Entpacken eines empfangenen Pakets.
 *
 * Dieses Modell trennt die WebSocket- und Transportebene von der späteren
 * fachlichen Weiterverarbeitung. Es enthält nur Verbindungsbezug, Header und
 * die weiterhin roh serialisierten Paketdaten.
 */
data class ReceivedPacket(
    val connectionId: ConnectionId,
    val header: MessageHeader,
    val packet: SerializedPacket,
)
