package at.aau.pulverfass.server.receive

import at.aau.pulverfass.shared.network.exception.PacketReceiveException
import at.aau.pulverfass.shared.network.receive.PacketReceiveAdapter
import at.aau.pulverfass.shared.network.receive.ReceivedPacket
import at.aau.pulverfass.shared.network.transport.BinaryMessageReceived
import at.aau.pulverfass.shared.network.transport.TransportEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.slf4j.LoggerFactory

/**
 * Serverseitige technische Zwischenschicht zwischen WebSocket-Transport und
 * späterer Nachrichtenverarbeitung.
 *
 * Die Klasse nimmt rohe Binary-Transportevents entgegen, dekodiert diese bis
 * zum [at.aau.pulverfass.shared.network.message.MessageHeader] und emittiert
 * das neutrale [ReceivedPacket]-Modell für höhere Schichten.
 */
class PacketReceiver(
    private val adapter: PacketReceiveAdapter = PacketReceiveAdapter(),
) {
    private val logger = LoggerFactory.getLogger(PacketReceiver::class.java)
    private val _packets = MutableSharedFlow<ReceivedPacket>(extraBufferCapacity = 64)
    private val _errors = MutableSharedFlow<PacketReceiveException>(extraBufferCapacity = 64)

    /**
     * Erfolgreich bis zum Header gelesene Pakete.
     */
    val packets: SharedFlow<ReceivedPacket> = _packets.asSharedFlow()

    /**
     * Fehler beim Entpacken oder Lesen des Headers.
     */
    val errors: SharedFlow<PacketReceiveException> = _errors.asSharedFlow()

    /**
     * Verarbeitet ein einzelnes Transportevent.
     *
     * Nur [BinaryMessageReceived] wird weiter dekodiert. Andere technische
     * Transportevents werden bewusst ignoriert.
     */
    suspend fun onTransportEvent(event: TransportEvent) {
        when (event) {
            is BinaryMessageReceived -> decode(event)
            else -> Unit
        }
    }

    /**
     * Dekodiert rohe Bytes bis zum Header.
     *
     * Diese Variante vermeidet zusätzliche defensive Kopien, wenn Aufrufende
     * bereits ein ByteArray besitzen und kein [BinaryMessageReceived] benötigen.
     *
     * @return das neutrale [ReceivedPacket] oder `null`, falls die Bytes nicht
     * bis zum Header dekodiert werden konnten
     */
    suspend fun decode(
        connectionId: at.aau.pulverfass.shared.ids.ConnectionId,
        bytes: ByteArray,
    ): ReceivedPacket? =
        try {
            val receivedPacket = adapter.decode(connectionId, bytes)
            logger.debug(
                "Decoded packet header {} for connection {}",
                receivedPacket.header.type,
                receivedPacket.connectionId.value,
            )
            _packets.emit(receivedPacket)
            receivedPacket
        } catch (cause: PacketReceiveException) {
            logger.warn(
                "Failed to decode packet for connection {}",
                connectionId.value,
                cause,
            )
            _errors.emit(cause)
            null
        }

    /**
     * Dekodiert ein Binary-Transportevent bis zum Header.
     *
     * @return das neutrale [ReceivedPacket] oder `null`, falls die Bytes nicht
     * bis zum Header dekodiert werden konnten
     */
    suspend fun decode(event: BinaryMessageReceived): ReceivedPacket? =
        decode(event.connectionId, event.bytes)
}
