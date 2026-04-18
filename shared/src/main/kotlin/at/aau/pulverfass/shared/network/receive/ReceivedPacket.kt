package at.aau.pulverfass.shared.network.receive

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.message.protocol.MessageHeader
import at.aau.pulverfass.shared.network.codec.SerializedPacket

/**
 * Technisches Zwischenergebnis nach dem Entpacken eines empfangenen Pakets.
 *
 * Das Modell trennt die WebSocket- und Transportebene von späterer fachlicher
 * Verarbeitung. Es enthält nur Verbindungsbezug, Header und die weiterhin roh
 * serialisierten Paketdaten.
 *
 * @property connectionId Verbindung, auf der das Paket empfangen wurde
 * @property header bereits gelesener Nachrichtenkopf
 * @property packet weiterhin roh serialisierte Paketdaten
 */
data class ReceivedPacket(
    val connectionId: ConnectionId,
    val header: MessageHeader,
    val packet: SerializedPacket,
)
