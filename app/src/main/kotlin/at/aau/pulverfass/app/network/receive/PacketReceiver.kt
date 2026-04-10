package at.aau.pulverfass.app.network.receive

import at.aau.pulverfass.shared.network.PacketReceiveException
import at.aau.pulverfass.shared.network.receive.PacketReceiveAdapter
import at.aau.pulverfass.shared.network.receive.ReceivedPacket
import at.aau.pulverfass.shared.network.transport.BinaryMessageReceived
import at.aau.pulverfass.shared.network.transport.TransportEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Android-seitige technische Zwischenschicht zwischen WebSocket-Transport und
 * späterer Nachrichtenverarbeitung.
 *
 * Die Klasse nimmt rohe Binary-Transportevents entgegen, dekodiert diese bis
 * zum MessageHeader und emittiert neutrale ReceivedPacket-Objekte für höhere
 * Schichten.
 */
class PacketReceiver(
    private val adapter: PacketReceiveAdapter = PacketReceiveAdapter(),
) {
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
     * Nur BinaryMessageReceived wird weiter dekodiert. Andere technische
     * Transportevents werden bewusst ignoriert.
     */
    suspend fun onTransportEvent(event: TransportEvent) {
        when (event) {
            is BinaryMessageReceived -> decode(event)
            else -> Unit
        }
    }

    private suspend fun decode(event: BinaryMessageReceived): ReceivedPacket? =
        try {
            val receivedPacket = adapter.decode(event.connectionId, event.bytes)
            _packets.emit(receivedPacket)
            receivedPacket
        } catch (cause: PacketReceiveException) {
            _errors.emit(cause)
            null
        }
}
