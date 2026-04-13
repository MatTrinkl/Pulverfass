package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.network.exception.PayloadTypeMismatchException
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.NetworkMessagePayload
import at.aau.pulverfass.shared.network.message.NetworkPayloadRegistry

/**
 * Repräsentiert ein bereits fachlich deserialisiertes Netzwerkpaket.
 *
 * Das Modell wird zwischen Protokollschichten übertragen und später wieder
 * serialisiert. Header-Typ und Payload-Typ müssen dabei konsistent sein.
 *
 * @property header Definiert welchen MessageType dieses Paket hat.
 * @property payload Der eigentliche Inhalt des Paketes.
 */
internal data class NetworkPacket<T : NetworkMessagePayload>(
    val header: MessageHeader,
    val payload: T,
) {
    init {
        val payloadType = NetworkPayloadRegistry.messageTypeFor(payload)
        if (header.type != payloadType) {
            throw PayloadTypeMismatchException(header.type, payloadType)
        }
    }
}
