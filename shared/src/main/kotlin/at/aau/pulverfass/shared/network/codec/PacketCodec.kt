package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.network.codec.PacketCodec.pack
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Kodiert und dekodiert das binäre Transport-Format eines [SerializedPacket]s.
 *
 * Dieses Codec ist ausschließlich für das Framing auf Byte-Ebene verantwortlich.
 * Es kennt weder fachliche Nachrichtentypen noch Payload-Klassen, sondern arbeitet
 * nur mit bereits serialisierten Header- und Payload-Bytes.
 *
 * Das Wire-Format ist wie folgt aufgebaut:
 * [Int32 headerLength][headerBytes][payloadBytes]
 *
 * Dabei gilt:
 * - `headerLength` belegt genau 4 Byte und wird in Big Endian kodiert
 * - `headerBytes` belegen exakt `headerLength` Byte und dürfen nicht leer sein
 * - `payloadBytes` umfassen alle verbleibenden Byte und dürfen leer sein
 *
 * Dadurch kann beim Dekodieren die Grenze zwischen Header und Payload eindeutig
 * wiederhergestellt werden, obwohl beide Bestandteile variabel lang sind.
 */
object PacketCodec {
    /**
     * Baut aus einem [SerializedPacket] das transportierbare Byte-Format.
     *
     * Die Header-Länge wird vorne als 4-Byte-Integer in Big Endian abgelegt,
     * danach folgen Header und Payload unverändert.
     *
     * @param packet das bereits serialisierte Paket
     * @return das vollständig gepackte ByteArray für den Transport
     * @throws IllegalArgumentException wenn der Header leer ist
     */
    fun pack(packet: SerializedPacket): ByteArray {
        if (packet.headerBytes.isEmpty()) {
            throw EmptyHeaderException()
        }

        val headerLength = packet.headerBytes.size
        val buffer =
            ByteBuffer.allocate(Int.SIZE_BYTES + headerLength + packet.payloadBytes.size)
                .order(ByteOrder.BIG_ENDIAN)

        buffer.putInt(headerLength)
        buffer.put(packet.headerBytes)
        buffer.put(packet.payloadBytes)

        return buffer.array()
    }

    /**
     * Zerlegt ein transportiertes ByteArray wieder in Header- und Payload-Bytes.
     *
     * Erwartet exakt das durch [pack] definierte Framing:
     * zuerst eine 4-Byte-Header-Länge in Big Endian, danach mindestens so viele
     * Header-Bytes und schließlich optional Payload-Bytes.
     *
     * @param bytes das empfangene ByteArray im Transportformat
     * @return das entpackte [SerializedPacket]
     * @throws IllegalArgumentException wenn das ByteArray zu kurz ist, eine ungültige
     * Header-Länge enthält oder weniger Header-Bytes vorhanden sind als angegeben
     */
    fun unpack(bytes: ByteArray): SerializedPacket {
        if (bytes.size < Int.SIZE_BYTES) {
            throw PacketTooShortException()
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val headerLength = buffer.int

        if (headerLength <= 0) {
            throw InvalidHeaderLengthException(headerLength)
        }
        if (bytes.size < Int.SIZE_BYTES + headerLength) {
            throw CorruptPacketException("Packet too short for declared header length.")
        }

        val headerBytes = ByteArray(headerLength)
        buffer[headerBytes]

        val payloadBytes = ByteArray(buffer.remaining())
        buffer[payloadBytes]

        return SerializedPacket(
            headerBytes = headerBytes,
            payloadBytes = payloadBytes,
        )
    }
}
