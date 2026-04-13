package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.network.exception.PayloadTypeMismatchException
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.NetworkMessagePayload
import at.aau.pulverfass.shared.network.message.NetworkPayloadRegistry

/**
 * Diese Klasse ist das Netzwerkpaket selbst welches deserialisiert, übertragen und serialisiert wird.
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
