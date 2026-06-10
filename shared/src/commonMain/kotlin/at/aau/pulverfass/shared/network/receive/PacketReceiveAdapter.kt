package at.aau.pulverfass.shared.network.receive

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.message.codec.NetworkMessageSerializer
import at.aau.pulverfass.shared.network.codec.PacketCodec
import at.aau.pulverfass.shared.network.exception.NetworkException
import at.aau.pulverfass.shared.network.exception.PacketReceiveException

/**
 * Dekodiert rohe Transport-Bytes bis zum technischen Nachrichtenkopf.
 *
 * Die Klasse entpackt nur das Wire-Format und liest den Header. Ein fachliches
 * Routing nach Nachrichtentypen findet hier bewusst noch nicht statt.
 */
class PacketReceiveAdapter {
    /**
     * Dekodiert empfangene Bytes bis zu einem neutralen [ReceivedPacket].
     *
     * @param connectionId Verbindung, auf der die Bytes empfangen wurden
     * @param bytes empfangene Wire-Bytes
     * @return neutrales technisches Paketmodell
     * @throws PacketReceiveException wenn das Framing oder die Header-Dekodierung fehlschlägt
     */
    fun decode(
        connectionId: ConnectionId,
        bytes: ByteArray,
    ): ReceivedPacket =
        try {
            val packet = PacketCodec.unpack(bytes)
            val header = NetworkMessageSerializer.deserializeHeader(packet.headerBytes)

            ReceivedPacket(
                connectionId = connectionId,
                header = header,
                packet = packet,
            )
        } catch (cause: NetworkException) {
            throw PacketReceiveException(
                connectionId = connectionId,
                message = "Failed to decode received packet for connection $connectionId",
                cause = cause,
            )
        }
}
