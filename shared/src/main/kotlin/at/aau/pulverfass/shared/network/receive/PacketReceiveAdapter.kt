package at.aau.pulverfass.shared.network.receive

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.NetworkException
import at.aau.pulverfass.shared.network.PacketReceiveException
import at.aau.pulverfass.shared.network.codec.PacketCodec
import at.aau.pulverfass.shared.network.codec.PacketCodecException
import at.aau.pulverfass.shared.network.message.NetworkMessageSerializer

/**
 * Dekodiert rohe Transport-Bytes serverseitig bis zum technischen Nachrichtenkopf.
 *
 * Die Klasse entpackt nur das Wire-Format und liest den [at.aau.pulverfass.shared.network.message.MessageHeader].
 * Ein fachliches Routing nach `MessageType` findet hier bewusst noch nicht statt.
 */
class PacketReceiveAdapter {
    /**
     * Dekodiert empfangene Bytes bis zu einem neutralen [ReceivedPacket].
     *
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
        } catch (cause: PacketCodecException) {
            throw PacketReceiveException(
                connectionId = connectionId,
                message = "Failed to decode received packet for connection $connectionId",
                cause = cause,
            )
        }
}
